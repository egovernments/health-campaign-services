import * as express from "express";
import { templateConfigs } from "../config/templateConfigs";
import { getLocaleFromRequest } from "../utils/localisationUtils";
import { initializeGenerateAndGetResponse } from "../utils/sheetManageUtils";


export async function generateDataService(request: express.Request) {
    let { type, tenantId, hierarchyType, campaignId } = request.query;
    const userUuid = request?.body?.RequestInfo?.userInfo?.uuid;
    tenantId = String(tenantId);
    type = String(type);
    hierarchyType = String(hierarchyType);
    campaignId = String(campaignId);
    const locale = getLocaleFromRequest(request);
    const templateConfig = templateConfigs?.[String(type)];
    const responseToSend = initializeGenerateAndGetResponse(tenantId, type, hierarchyType, campaignId, userUuid, templateConfig, locale);
    return responseToSend;
}