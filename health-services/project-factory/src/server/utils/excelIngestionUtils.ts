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
 * Search sheet data from excel-ingestion service using referenceId and fileStoreId
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

        const requestData: SheetDataSearchRequest = {
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
                offset: 0
            }
        };

        // Excel ingestion service configuration
        const searchUrl = `${config.host.excelIngestionHost}${config.paths.excelIngestionSheetSearch}`;

        logger.info(`Making HTTP request to: ${searchUrl}`);
        
        const searchResponse: SheetDataSearchResponse = await httpRequest(
            searchUrl,
            requestData,
            {},
            'post',
            '',
            {
                'Content-Type': 'application/json'
            }
        );
        if (searchResponse.SheetDataDetails && searchResponse.SheetDataDetails.Data) {
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
 * Format and display sheet data in a readable format
 */
export function formatSheetDataForDisplay(sheetData: Array<any>): string {
    if (!sheetData || sheetData.length === 0) {
        return 'No sheet data found';
    }

    let output = `\n=== SHEET DATA (${sheetData.length} records) ===\n`;
    
    // Group by sheet name
    const groupedBySheet: { [key: string]: any[] } = {};
    sheetData.forEach(record => {
        const sheetName = record.sheetname || record.sheetName || 'Unknown';
        if (!groupedBySheet[sheetName]) {
            groupedBySheet[sheetName] = [];
        }
        groupedBySheet[sheetName].push(record);
    });

    // Display each sheet's data
    Object.keys(groupedBySheet).forEach(sheetName => {
        const records = groupedBySheet[sheetName];
        output += `\n--- Sheet: ${sheetName} (${records.length} records) ---\n`;
        
        records.forEach((record, index) => {
            output += `\nRecord ${index + 1} (Row ${record.rownumber || record.rowNumber}):\n`;
            if (record.rowjson) {
                Object.keys(record.rowjson).forEach(key => {
                    output += `  ${key}: ${record.rowjson[key]}\n`;
                });
            }
        });
    });

    output += '\n=== END OF SHEET DATA ===\n';
    return output;
}