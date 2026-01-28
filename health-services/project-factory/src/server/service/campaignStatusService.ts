import config from "../config";
import { executeQuery, getTableName } from "../utils/db";
import { logger } from "../utils/logger";

interface MappingRow {
    type: string;
    status: string;
    status_count: string;
}

interface CampaignRow {
    type: string;
    status: string;
    status_count: string;
}

interface ProcessRow {
    processname: string;
    status: string;
}

interface CampaignStatusResponse {
    campaignNumber: string;
    summary: Record<string, Record<string, number>>;
    processes: ProcessRow[];
}

async function getCampaignStatusService(
    campaignNumber: string, 
    tenantId: string,
    request?: any
): Promise<CampaignStatusResponse> {
    try {
        logger.info(`Fetching campaign status for campaign: ${campaignNumber}, tenant: ${tenantId}`);

        // Get table names with tenant schema
        const mappingTableName = getTableName(config.DB_CONFIG.DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME, tenantId);
        const campaignDataTableName = getTableName(config.DB_CONFIG.DB_CAMPAIGN_DATA_TABLE_NAME, tenantId);
        const processTableName = getTableName(config.DB_CONFIG.DB_CAMPAIGN_PROCESS_DATA_TABLE_NAME, tenantId);

        // 1. Mapping Data Query (no tenantid column in this table)
        const mappingQuery = `
            SELECT type, status, COUNT(status) AS status_count
            FROM ${mappingTableName}
            WHERE campaignnumber = $1
            GROUP BY type, status
        `;
        const mappingResult = await executeQuery(mappingQuery, [campaignNumber]);

        // 2. Campaign Data Query (no tenantid column in this table)
        const campaignQuery = `
            SELECT type, status, COUNT(status) AS status_count
            FROM ${campaignDataTableName}
            WHERE campaignnumber = $1
            GROUP BY type, status
        `;
        const campaignResult = await executeQuery(campaignQuery, [campaignNumber]);

        // 3. Process Data Query (no tenantid column in this table)
        const processQuery = `
            SELECT processname, status
            FROM ${processTableName}
            WHERE campaignnumber = $1
        `;
        const processResult = await executeQuery(processQuery, [campaignNumber]);

        // Transform results
        const summary: Record<string, Record<string, number>> = {};

        // Process mapping data
        mappingResult.rows.forEach((row: MappingRow) => {
            if (!summary[row.type]) {
                summary[row.type] = {};
            }
            summary[row.type][row.status] = parseInt(row.status_count, 10);
        });

        // Process campaign data
        campaignResult.rows.forEach((row: CampaignRow) => {
            if (!summary[row.type]) {
                summary[row.type] = {};
            }
            summary[row.type][row.status] = parseInt(row.status_count, 10);
        });

        const response: CampaignStatusResponse = {
            campaignNumber,
            summary,
            processes: processResult.rows
        };

        logger.info(`Successfully fetched campaign status for campaign: ${campaignNumber}`);
        return response;
    } catch (error: any) {
        logger.error(`Error fetching campaign status: ${error?.message}`);
        throw error;
    }
}

export { getCampaignStatusService };