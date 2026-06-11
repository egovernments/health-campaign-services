import { logger } from './logger';
import { httpRequest } from './request';
import config from '../config';

/**
 * Interface for Sheet Data Search Request
 */
interface SheetDataSearchRequest {
    RequestInfo: {
        apiId?: string;
        ver?: string;
        ts?: number;
        action?: string;
        did?: string;
        key?: string;
        msgId?: string;
        correlationId?: string;
        userInfo?: any;
    };
    SheetDataSearchCriteria: {
        tenantId: string;
        referenceId?: string;
        fileStoreId?: string;
        sheetName?: string;
        limit?: number | null;
        offset?: number;
    };
}

/**
 * Interface for Sheet Data Response
 */
interface SheetDataSearchResponse {
    ResponseInfo: any;
    SheetDataDetails: {
        Data: Array<{
            referenceId: string;
            tenantId: string;
            fileStoreId: string;
            sheetName: string;
            rowNumber: number;
            rowjson: any;
            createdBy: string;
            createdTime: number;
        }>;
        TotalCount: number;
        SheetWiseCounts: Array<{
            sheetName: string;
            recordCount: number;
        }>;
    };
}

/**
 * Page size for paginated sheet-data reads. Sourced from config.excelIngestion
 * (env-overridable) — no hardcoded value here.
 */
export function getSheetFetchPageSize(): number {
    return config.excelIngestion.sheetFetchPageSize;
}

/**
 * Build the sheet-data search request envelope shared by every search variant.
 */
function buildSheetSearchRequest(
    tenantId: string,
    referenceId: string,
    fileStoreId: string,
    limit: number | null,
    offset: number,
    sheetName?: string
): SheetDataSearchRequest {
    return {
        RequestInfo: {
            apiId: "project-factory",
            ver: "1.0",
            ts: Date.now(),
            action: "search",
            msgId: `pf-${Date.now()}`,
            correlationId: `pf-correlation-${Date.now()}`
        },
        SheetDataSearchCriteria: {
            tenantId,
            referenceId,
            fileStoreId,
            sheetName,
            limit,
            offset
        }
    };
}

/**
 * POST a sheet-data search request to the excel-ingestion service.
 */
async function postSheetDataSearch(requestData: SheetDataSearchRequest): Promise<SheetDataSearchResponse | null> {
    // Excel ingestion service configuration
    const searchUrl = `${config.host.excelIngestionHost}${config.paths.excelIngestionSheetSearch}`;
    logger.info(`Making HTTP request to: ${searchUrl}`);
    return await httpRequest(
        searchUrl,
        requestData,
        {},
        'post',
        '',
        {
            'Content-Type': 'application/json'
        }
    );
}

/**
 * Search sheet data from excel-ingestion service using referenceId and fileStoreId.
 *
 * NOTE: returns the full row payload (Data array) and is unbounded when `limit` is
 * null. Prefer getSheetDataCount when only a count is needed, and forEachSheetDataPage
 * to stream large sheets without loading every row into memory at once.
 */
export async function searchSheetData(
    tenantId: string,
    referenceId: string,
    fileStoreId: string,
    limit: number | null = null,
    sheetName?: string
): Promise<Array<any> | null> {
    try {
        logger.info(`Searching sheet data for referenceId: ${referenceId}, fileStoreId: ${fileStoreId}`);

        const requestData = buildSheetSearchRequest(tenantId, referenceId, fileStoreId, limit, 0, sheetName);

        const searchResponse = await postSheetDataSearch(requestData);

        if (searchResponse?.SheetDataDetails && searchResponse.SheetDataDetails.Data) {
            logger.info(`Found ${searchResponse.SheetDataDetails.TotalCount} records for referenceId: ${referenceId}`);

            // Log sheet-wise counts
            if (searchResponse.SheetDataDetails.SheetWiseCounts) {
                logger.info('Sheet-wise record counts:');
                searchResponse.SheetDataDetails.SheetWiseCounts.forEach((sheetCount : any) => {
                    logger.info(`  ${sheetCount.sheetName}: ${sheetCount.recordCount} records`);
                });
            }

            return searchResponse.SheetDataDetails.Data;
        } else {
            logger.error('Invalid response from excel-ingestion service');
            return null;
        }

    } catch (error) {
        logger.error(`Error searching sheet data for referenceId: ${referenceId}`, error);
        return null;
    }
}

/**
 * Count-only sheet-data search. Returns the excel-ingestion service's true
 * TotalCount (a SELECT COUNT(*) independent of the row payload), so callers that
 * only need a count never transfer or retain thousands of rowjson blobs. A
 * `limit:1` is sent purely to satisfy the service's @Min(1) limit validator.
 *
 * @returns the count, or null when the service is unreachable / responds invalid.
 */
export async function getSheetDataCount(
    tenantId: string,
    referenceId: string,
    fileStoreId: string,
    sheetName?: string
): Promise<number | null> {
    try {
        const requestData = buildSheetSearchRequest(tenantId, referenceId, fileStoreId, 1, 0, sheetName);
        const searchResponse = await postSheetDataSearch(requestData);
        const details = searchResponse?.SheetDataDetails;
        if (!details) {
            logger.error('Invalid response from excel-ingestion service while counting sheet data');
            return null;
        }
        return typeof details.TotalCount === 'number' ? details.TotalCount : 0;
    } catch (error) {
        logger.error(`Error counting sheet data for referenceId: ${referenceId}`, error);
        return null;
    }
}

/**
 * Iterate sheet data one page at a time using the service's LIMIT/OFFSET support,
 * invoking `onPage` for each non-empty page (awaited). Keeps peak memory bounded
 * regardless of total row count.
 *
 * Fails loud: throws on a failed/invalid page so a partial read never silently
 * produces incomplete downstream data (e.g. a half-built boundary cascade).
 *
 * @returns the total number of rows iterated across all pages.
 */
export async function forEachSheetDataPage(
    tenantId: string,
    referenceId: string,
    fileStoreId: string,
    sheetName: string | undefined,
    pageSize: number,
    onPage: (rows: any[], pageInfo: { offset: number; totalCount: number }) => Promise<void> | void
): Promise<number> {
    const effectivePageSize = pageSize && pageSize > 0 ? pageSize : getSheetFetchPageSize();
    let offset = 0;
    let processed = 0;
    let totalCount = 0;

    while (true) {
        const requestData = buildSheetSearchRequest(tenantId, referenceId, fileStoreId, effectivePageSize, offset, sheetName);

        let searchResponse: SheetDataSearchResponse | null;
        try {
            searchResponse = await postSheetDataSearch(requestData);
        } catch (error) {
            logger.error(`Error fetching sheet data page (sheet=${sheetName}, offset=${offset})`, error);
            throw new Error(`Failed to fetch sheet data page (sheet=${sheetName}, offset=${offset})`);
        }

        const details = searchResponse?.SheetDataDetails;
        if (!details || !Array.isArray(details.Data)) {
            throw new Error(`Invalid response while paginating sheet data (sheet=${sheetName}, offset=${offset})`);
        }

        const rows = details.Data;
        if (typeof details.TotalCount === 'number') totalCount = details.TotalCount;

        if (rows.length > 0) {
            await onPage(rows, { offset, totalCount });
            processed += rows.length;
        }

        // Stop when the service returned a short page (end of data) …
        if (rows.length < effectivePageSize) break;
        offset += effectivePageSize;
        // … or once we have walked past the known total.
        if (totalCount > 0 && offset >= totalCount) break;
    }

    return processed;
}