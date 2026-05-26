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

/**
 * Checks the generation status by polling the Excel ingestion search API
 * @param requestBody - Request body containing campaign details
 * @param generationId - The specific generation ID to check status for
 * @param generationType - The type of generation (e.g., 'unified-console')
 * @param timeout - Maximum time to wait for generation completion in milliseconds
 * @param pollingInterval - Time between status checks in milliseconds
 * @returns 'completed' | 'failed' | 'timeout' - the final status
 */
async function checkGenerationStatus(
    requestBody: any,
    generationId: string,
    generationType: string,
    timeout: number,
    pollingInterval: number
): Promise<'completed' | 'failed' | 'timeout'> {
    const startTime = Date.now();
    const campaignDetails = requestBody?.CampaignDetails;
    const campaignId = campaignDetails?.id;
    const tenantId = campaignDetails?.tenantId;

    while (Date.now() - startTime < timeout) {
        try {
            const excelIngestionSearchUrl = config.host.excelIngestionHost + config.paths.excelIngestionGenerateSearch;
            const searchRequest = {
                RequestInfo: requestBody?.RequestInfo,
                GenerationSearchCriteria: {
                    tenantId: tenantId,
                    ids: [generationId],
                    referenceTypes: ['campaign'],
                    limit: 5,
                    offset: 0
                }
            };

            const response = await httpRequest(
                excelIngestionSearchUrl,
                searchRequest,
                undefined,
                'post',
                undefined
            );

            const generationDetails = response?.GenerationDetails || [];
            const targetGeneration = generationDetails.find((g: any) =>
                g.id === generationId &&
                g.tenantId === tenantId &&
                g.referenceId === campaignId &&
                g.type === generationType
            );

            if (targetGeneration) {
                const status = targetGeneration.status?.toLowerCase();
                logger.info(`Excel generation status for ID ${generationId} (campaign ${campaignId}): ${status}`);

                if (status === 'completed') {
                    logger.info(`Excel generation completed successfully for ID ${generationId} (campaign ${campaignId})`);
                    if (targetGeneration.fileStoreId) {
                        logger.info(`Generated file stored with ID: ${targetGeneration.fileStoreId}`);
                    }
                    return 'completed';
                } else if (status === 'failed' || status === 'error') {
                    logger.error(`Excel generation failed for ID ${generationId} (campaign ${campaignId}) with status: ${status}`);
                    return 'failed';
                }
                // Status is pending or in-progress, continue polling
                logger.debug(`Generation ${generationId} is still in progress with status: ${status}`);
            } else {
                logger.debug(`Generation ${generationId} not found in response, continuing to poll...`);
            }

            // Wait before next polling attempt
            await new Promise(resolve => setTimeout(resolve, pollingInterval));

        } catch (error: any) {
            logger.error(`Error checking generation status for ID ${generationId}: ${error.message}`);
            // Continue polling on error
            await new Promise(resolve => setTimeout(resolve, pollingInterval));
        }
    }

    logger.error(`Excel generation timed out after ${timeout}ms for ID ${generationId} (campaign ${campaignId})`);
    return 'timeout';
}

/**
 * Initiates Excel generation with the Excel ingestion service
 * @returns Object containing generation ID and type from the response
 */
async function initiateExcelGeneration(
    requestBody: any,
    referenceIdOverride?: string,
    referenceTypeOverride?: string,
    typeOverride?: string,
    additionalDetailsOverride?: Record<string, any>
): Promise<{ generationId: string; type: string }> {
    const campaignDetails = requestBody?.CampaignDetails;
    const tenantId = campaignDetails?.tenantId;
    const campaignId = campaignDetails?.id;
    const hierarchyType = campaignDetails?.hierarchyType;
    const excelIngestionUrl = config.host.excelIngestionHost + config.paths.excelIngestionGenerate;

    const generationType = typeOverride || 'unified-console';
    const generateResource = {
        tenantId: tenantId,
        type: generationType,
        hierarchyType: hierarchyType,
        locale: getLocaleFromRequestInfo(requestBody?.RequestInfo),
        referenceId: referenceIdOverride || campaignId,
        referenceType: referenceTypeOverride || 'campaign',
        additionalDetails: additionalDetailsOverride || {}
    };

    const requestBodyToCallGenerate = {
        RequestInfo: requestBody?.RequestInfo,
        GenerateResource: generateResource
    };

    const response = await httpRequest(
        excelIngestionUrl,
        requestBodyToCallGenerate,
        undefined,
        'post',
        undefined
    );

    const generationResource = response?.GenerateResource;
    const generationId = generationResource?.id;

    if (!generationId) {
        throw new Error(`No generation ID received from Excel ingestion service for campaign ${campaignId}`);
    }

    logger.info(`Successfully initiated excel-ingestion generate API for campaign ${campaignId}`);
    logger.info(`Generation details - ID: ${generationId}, Type: ${generationType}, Status: ${generationResource?.status}`);

    return {
        generationId,
        type: generationType
    };
}

