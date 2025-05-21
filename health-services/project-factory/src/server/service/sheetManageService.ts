import { generationtTemplateConfigs } from "../config/generationtTemplateConfigs";
import { processTemplateConfigs } from "../config/processTemplateConfigs";
import { generateResource, initializeGenerateAndGetResponse, initializeProcessAndGetResponse, processResource } from "../utils/sheetManageUtils";
import config from "../config";
import {GenerateTemplateQuery} from "../models/GenerateTemplateQuery";
import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { validateGenerateQuery, validateResourceDetails } from "../validators/campaignValidators";


export async function generateDataService(generateRequestQuery: GenerateTemplateQuery, userUuid: string, locale : string = config.localisation.defaultLocale) {
    let { type, tenantId, hierarchyType, campaignId } = generateRequestQuery;
    await validateGenerateQuery(generateRequestQuery);
    tenantId = String(tenantId);
    type = String(type);
    hierarchyType = String(hierarchyType);
    campaignId = String(campaignId);
    const generationTemplateConfig = JSON.parse(JSON.stringify(generationtTemplateConfigs?.[String(type)]));
    const responseToSend = await initializeGenerateAndGetResponse(tenantId, type, hierarchyType, campaignId, userUuid, locale);
    generateResource(responseToSend, generationTemplateConfig);
    return responseToSend;
}

export async function processDataService(ResourceDetails : ResourceDetails, userUuid: string, locale : string = config.localisation.defaultLocale) {
    await validateResourceDetails(ResourceDetails);
    const processTemplateConfig = JSON.parse(JSON.stringify(processTemplateConfigs?.[String(ResourceDetails.type)]));
    const responseToSend = await initializeProcessAndGetResponse(ResourceDetails, userUuid, processTemplateConfig, locale);
    processResource(responseToSend, processTemplateConfig, locale);
    return responseToSend;
}