import { getLocalizedMessagesHandler, processGenerate, replicateRequest } from "./genericUtils";
import _ from 'lodash';
import { getFormattedStringForDebug, logger } from "./logger";
import { generateDataService } from "../service/sheetManageService";
import config from "../config";
import { getLocaleFromRequestInfo, getLocalisationModuleName } from "./localisationUtils";
import { getBoundarySheetData } from "../api/genericApis";
import { checkIfSourceIsMicroplan } from "./campaignUtils";

// Now you can use Lodash functions with the "_" prefix, e.g., _.isEqual(), _.sortBy(), etc.
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

    // If both are empty, return true
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

async function callGenerateIfBoundariesOrCampaignTypeDiffer(request: any) {
    try {
        if (checkIfSourceIsMicroplan(request?.body?.CampaignDetails)) {
            await handleMicroplanFlow(request);
        } else {
            await handleNonMicroplanFlow(request);
        }
    } catch (error: any) {
        logger.error(error);
        // throwError("COMMON", 400, "GENERATE_ERROR", `Error while generating user/facility/boundary: ${error.message}`);
    }
}

async function handleMicroplanFlow(request: any) {
    const ExistingCampaignDetails = request?.body?.ExistingCampaignDetails;
    const boundaries = request?.body?.boundariesCombined;

    if (ExistingCampaignDetails) {
        const isBoundaryDiff = !areBoundariesSame(ExistingCampaignDetails?.boundaries, boundaries);
        const isSourceDiff = isSourceDifferent(request);
        const isCampaignTypeDiff = !isCampaignTypeSame(request);

        if (isBoundaryDiff || isSourceDiff || isCampaignTypeDiff) {
            logger.info("Boundaries or Campaign Type differ, generating new resources for microplan flow");
            await new Promise(resolve => setTimeout(resolve, 2000)); // Wait 2 seconds

            const { query } = request;
            const campaignDetails = request?.body?.CampaignDetails;
            const params = {
                tenantId: campaignDetails?.tenantId,
                forceUpdate: 'true',
                hierarchyType: campaignDetails?.hierarchyType,
                campaignId: campaignDetails?.id
            };

            const newRequestBody = {
                RequestInfo: request?.body?.RequestInfo,
                Filters: { boundaries }
            };

            const types = ["boundary", "facilityWithBoundary"];

            for (const type of types) {
                const newParams = { ...query, ...params, type };
                const newRequest = replicateRequest(request, newRequestBody, newParams);
                await callGenerate(newRequest, type);
                logger.debug(`generate request type: ${type}, boundaries: ${getFormattedStringForDebug(boundaries)}`);
            }
        }
    }
}

async function handleNonMicroplanFlow(request: any) {
    await new Promise(resolve => setTimeout(resolve, 2000)); // Wait 2 seconds

    const requestInfo = request?.body?.RequestInfo;
    const campaignDetails = request?.body?.CampaignDetails;

    const useruuid = requestInfo?.userInfo?.uuid || campaignDetails?.auditDetails?.createdBy;
    const locale = getLocaleFromRequestInfo(requestInfo);
    const tenantId = campaignDetails?.tenantId;
    const hierarchyType = campaignDetails?.hierarchyType;
    const campaignId = campaignDetails?.id;

    logger.info(`Generating new resources for campaignId: ${campaignId}`);

    triggerGenerate("boundary", tenantId, hierarchyType, campaignId, useruuid, locale);
    triggerGenerate("user", tenantId, hierarchyType, campaignId, useruuid, locale);
    triggerGenerate("facility", tenantId, hierarchyType, campaignId, useruuid, locale);
}



function isSourceDifferent(request: any){
    const ExistingCampaignDetails = request?.body?.ExistingCampaignDetails;
    const CampaignDetails = request?.body?.CampaignDetails;

    if(CampaignDetails?.additionalDetails?.source !== ExistingCampaignDetails?.additionalDetails?.source){
        return true;
    }
    return false;
}

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

export async function triggerGenerate(type: string, tenantId: string, hierarchyType: string, campaignId: string, userUuid: string, locale: string = config.localisation.defaultLocale) {

    logger.info(`Calling generate API for type ${type}`);

    const generateRequestQuery = {
        type,
        tenantId,
        hierarchyType,
        campaignId
    };

    try {
        await generateDataService(generateRequestQuery, userUuid, locale);
    } catch (error: any) {
        logger.error(`Error in triggerGenerate for type ${type}: ${error?.message}`, error);
    }
}



const buildGenerateRequest = (request: any) => {
    const newRequestBody = {
        RequestInfo: request?.body?.RequestInfo
    };

    const params = {
        type: request?.query?.type,
        tenantId: request?.query?.tenantId,
        forceUpdate: 'true',
        hierarchyType: request?.query?.hierarchyType,
        campaignId: request?.query?.campaignId
    };

    return replicateRequest(request, newRequestBody, params);
};

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



export { callGenerateIfBoundariesOrCampaignTypeDiffer, areBoundariesSame, buildGenerateRequest }
