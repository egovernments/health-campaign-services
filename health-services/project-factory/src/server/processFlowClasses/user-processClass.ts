import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getRelatedDataWithCampaign } from "../utils/genericUtils";
import { dataRowStatuses } from "../config/constants";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";

// This will be a dynamic template class for different types
export class TemplateClass {
    // Static generate function
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>
    ): Promise<SheetMap> {
        logger.info("Processing file...");
        logger.info(`ResourceDetails: ${JSON.stringify(resourceDetails)}`);

        const reverseMap = this.getReverseLocalizationMap(localizationMap);
        const campaign = await this.getCampaignDetails(resourceDetails);

        const sheetData = wholeSheetData[getLocalizedName("HCM_ADMIN_CONSOLE_USER_LIST", localizationMap)];
        const mobileKey = getLocalizedName("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", localizationMap);

        const newUsers = await this.extractNewUsers(sheetData, mobileKey, campaign.campaignNumber, resourceDetails, reverseMap);
        await this.persistInBatches(newUsers);

        const waitTime = Math.max(5000, newUsers.length * 8);
        logger.info(`Waiting for ${waitTime} ms for persistence...`);
        await new Promise((res) => setTimeout(res, waitTime));

        await this.createUserFromTableData(campaign.campaignNumber);
        return {};
    }

    private static getReverseLocalizationMap(localizationMap: Record<string, string>): Map<string, string> {
        const reverse = new Map<string, string>();
        Object.entries(localizationMap).forEach(([key, val]) => reverse.set(val, key));
        return reverse;
    }

    private static async getCampaignDetails(resourceDetails: any): Promise<any> {
        const response = await searchProjectTypeCampaignService({
            tenantId: resourceDetails.tenantId,
            ids: [resourceDetails?.campaignId],
        });
        const campaign = response?.CampaignDetails?.[0];
        if (!campaign) throw new Error("Campaign not found");
        return campaign;
    }

    private static async extractNewUsers(
        sheetData: any[],
        mobileKey: string,
        campaignNumber: string,
        resourceDetails: any,
        reverseMap: Map<string, string>
    ): Promise<any[]> {
        const userMap : any = Object.fromEntries(sheetData.map((row: any) => [row?.[mobileKey], row]).filter(([m]) => m));

        const existing = await getRelatedDataWithCampaign(resourceDetails?.type, campaignNumber);
        const existingMap = new Map<string, any>(existing.map((u: any) => [String(u?.data?.[reverseMap.get(mobileKey) || mobileKey]), u]).filter(([m]:any) => m));

        const newEntries = [];
        for (const [mobile, row] of Object.entries(userMap)) {
            if (existingMap.has(String(mobile))) continue;
            const data = Object.fromEntries(Object.entries(row as any).map(([k, v]) => [reverseMap.get(k) || k, v]));
            newEntries.push({
                campaignNumber,
                data,
                type: resourceDetails?.type,
                uniqueIdentifier: mobile,
                uniqueIdAfterProcess: null,
                status: dataRowStatuses.pending
            });
        }
        return newEntries;
    }

    private static async persistInBatches(users: any[]): Promise<void> {
        const BATCH_SIZE = 100;
        for (let i = 0; i < users.length; i += BATCH_SIZE) {
            const batch = users.slice(i, i + BATCH_SIZE);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC);
        }
    }

    

    static async createUserFromTableData(campaignNumber: string) {
        const allCurrentUsers = await getRelatedDataWithCampaign("user",campaignNumber);
        const usersToCreate = allCurrentUsers?.filter((user: any) => (user?.status === dataRowStatuses.pending || user?.status === dataRowStatuses.failed));
        logger.info(`${usersToCreate?.length} users to create`);
        // TODO
    }
}