/**
 * Calls Excel ingestion service with timeout and retry logic
 */
export async function callExcelIngestionService(
    requestBody: any,
    referenceIdOverride?: string,
    referenceTypeOverride?: string,
    typeOverride?: string,
    additionalDetailsOverride?: Record<string, any>
) {
    const campaignId = requestBody?.CampaignDetails?.id;
    const { generationTimeout, pollingInterval, maxRetries } = config.excelIngestionConfig;

    let retryCount = 0;
    let generationCompleted = false;

    while (retryCount <= maxRetries && !generationCompleted) {
        try {
            if (retryCount > 0) {
                logger.info(`Retry attempt ${retryCount}/${maxRetries} for Excel generation of campaign ${campaignId}`);
            }

            // Initiate the generation and get the generation ID and type
            const { generationId, type } = await initiateExcelGeneration(
                requestBody,
                referenceIdOverride,
                referenceTypeOverride,
                typeOverride,
                additionalDetailsOverride
            );

            // Check generation status with polling using the specific generation ID
            const status = await checkGenerationStatus(
                requestBody,
                generationId,
                type,
                generationTimeout,
                pollingInterval
            );

            if (status === 'completed') {
                logger.info(`Excel generation completed successfully for campaign ${campaignId}, generation ID: ${generationId}`);
                generationCompleted = true;
                return;
            } else if (status === 'failed') {
                // Generation failed - immediately retry if attempts remaining
                logger.warn(`Excel generation failed for generation ID ${generationId} (campaign ${campaignId})`);
                if (retryCount < maxRetries) {
                    logger.info(`Immediately retrying Excel generation for campaign ${campaignId}...`);
                    // Small delay before retry
                    await new Promise(resolve => setTimeout(resolve, 2000));
                } else {
                    throw new Error(`Excel generation failed for campaign ${campaignId} after ${retryCount + 1} attempts`);
                }
            } else if (status === 'timeout') {
                // Generation timed out - retry if attempts remaining
                logger.warn(`Excel generation timed out for generation ID ${generationId} (campaign ${campaignId})`);
                if (retryCount < maxRetries) {
                    logger.info(`Retrying Excel generation for campaign ${campaignId} after timeout...`);
                    // Small delay before retry
                    await new Promise(resolve => setTimeout(resolve, 2000));
                } else {
                    throw new Error(`Excel generation timed out for campaign ${campaignId} after ${retryCount + 1} attempts`);
                }
            }

        } catch (error: any) {
            logger.error(`Error in Excel generation attempt ${retryCount + 1} for campaign ${campaignId}: ${error.message}`);

            if (retryCount < maxRetries) {
                logger.info(`Retrying after error for campaign ${campaignId}...`);
                // Small delay before retry
                await new Promise(resolve => setTimeout(resolve, 3000));
            } else {
                throw new Error(`Excel generation failed after ${maxRetries + 1} attempts for campaign ${campaignId}: ${error.message}`);
            }
        }

        retryCount++;
    }

    if (!generationCompleted) {
        throw new Error(`Excel generation failed to complete after ${maxRetries + 1} attempts for campaign ${campaignId}`);
    }
}

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
        // Apply 2-second timeout after the condition check
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
            // For microplan, trigger all three types
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
        // throwError("COMMON", 400, "GENERATE_ERROR", `Error while generating user/facility/boundary: ${error.message}`);
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



// const buildGenerateRequest = (request: any) => {
//     const newRequestBody = {
//         RequestInfo: request?.body?.RequestInfo
//     };

//     const params = {
//         type: request?.query?.type,
//         tenantId: request?.query?.tenantId,
//         forceUpdate: 'true',
//         hierarchyType: request?.query?.hierarchyType,
//         campaignId: request?.query?.campaignId
//     };

//     return replicateRequest(request, newRequestBody, params);
// };

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
