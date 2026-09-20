import express from "express";
import { processGenericRequest } from "../api/campaignApis";
import { createAndUploadFile, getBoundarySheetData } from "../api/genericApis";
import { getLocalizedName, getResourceDetails, processDataSearchRequest } from "../utils/campaignUtils";
import { addDataToSheet, enrichResourceDetails, getLocalizedMessagesHandler, searchGeneratedResources, searchAllGeneratedResources, processGenerate, throwError, searchCampaignData, searchMappingData } from "../utils/genericUtils";
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { validateCreateRequest, validateDownloadRequest, validateSearchRequest } from "../validators/campaignValidators";
import { validateGenerateRequest } from "../validators/genericValidator";
import { getLocaleFromRequestInfo, getLocalisationModuleName } from "../utils/localisationUtils";
import { getBoundaryTabName } from "../utils/boundaryUtils";
import { getNewExcelWorkbook } from "../utils/excelUtils";
import { redis, checkRedisConnection } from "../utils/redisUtils";
import config from '../config/index'
import {callGenerate } from "../utils/generateUtils";
import { generatedResourceStatuses } from "../config/constants";
import { isCampaignIdOfMicroplan } from "../utils/campaignUtils";
import { generateDataService as generateTemplateDataService } from "./sheetManageService";
import { localityKeyOf } from "../utils/generatedResourceUtils";
import { GenerateTemplateQuery } from "../models/GenerateTemplateQuery";
import { generationtTemplateConfigs } from "../config/generationtTemplateConfigs";


const generateDataService = async (request: express.Request) => {
    await validateGenerateRequest(request);
    logger.info("VALIDATED THE DATA GENERATE REQUEST");
    await processGenerate(request);
    return request?.body?.generatedResource;
};

// boundary stays on the legacy generate flow, which owns the per-level tab splitting the v2 class has no equivalent for.
const legacyOwnedGenerationTypes = new Set<string>(["boundary"]);
const sheetManageGenerationTypes = new Set<string>(
    Object.keys(generationtTemplateConfigs).filter((type) => !legacyOwnedGenerationTypes.has(type))
);

const downloadGeneratedStatusPollIntervalMs = 1000;
const downloadGeneratedStatusPollMaxAttempts = 45;
const staleInProgressResourceThresholdMs = 5 * 60 * 1000;

function toEpoch(value: unknown): number {
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string") {
        const parsed = parseInt(value, 10);
        return Number.isFinite(parsed) ? parsed : NaN;
    }
    return NaN;
}

function isStaleInProgressResource(resource: any): boolean {
    if (resource?.status !== generatedResourceStatuses.inprogress) return false;
    const createdTime = toEpoch(resource?.auditDetails?.createdTime);
    if (!Number.isFinite(createdTime)) return false;
    return Date.now() - createdTime > staleInProgressResourceThresholdMs;
}

// Falls back to createdTime so a row without a completion stamp yields a shorter window, never a longer one
function completionEpoch(resource: any): number {
    const completedAt = toEpoch(resource?.auditDetails?.lastModifiedTime);
    if (Number.isFinite(completedAt)) return completedAt;
    return toEpoch(resource?.auditDetails?.createdTime);
}

function isReusableCompletedResource(resource: any): boolean {
    if (resource?.status !== generatedResourceStatuses.completed) return false;
    if (!resource?.fileStoreid) return false;
    const reuseWindowMs = config?.generatedResource?.reuseWindowMs ?? 0;
    if (reuseWindowMs <= 0) return false;
    const completedAt = completionEpoch(resource);
    if (!Number.isFinite(completedAt)) return false;
    return Date.now() - completedAt <= reuseWindowMs;
}

function newestForLocality(resources: any[], localityKey: string | null): any {
    return (Array.isArray(resources) ? resources : [])
        .filter((resource) => localityKeyOf(resource) === localityKey)
        .sort((a, b) => toEpoch(b?.auditDetails?.createdTime) - toEpoch(a?.auditDetails?.createdTime))[0];
}

function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForGeneratedResourceTerminalStatus(
    requestQuery: any,
    locale: string,
    generatedResourceId: string
): Promise<any | null> {
    for (let attempt = 0; attempt < downloadGeneratedStatusPollMaxAttempts; attempt++) {
        const generatedResources = await searchGeneratedResources(
            { ...requestQuery, id: generatedResourceId },
            locale
        );
        const generatedResource = generatedResources?.[0];
        const status = generatedResource?.status;

        if (status === generatedResourceStatuses.completed || status === generatedResourceStatuses.failed) {
            return generatedResource;
        }

        if (attempt < downloadGeneratedStatusPollMaxAttempts - 1) {
            await sleep(downloadGeneratedStatusPollIntervalMs);
        }
    }
    return null;
}


