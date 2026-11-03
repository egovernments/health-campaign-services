import { createMdmsData, searchMDMSDataViaV2Api } from "../api/coreApis";
import { logger } from "./logger";

// The MDMS v2 search returns 10 rows when no limit is sent
const MDMS_PAGE_SIZE = 500;
const MDMS_MAX_PAGES = 50;

/** Every active row of an MDMS v2 schema that matches the filters, read page by page. */
export async function searchAllMdmsRows(tenantId: string, schemaCode: string, filters: Record<string, any>): Promise<any[]> {
    const rows: any[] = [];
    for (let page = 0; page < MDMS_MAX_PAGES; page++) {
        const response = await searchMDMSDataViaV2Api({
            MdmsCriteria: { tenantId, schemaCode, filters, isActive: true, limit: MDMS_PAGE_SIZE, offset: page * MDMS_PAGE_SIZE },
        } as any);
        const batch = response?.mdms || [];
        rows.push(...batch);
        if (batch.length < MDMS_PAGE_SIZE) return rows;
    }
    logger.warn(`MDMS search for ${schemaCode} ${JSON.stringify(filters)} stopped after ${MDMS_MAX_PAGES * MDMS_PAGE_SIZE} rows`);
    return rows;
}

/**
 * Copies a campaign's rows of an MDMS schema to its clone (project set to the clone's number), leaving out rows the clone
 * already has (same key). Returns the number of rows created.
 */
export async function copyCampaignMdmsRows(
    tenantId: string,
    schemaCode: string,
    fromCampaignNumber: string,
    toCampaignNumber: string,
    keyOf: (data: any) => string,
    requestInfo: any
): Promise<number> {
    const [sourceRows, cloneRows] = await Promise.all([
        searchAllMdmsRows(tenantId, schemaCode, { project: fromCampaignNumber }),
        searchAllMdmsRows(tenantId, schemaCode, { project: toCampaignNumber }),
    ]);
    const existing = new Set(cloneRows.map((row: any) => keyOf(row?.data)));
    let created = 0;
    for (const row of sourceRows) {
        if (!row?.data || existing.has(keyOf(row.data))) continue;
        await createMdmsData(tenantId, schemaCode, { ...row.data, project: toCampaignNumber }, requestInfo);
        existing.add(keyOf(row.data));
        created++;
    }
    return created;
}
