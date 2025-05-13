import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getRelatedDataWithCampaign } from "../utils/genericUtils";

// This will be a dynamic template class for different types
export class TemplateClass {
    // Static generate function
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>
    ): Promise<SheetMap> {
        logger.info("Processing file...");
        logger.info(`ResourceDetails to send: ${JSON.stringify(resourceDetails)}`);

        const reverseLocalizationMap = new Map<string, string>();
        for (const [key, value] of Object.entries(localizationMap)) {
            reverseLocalizationMap.set(String(value), key);
        }

        const campaignResponse = await searchProjectTypeCampaignService({
            tenantId: resourceDetails.tenantId,
            ids: [resourceDetails?.campaignId],
        });

        const campaignDetails = campaignResponse?.CampaignDetails?.[0];
        if (!campaignDetails) throw new Error("Campaign not found");

        const campaignNumber = campaignDetails.campaignNumber;

        const jsonData = wholeSheetData[getLocalizedName("HCM_ADMIN_CONSOLE_USER_LIST", localizationMap)];

        const localizedMobileKey = getLocalizedName("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", localizationMap);

        const sheetUserMap = new Map<string, any>();
        jsonData.forEach((row: any) => {
            const mobile = row?.[localizedMobileKey];
            if (mobile) sheetUserMap.set(mobile, row);
        });

        const existingUsers = await getRelatedDataWithCampaign(resourceDetails?.type, campaignNumber);
        const existingUserMap = new Map<string, any>();
        existingUsers?.forEach((user: any) => {
            const mobile = user?.data?.["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"];
            if (mobile) existingUserMap.set(mobile, user);
        });
        const sheetMap: SheetMap = {};
        return sheetMap;
    }

}
