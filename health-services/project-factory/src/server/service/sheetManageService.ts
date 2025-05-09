import { templateConfigs } from "../config/templateConfigs";
import { initializeGenerateAndGetResponse } from "../utils/sheetManageUtils";
import config from "../config";
import GenerateTemplateQuery from "../models/GenerateTemplateQuery";


export async function generateDataService(generateRequestQuery: GenerateTemplateQuery, userUuid: string, locale : string = config.localisation.defaultLocale) {
    let { type, tenantId, hierarchyType, campaignId, additionalDetails } = generateRequestQuery;
    tenantId = String(tenantId);
    type = String(type);
    hierarchyType = String(hierarchyType);
    campaignId = String(campaignId);
    const templateConfig = JSON.parse(JSON.stringify(templateConfigs?.[String(type)]));
    const responseToSend = initializeGenerateAndGetResponse(tenantId, type, hierarchyType, campaignId, userUuid, templateConfig, locale, additionalDetails);
    return responseToSend;
}