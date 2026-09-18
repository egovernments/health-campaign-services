import { getLocalizedMessagesHandler, processGenerate } from "./genericUtils";
import _ from 'lodash';
import { logger } from "./logger";
import { generateDataService } from "../service/sheetManageService";
import config from "../config";
import { getLocaleFromRequestInfo, getLocalisationModuleName } from "./localisationUtils";
import { getBoundarySheetData } from "../api/genericApis";
import { checkIfSourceIsMicroplan } from "./campaignUtils";
import { httpRequest } from "./request";
import { RequestInfo } from "../config/models/requestInfoSchema";

function extractProperties(obj: any) {
    return {
        code: obj.code || null,
        includeAllChildren: obj.includeAllChildren || null,
        isRoot: obj.isRoot || null
    };
}

function areBoundariesSame(existingBoundaries: any, currentBoundaries: any) {
    const existing = existingBoundaries ?? [];
    const current = currentBoundaries ?? [];

    if (existing.length === 0 && current.length === 0) return true;
    if (existing.length !== current.length) return false;
    const existingSetOfBoundaries = new Set(existing.map((exboundary: any) => JSON.stringify(extractProperties(exboundary))));
    const currentSetOfBoundaries = new Set(current.map((currboundary: any) => JSON.stringify(extractProperties(currboundary))));
    return _.isEqual(existingSetOfBoundaries, currentSetOfBoundaries);
}

function isCampaignTypeSame(request: any) {
    const existingCampaignType = request?.body?.ExistingCampaignDetails?.projectType;
    const currentCampaignType = request?.body?.CampaignDetails?.projectType;
    return _.isEqual(existingCampaignType, currentCampaignType);
}

/** Delegates template generation to the excel-ingestion service; swallows errors so a generate failure never blocks the caller. */
export async function callExcelIngestionService(requestBody: any, referenceIdOverride?: string, referenceTypeOverride?: string, typeOverride?: string, additionalDetailsOverride?: Record<string, any>) {
    try {
        const campaignDetails = requestBody?.CampaignDetails;
        const tenantId = campaignDetails?.tenantId;
        const campaignId = campaignDetails?.id;
        const hierarchyType = campaignDetails?.hierarchyType;
        const excelIngestionUrl = config.host.excelIngestionHost + config.paths.excelIngestionGenerate;

        const generateResource = {
            tenantId: tenantId,
            type: typeOverride || 'unified-console',
            hierarchyType: hierarchyType,
            locale: getLocaleFromRequestInfo (requestBody?.RequestInfo),
            referenceId: referenceIdOverride || campaignId,
            referenceType: referenceTypeOverride || 'campaign',
            additionalDetails: additionalDetailsOverride || {}
        };

        const requestBodyToCallGenerate = {
            RequestInfo: requestBody?.RequestInfo,
            GenerateResource: generateResource
        };

        await httpRequest(
            excelIngestionUrl,
            requestBodyToCallGenerate,
            undefined,
            'post',
            undefined
        );
        logger.info(`Successfully called excel-ingestion generate API for campaign ${campaignId}`);
    } catch (error: any) {
        logger.error(`Error calling excel-ingestion generate API: ${error.message}`);
    }
}

/** Queries excel-ingestion for the completed generation result of a campaign's template. */
export async function callExcelIngestionGenerateSearch(requestBody: any) {
    try {
        const excelIngestionSearchUrl = config.host.excelIngestionHost + config.paths.excelIngestionGenerateSearch;
        const requestInfo = requestBody?.RequestInfo;
        const campaignDetails = requestBody?.CampaignDetails;


        const requestBodyToCallSearch = {
            RequestInfo: requestInfo,
            GenerationSearchCriteria: {
                tenantId: campaignDetails?.tenantId,
                referenceIds: [campaignDetails?.id],
                statuses: ["completed"],
            }
        };

        const response = await httpRequest(
            excelIngestionSearchUrl,
            requestBodyToCallSearch,
            undefined,
            'post',
            undefined
        );

        logger.info(`Successfully called excel-ingestion generate search API for referenceIds: ${campaignDetails?.id}`);
        return response;
    } catch (error: any) {
        logger.error(`Error calling excel-ingestion generate search API: ${error.message}`);
        throw error;
    }
}