const downloadDataService = async (request: express.Request) => {
    await validateDownloadRequest(request);
    logger.info("VALIDATED THE DATA DOWNLOAD REQUEST");

    const type = String(request.query.type);
    const locale = getLocaleFromRequestInfo(request?.body?.RequestInfo);
    const hasRequestedGeneratedId = Boolean(request?.query?.id);
    let responseData = hasRequestedGeneratedId ? await searchGeneratedResources(request?.query, locale) : [];
    const resourceDetails = await getResourceDetails(request);
    const tenantId = String(request?.query?.tenantId || "");
    const hierarchyType = String(request?.query?.hierarchyType || "");
    const campaignId = String(request?.query?.campaignId || "");
    const localityCode = request?.query?.localityCode ? String(request?.query?.localityCode) : undefined;
    const forceUpdate = String(request?.query?.forceUpdate || "") === "true";
    const userUuid = request?.body?.RequestInfo?.userInfo?.uuid || "null";

    if (!hasRequestedGeneratedId) {
        const requestLocalityKey = localityKeyOf({ additionalDetails: { localityCode } });
        const candidates = await searchAllGeneratedResources(
            {
                tenantId,
                type,
                hierarchyType,
                campaignId,
                status: `${generatedResourceStatuses.completed},${generatedResourceStatuses.inprogress}`
            },
            locale
        );
        const latestResource = newestForLocality(candidates || [], requestLocalityKey);
        const hasFreshInProgressResource =
            latestResource?.status === generatedResourceStatuses.inprogress
            && !isStaleInProgressResource(latestResource);
        const hasReusableCompletedResource =
            !forceUpdate && isReusableCompletedResource(latestResource);

        if (hasFreshInProgressResource || hasReusableCompletedResource) {
            responseData = [latestResource];
            logger.info(
                `Reusing generation id=${latestResource.id} status=${latestResource.status} `
                + `for localityCode=${requestLocalityKey}.`
            );
        } else {
            if (latestResource?.status === generatedResourceStatuses.inprogress) {
                logger.warn(`Found stale in-progress generation id=${latestResource.id}; creating a fresh one.`);
            } else {
                logger.info(`Generating fresh template for download — campaignId=${campaignId}, type=${type}`);
            }

            let isMicroplan = false;
            try {
                isMicroplan = await isCampaignIdOfMicroplan(tenantId, campaignId);
            } catch (e) {
                throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", "Error checking if campaign id is of microplan");
            }

            if (!isMicroplan && sheetManageGenerationTypes.has(type)) {
                const generateTemplateQuery: GenerateTemplateQuery = {
                    type,
                    tenantId,
                    hierarchyType,
                    campaignId,
                    ...(localityCode ? { localityCode } : {}),
                };
                const generatedResource = await generateTemplateDataService(
                    generateTemplateQuery,
                    userUuid,
                    locale,
                    request?.body?.RequestInfo
                );
                responseData = generatedResource ? [generatedResource] : [];
            } else {
                const newRequestToGenerate = {
                    ...request,
                    query: {
                        ...request.query,
                        type,
                        tenantId,
                        hierarchyType,
                        campaignId,
                        localityCode,
                        forceUpdate: 'true'
                    }
                };
                await callGenerate(newRequestToGenerate, type);
                if (Array.isArray(newRequestToGenerate?.body?.generatedResource) && newRequestToGenerate.body.generatedResource.length > 0) {
                    responseData = newRequestToGenerate.body.generatedResource;
                } else {
                    responseData = await searchGeneratedResources(newRequestToGenerate?.query, locale);
                }
            }
        }

        const generatedResourceId = responseData?.[0]?.id;
        if (generatedResourceId && responseData?.[0]?.status === generatedResourceStatuses.inprogress) {
            const terminalResource = await waitForGeneratedResourceTerminalStatus(request?.query, locale, generatedResourceId);
            if (terminalResource) {
                responseData = [terminalResource];
            }
        }
    }

    if (resourceDetails != null && responseData != null && responseData.length > 0) {
        responseData[0].additionalDetails = {
            ...(responseData[0].additionalDetails || {}),
            ...(resourceDetails?.additionalDetails || {})
        };
    }

    return responseData;
}

const getBoundaryDataService = async (
    request: express.Request, enableCaching = false) => {
    try {
        const { hierarchyType, campaignId } = request?.query;
        const cacheTTL = config?.cacheTime;
        const cacheKey = `${campaignId}-${hierarchyType}`;
        let isRedisConnected = false;
        let cachedData: any = null;
        if (cacheKey && enableCaching) {
            isRedisConnected = await checkRedisConnection();
            cachedData = await redis.get(cacheKey);
        }
        if (cachedData) {
            logger.info("CACHE HIT :: " + cacheKey);
            logger.debug(`CACHED DATA :: ${getFormattedStringForDebug(cachedData)}`);

            if (config.cacheValues.resetCache) {
                await redis.expire(cacheKey, cacheTTL);
            }

            return JSON.parse(cachedData);
        } else {
            logger.info("NO CACHE FOUND :: REQUEST :: " + cacheKey);
        }
        const workbook = getNewExcelWorkbook();
        const localizationMapHierarchy = hierarchyType && await getLocalizedMessagesHandler(request, request?.query?.tenantId, getLocalisationModuleName(hierarchyType), true);
        const localizationMapModule = await getLocalizedMessagesHandler(request, request?.query?.tenantId);
        const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };
        const boundarySheetData: any = await getBoundarySheetData(request, localizationMap,enableCaching === true);
        const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap);
        const boundarySheet = workbook.addWorksheet(localizedBoundaryTab);
        addDataToSheet(request, boundarySheet, boundarySheetData, '93C47D', 40, true);
        const boundaryFileDetails: any = await createAndUploadFile(workbook, request);
        logger.info("RETURNS THE BOUNDARY RESPONSE");
        if (cacheKey && isRedisConnected) {
            await redis.set(cacheKey, JSON.stringify(boundaryFileDetails), "EX", cacheTTL);
        }
        return boundaryFileDetails;
    } catch (e: any) {
        console.log(e)
        logger.error(String(e))
        throw (e);
    }
};


const createDataService = async (request: any) => {

    const hierarchyType = request?.body?.ResourceDetails?.hierarchyType;
    const localizationMapHierarchy = hierarchyType && await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId, getLocalisationModuleName(hierarchyType), true);
    const localizationMapModule = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId);
    const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };
    logger.info("Validating data create request")
    await validateCreateRequest(request, localizationMap);
    logger.info("VALIDATED THE DATA CREATE REQUEST");

    await enrichResourceDetails(request);

    await processGenericRequest(request, localizationMap);
    return request?.body?.ResourceDetails;
}

const searchDataService = async (request: any) => {
    await validateSearchRequest(request);
    logger.info("VALIDATED THE DATA GENERATE REQUEST");
    await processDataSearchRequest(request);
    return request?.body?.ResourceDetails;
}

/**
 * Search campaign data service with proper validation
 */
const searchCampaignDataService = async (request: any) => {
    try {
        const searchCriteria = request?.body?.SearchCriteria;
        const pagination = request?.body?.Pagination;

        if (!searchCriteria) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "SearchCriteria is required");
        }

        if (!searchCriteria.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required in SearchCriteria");
        }

        if (searchCriteria.uniqueIdentifiers && !Array.isArray(searchCriteria.uniqueIdentifiers)) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "uniqueIdentifiers must be an array");
        }

        const searchParams: any = {
            tenantId: searchCriteria.tenantId,
            type: searchCriteria.type,
            campaignNumber: searchCriteria.campaignNumber,
            status: searchCriteria.status,
            uniqueIdentifiers: searchCriteria.uniqueIdentifiers
        };

        if (pagination) {
            searchParams.offset = pagination.offset || 0;
            searchParams.limit = pagination.limit || 100;

            if (searchParams.limit > 1000) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "limit cannot exceed 1000");
            }
            if (searchParams.offset < 0) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "offset cannot be negative");
            }
        }

        logger.info(`Searching campaign data: ${getFormattedStringForDebug(searchParams)}`);

        const result = await searchCampaignData(searchParams);

        logger.info(`Campaign data search completed: ${result.totalCount} total records found`);
        
        return {
            CampaignData: result.data,
            TotalCount: result.totalCount,
            ...(result.pagination && { Pagination: result.pagination })
        };
        
    } catch (error: any) {
        logger.error("Error in searchCampaignDataService:", error);
        throw error;
    }
}

/**
 * Search mapping data service with proper validation
 */
const searchMappingDataService = async (request: any) => {
    try {
        const searchCriteria = request?.body?.SearchCriteria;
        const pagination = request?.body?.Pagination;

        if (!searchCriteria) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "SearchCriteria is required");
        }

        if (!searchCriteria.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required in SearchCriteria");
        }

        const searchParams: any = {
            tenantId: searchCriteria.tenantId,
            type: searchCriteria.type,
            campaignNumber: searchCriteria.campaignNumber,
            status: searchCriteria.status,
            boundaryCode: searchCriteria.boundaryCode,
            uniqueIdentifierForData: searchCriteria.uniqueIdentifierForData
        };

        if (pagination) {
            searchParams.offset = pagination.offset || 0;
            searchParams.limit = pagination.limit || 100;

            if (searchParams.limit > 1000) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "limit cannot exceed 1000");
            }
            if (searchParams.offset < 0) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "offset cannot be negative");
            }
        }

        logger.info(`Searching mapping data: ${getFormattedStringForDebug(searchParams)}`);

        const result = await searchMappingData(searchParams);

        logger.info(`Mapping data search completed: ${result.totalCount} total records found`);
        
        return {
            MappingData: result.data,
            TotalCount: result.totalCount,
            ...(result.pagination && { Pagination: result.pagination })
        };
        
    } catch (error: any) {
        logger.error("Error in searchMappingDataService:", error);
        throw error;
    }
}

export {
    generateDataService,
    downloadDataService,
    getBoundaryDataService,
    createDataService,
    searchDataService,
    searchCampaignDataService,
    searchMappingDataService
}