async function callGenerateIfBoundariesOrCampaignTypeDiffer(request: any) {
    try {
        // Let the just-persisted campaign changes settle before regenerating templates
        await new Promise(resolve => setTimeout(resolve, 2000));
        const campaignDetails = request?.body?.CampaignDetails;
        const tenantId = campaignDetails?.tenantId;
        const campaignId = campaignDetails?.id;
        const hierarchyType = campaignDetails?.hierarchyType;
        const useruuid = request?.body?.RequestInfo?.userInfo?.uuid || campaignDetails?.auditDetails?.createdBy;
        const locale = getLocaleFromRequestInfo(request?.body?.RequestInfo);

        const isMicroplan = checkIfSourceIsMicroplan(campaignDetails);
        const isUnifiedCampaign = campaignDetails?.additionalDetails?.isUnifiedCampaign;
        if(isUnifiedCampaign){
            await callExcelIngestionService(request?.body);
            return;
        }

        if (isMicroplan) {
            const types = ["boundary", "facilityWithBoundary"];
            for (const t of types) {
                const newRequestToGenerate = {
                    ...request,
                    query: {
                        ...request.query,
                        type: t,
                        tenantId,
                        hierarchyType,
                        campaignId,
                        forceUpdate: 'true'
                    }
                };
                await callGenerate(newRequestToGenerate, t);
            }
        } else {
            const requestInfo = request?.body?.RequestInfo;
            triggerGenerate("boundary", tenantId, hierarchyType, campaignId, useruuid, locale, requestInfo);
            triggerGenerate("user", tenantId, hierarchyType, campaignId, useruuid, locale, requestInfo);
            triggerGenerate("facility", tenantId, hierarchyType, campaignId, useruuid, locale, requestInfo);
        }
    } catch (error: any) {
        logger.error(error);
    }
}

function isSourceDifferent(request: any){
    const ExistingCampaignDetails = request?.body?.ExistingCampaignDetails;
    const CampaignDetails = request?.body?.CampaignDetails;

    if(CampaignDetails?.additionalDetails?.source !== ExistingCampaignDetails?.additionalDetails?.source){
        return true;
    }
    return false;
}

/** Runs the template generate flow for a type; the *WithBoundary types additionally attach localized boundary sheet data. */
export async function callGenerate(request: any, type: any, enableCaching = false) {
    logger.info(`calling generate api for type ${type}`);
    if (type === "facilityWithBoundary" || type == "userWithBoundary") {
        const { hierarchyType } = request.query;
        const localizationMapHierarchy = hierarchyType && await getLocalizedMessagesHandler(
            request,
            request.query.tenantId,
            getLocalisationModuleName(hierarchyType)
        );
        const localizationMapModule = await getLocalizedMessagesHandler(request, request.query.tenantId);
        const localizationMap = { ...localizationMapHierarchy, ...localizationMapModule };
        const filteredBoundary = await getBoundarySheetData(request, localizationMap);
        await processGenerate(request, enableCaching, filteredBoundary);
    } else {
        await processGenerate(request, enableCaching);
    }
}

/** Fire-and-forget generate for one resource type; logs and swallows errors so one type's failure doesn't abort the others. */
export async function triggerGenerate(type: string, tenantId: string, hierarchyType: string, campaignId: string, userUuid: string, locale: string = config.localisation.defaultLocale, requestInfo?: RequestInfo) {

    logger.info(`Calling generate API for type ${type}`);

    const generateRequestQuery = {
        type,
        tenantId,
        hierarchyType,
        campaignId
    };

    try {
        await generateDataService(generateRequestQuery, userUuid, locale, requestInfo);
    } catch (error: any) {
        logger.error(`Error in triggerGenerate for type ${type}: ${error?.message}`, error);
    }
}



/** True (with the new boundaries) when a campaign update changed boundaries, source, or campaign type and templates must be regenerated. */
export const isGenerationTriggerNeeded = (request: any) => {
    const ExistingCampaignDetails = request?.body?.ExistingCampaignDetails;
    const boundaries = request?.body?.CampaignDetails?.boundaries;
    const newBoundaries = boundaries?.filter((boundary: any) => !boundary.insertedAfter) || [];


    if (!areBoundariesSame(ExistingCampaignDetails?.boundaries, newBoundaries) || isSourceDifferent(request) || !isCampaignTypeSame(request)) {
        logger.info("Boundaries or Campaign Type  differ, generating new resources");
        return { trigger: true, newBoundaries };
    }
    return { trigger: false };
}



export { callGenerateIfBoundariesOrCampaignTypeDiffer, areBoundariesSame }
