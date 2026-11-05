import express from "express";
import { processGenericRequest } from "../api/campaignApis";
import { createAndUploadFile, getBoundarySheetData } from "../api/genericApis";
import { getLocalizedName, getResourceDetails, processDataSearchRequest } from "../utils/campaignUtils";
import { addDataToSheet, enrichResourceDetails, getLocalizedMessagesHandler, searchGeneratedResources, searchAllGeneratedResources, processGenerate, throwError, searchCampaignData, searchMappingData, getRelatedDataWithCampaign } from "../utils/genericUtils";
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { validateCreateRequest, validateDownloadRequest, validateSearchRequest } from "../validators/campaignValidators";
import { validateGenerateRequest } from "../validators/genericValidator";
import { getLocaleFromRequestInfo, getLocalisationModuleName } from "../utils/localisationUtils";
import { getBoundaryTabName } from "../utils/boundaryUtils";
import { getNewExcelWorkbook } from "../utils/excelUtils";
import { redis, checkRedisConnection } from "../utils/redisUtils";
import config from '../config/index'
import {callGenerate } from "../utils/generateUtils";
import { attendanceSheetNames, dataRowStatuses, generatedResourceStatuses } from "../config/constants";
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
const downloadGeneratedStatusPollMaxAttempts = 60;
const staleInProgressResourceThresholdMs = 5 * 60 * 1000;

const DISTRIBUTOR_ROLE_CODE = "DISTRIBUTOR";
const WORKER_ID_COLUMN = "HCM_ADMIN_CONSOLE_USER_WORKER_ID";
const USERNAME_COLUMN = "UserName";
const USER_NAME_COLUMN = "HCM_ADMIN_CONSOLE_USER_NAME";
const ROLE_COLUMN = "HCM_ADMIN_CONSOLE_USER_ROLE";
const BOUNDARY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_NAME";
const BOUNDARY_CODE_MANDATORY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY";
const BOUNDARY_CODE_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
const USER_DATA_TYPE = "user";
const USER_SERVICE_UUID_COLUMN = "UserService Uuids";
const MAX_ROLE_COLUMNS = 5;
const DISTRIBUTOR_BY_DH_CACHE_PREFIX = "cache:distributor-by-dh";
const DISTRIBUTOR_BY_DH_BASE_CACHE_SEGMENT = "campaign-base";
const DISTRIBUTOR_BY_DH_CACHE_KEY_MAX_PART_LENGTH = 200;
const DISTRIBUTOR_BY_DH_LATENCY_WINDOW_SIZE = 200;

type DistributorRecord = { uuid: string; userName: string; name: string };
type DistributorGroup = { dhCode: string; dhName: string; distributors: DistributorRecord[] };
type RequesterDhCodesByUuid = Record<string, string[]>;
type DistributorByDhCachedPayload = {
    version: 1;
    groups: DistributorGroup[];
    requesterDhCodesByUuid?: RequesterDhCodesByUuid;
    cachedAt: number;
};
type DistributorByDhCampaignDataset = {
    groups: DistributorGroup[];
    requesterDhCodesByUuid: RequesterDhCodesByUuid;
};

let distributorByDhOptimizationDisabled = false;
let distributorByDhShadowMismatchCount = 0;
const distributorByDhLatencyWindow: number[] = [];
const distributorByDhLocalCache = new Map<string, { expiresAt: number; payload: DistributorByDhCachedPayload }>();
const distributorByDhPrewarmInFlight = new Map<string, Promise<void>>();

function normalizedLocalityCodes(localityCodes: unknown): string[] {
    if (!Array.isArray(localityCodes)) return [];
    return localityCodes
        .map((code) => textValue(code))
        .filter(Boolean)
        .sort();
}

function boundedKeyPart(value: string): string {
    if (value.length <= DISTRIBUTOR_BY_DH_CACHE_KEY_MAX_PART_LENGTH) return value;
    return value.slice(0, DISTRIBUTOR_BY_DH_CACHE_KEY_MAX_PART_LENGTH);
}

function distributorByDhCacheKey(tenantId: string, campaignNumber: string, requesterUuid: string, localityCodes: string[]): string {
    const localityPart = localityCodes.length > 0 ? localityCodes.join(",") : "ALL";
    return [
        DISTRIBUTOR_BY_DH_CACHE_PREFIX,
        boundedKeyPart(tenantId || "na"),
        boundedKeyPart(campaignNumber || "na"),
        boundedKeyPart(requesterUuid || "na"),
        boundedKeyPart(localityPart),
    ].join(":");
}

function distributorByDhCampaignBaseCacheKey(tenantId: string, campaignNumber: string): string {
    return [
        DISTRIBUTOR_BY_DH_CACHE_PREFIX,
        DISTRIBUTOR_BY_DH_BASE_CACHE_SEGMENT,
        boundedKeyPart(tenantId || "na"),
        boundedKeyPart(campaignNumber || "na"),
    ].join(":");
}

function distributorByDhFeatureEnabled(): boolean {
    if (distributorByDhOptimizationDisabled) return false;
    return config?.distributorByDh?.cacheEnabled === true;
}

function distributorByDhCacheTtlSeconds(): number {
    const configured = Number(config?.distributorByDh?.cacheTtlSeconds);
    if (!Number.isFinite(configured) || configured <= 0) return 60;
    return Math.max(1, Math.round(configured));
}

function shouldRunDistributorByDhShadowComparison(): boolean {
    if (config?.distributorByDh?.shadowComparisonEnabled !== true) return false;
    const sampleRate = Number(config?.distributorByDh?.shadowComparisonSampleRate);
    if (!Number.isFinite(sampleRate) || sampleRate <= 0) return false;
    return Math.random() < Math.min(sampleRate, 1);
}

function removeDistributorByDhLocalCacheEntries(matcher: (key: string) => boolean): number {
    const keysToDelete = Array.from(distributorByDhLocalCache.keys()).filter(matcher);
    for (const key of keysToDelete) distributorByDhLocalCache.delete(key);
    return keysToDelete.length;
}

function p99Latency(latencies: number[]): number {
    if (latencies.length === 0) return 0;
    const sorted = [...latencies].sort((a, b) => a - b);
    const idx = Math.min(sorted.length - 1, Math.ceil(sorted.length * 0.99) - 1);
    return sorted[idx];
}

function evaluateDistributorByDhAutoRollback(): void {
    if (config?.distributorByDh?.autoRollbackEnabled !== true) return;
    const mismatchThreshold = Number(config?.distributorByDh?.autoRollbackMismatchThreshold);
    const p99ThresholdMs = Number(config?.distributorByDh?.autoRollbackP99Ms);
    const hasMismatchThreshold = Number.isFinite(mismatchThreshold) && mismatchThreshold > 0;
    const hasP99Threshold = Number.isFinite(p99ThresholdMs) && p99ThresholdMs > 0;

    if (hasMismatchThreshold && distributorByDhShadowMismatchCount >= mismatchThreshold) {
        distributorByDhOptimizationDisabled = true;
        distributorByDhLocalCache.clear();
        logger.error(
            `distributor-by-dh optimization auto-rolled back after ${distributorByDhShadowMismatchCount} shadow mismatch(es)`
        );
        return;
    }

    if (hasP99Threshold && distributorByDhLatencyWindow.length >= 20) {
        const currentP99 = p99Latency(distributorByDhLatencyWindow);
        if (currentP99 > p99ThresholdMs) {
            distributorByDhOptimizationDisabled = true;
            distributorByDhLocalCache.clear();
            logger.error(
                `distributor-by-dh optimization auto-rolled back after p99 ${currentP99}ms exceeded threshold ${p99ThresholdMs}ms`
            );
        }
    }
}

function recordDistributorByDhLatency(durationMs: number): void {
    distributorByDhLatencyWindow.push(durationMs);
    if (distributorByDhLatencyWindow.length > DISTRIBUTOR_BY_DH_LATENCY_WINDOW_SIZE) {
        distributorByDhLatencyWindow.shift();
    }
    evaluateDistributorByDhAutoRollback();
}

async function invalidateDistributorByDhCache(tenantId: string, campaignNumber?: string): Promise<void> {
    if (!tenantId) return;
    const responsePrefix = campaignNumber
        ? `${DISTRIBUTOR_BY_DH_CACHE_PREFIX}:${boundedKeyPart(tenantId)}:${boundedKeyPart(campaignNumber)}:`
        : `${DISTRIBUTOR_BY_DH_CACHE_PREFIX}:${boundedKeyPart(tenantId)}:`;
    const basePrefix = campaignNumber
        ? `${DISTRIBUTOR_BY_DH_CACHE_PREFIX}:${DISTRIBUTOR_BY_DH_BASE_CACHE_SEGMENT}:${boundedKeyPart(tenantId)}:${boundedKeyPart(campaignNumber)}`
        : `${DISTRIBUTOR_BY_DH_CACHE_PREFIX}:${DISTRIBUTOR_BY_DH_BASE_CACHE_SEGMENT}:${boundedKeyPart(tenantId)}:`;
    const localDeleted = removeDistributorByDhLocalCacheEntries(
        (key) => key.startsWith(responsePrefix) || key.startsWith(basePrefix)
    );

    let redisDeleted = 0;
    try {
        const isRedisConnected = await checkRedisConnection();
        if (isRedisConnected) {
            for (const prefix of [responsePrefix, basePrefix]) {
                let cursor = "0";
                do {
                    const scanResp = await redis.scan(cursor, "MATCH", `${prefix}*`, "COUNT", 200);
                    cursor = scanResp?.[0] || "0";
                    const keys = Array.isArray(scanResp?.[1]) ? scanResp[1] : [];
                    if (keys.length > 0) redisDeleted += await redis.del(...keys);
                } while (cursor !== "0");
            }
        }
    } catch (error) {
        logger.warn(`distributor-by-dh cache invalidation skipped for redis (${tenantId}/${campaignNumber || "*"})`, error);
    }

    if (localDeleted > 0 || redisDeleted > 0) {
        logger.info(
            `distributor-by-dh cache invalidated for tenant=${tenantId}, campaign=${campaignNumber || "*"} (local=${localDeleted}, redis=${redisDeleted})`
        );
    }
}

function parseDistributorByDhPagination(pagination: any, totalCount: number): { offset: number; limit: number } {
    let offset = 0;
    let limit = totalCount || 100;
    if (!pagination) return { offset, limit };

    offset = pagination.offset || 0;
    limit = pagination.limit || 100;
    if (offset < 0) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "offset cannot be negative");
    }
    if (limit < 1 || limit > 1000) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "limit must be between 1 and 1000");
    }
    return { offset, limit };
}

function toDistributorByDhResponse(sortedGroups: DistributorGroup[], pagination: any): { DHDistributors: DistributorGroup[]; TotalCount: number; Pagination: { offset: number; limit: number } } {
    const { offset, limit } = parseDistributorByDhPagination(pagination, sortedGroups.length);
    return {
        DHDistributors: sortedGroups.slice(offset, offset + limit),
        TotalCount: sortedGroups.length,
        Pagination: { offset, limit }
    };
}

function areDistributorByDhResponsesEqual(left: DistributorGroup[], right: DistributorGroup[]): boolean {
    return JSON.stringify(left) === JSON.stringify(right);
}

async function readDistributorByDhCache(cacheKey: string): Promise<DistributorByDhCachedPayload | null> {
    const now = Date.now();
    const fromLocal = distributorByDhLocalCache.get(cacheKey);
    if (fromLocal && fromLocal.expiresAt > now) return fromLocal.payload;
    if (fromLocal) distributorByDhLocalCache.delete(cacheKey);

    try {
        const isRedisConnected = await checkRedisConnection();
        if (!isRedisConnected) return null;
        const raw = await redis.get(cacheKey);
        if (!raw) return null;
        const parsed = JSON.parse(raw) as DistributorByDhCachedPayload;
        if (!parsed || parsed.version !== 1 || !Array.isArray(parsed.groups)) return null;
        const ttlSeconds = distributorByDhCacheTtlSeconds();
        distributorByDhLocalCache.set(cacheKey, { payload: parsed, expiresAt: now + ttlSeconds * 1000 });
        return parsed;
    } catch (error) {
        logger.warn(`distributor-by-dh cache read failed for key=${cacheKey}`, error);
        return null;
    }
}

async function writeDistributorByDhCache(cacheKey: string, payload: DistributorByDhCachedPayload): Promise<void> {
    const ttlSeconds = distributorByDhCacheTtlSeconds();
    const expiresAt = Date.now() + ttlSeconds * 1000;
    distributorByDhLocalCache.set(cacheKey, { payload, expiresAt });
    try {
        const isRedisConnected = await checkRedisConnection();
        if (!isRedisConnected) return;
        await redis.set(cacheKey, JSON.stringify(payload), "EX", ttlSeconds);
    } catch (error) {
        logger.warn(`distributor-by-dh cache write failed for key=${cacheKey}`, error);
    }
}

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
            !forceUpdate
            && isReusableCompletedResource(latestResource);

        if (hasFreshInProgressResource || hasReusableCompletedResource) {
            responseData = [latestResource];
            logger.info(
                `Reusing generation id=${latestResource.id} status=${latestResource.status} `
                + `for localityCode=${requestLocalityKey}.`
            );
        } else {
            if (latestResource?.status === generatedResourceStatuses.inprogress) {
                logger.warn(`Found in-progress generation id=${latestResource.id}; creating a fresh one (stale in-progress resource).`);
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

    const resourceType = textValue(request?.body?.ResourceDetails?.type).toLowerCase();
    const tenantId = textValue(request?.body?.ResourceDetails?.tenantId);
    const campaignNumber = textValue(request?.body?.ResourceDetails?.campaignNumber)
        || textValue(request?.body?.ResourceDetails?.additionalDetails?.campaignNumber);
    if (tenantId && (resourceType === USER_DATA_TYPE || resourceType === "campaign")) {
        await invalidateDistributorByDhCache(tenantId, campaignNumber || undefined);
        if (campaignNumber) {
            void prewarmDistributorByDhCampaignBaseCache(request, tenantId, campaignNumber);
        }
    }
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

function textValue(value: unknown): string {
    return typeof value === "string" ? value.trim() : "";
}

function extractRoleCodes(row: Record<string, unknown>): Set<string> {
    const roles = new Set<string>();
    const roleString = textValue(row[ROLE_COLUMN]);
    if (roleString) {
        for (const role of roleString.split(",")) {
            const normalized = textValue(role).toUpperCase();
            if (normalized) roles.add(normalized);
        }
    }
    for (let i = 1; i <= MAX_ROLE_COLUMNS; i++) {
        const normalized = textValue(row[`HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_${i}`]).toUpperCase();
        if (normalized) roles.add(normalized);
    }
    return roles;
}

interface UserUuidLookup {
    byWorkerId: Map<string, string>;
    byUserName: Map<string, string>;
}

function decryptUserName(encrypted: string, decrypt: (value: string) => string): string {
    try {
        return textValue(decrypt(encrypted));
    } catch {
        return "";
    }
}

/** Attendance sheets may carry the individual/user id instead of the upload worker id, so userName is a second key. */
function buildUserUuidLookup(
    userRows: unknown,
    decrypt: (value: string) => string,
    includeUserNameLookup: boolean,
    userNamesToResolve?: Set<string>
): UserUuidLookup {
    const lookup: UserUuidLookup = { byWorkerId: new Map<string, string>(), byUserName: new Map<string, string>() };
    if (!Array.isArray(userRows)) return lookup;
    for (const userRow of userRows) {
        if (userRow?.isDeleted) continue;
        const uuid = textValue(userRow?.uniqueIdAfterProcess) || textValue(userRow?.data?.[USER_SERVICE_UUID_COLUMN]);
        if (!uuid) continue;
        const workerId = textValue(userRow?.data?.[WORKER_ID_COLUMN]);
        if (workerId) lookup.byWorkerId.set(workerId, uuid);
        if (!includeUserNameLookup) continue;
        const userName = decryptUserName(textValue(userRow?.data?.[USERNAME_COLUMN]), decrypt);
        if (!userName) continue;
        if (userNamesToResolve && !userNamesToResolve.has(userName)) continue;
        if (!lookup.byUserName.has(userName)) lookup.byUserName.set(userName, uuid);
    }
    return lookup;
}

interface DhDistributorGroup {
    dhCode: string;
    dhName: string;
    distributors: Array<{ uuid: string; userName: string; name: string }>;
    dedupe: Set<string>;
}

/** Adds only distributors missing from the list, so registers or mappings that do not exist yet cannot hide them; existing entries are never changed. */
function addMissingDistributorsFromUserRows(
    distributorsByDh: Map<string, DhDistributorGroup>,
    userRows: unknown,
    requestedDhCodes: Set<unknown>,
    decrypt: (value: string) => string
): void {
    if (!Array.isArray(userRows)) return;
    const listedUuids = new Set<string>();
    const listedUserNames = new Set<string>();
    for (const group of distributorsByDh.values()) {
        for (const distributor of group.distributors) {
            if (distributor.uuid) listedUuids.add(distributor.uuid);
            if (distributor.userName) listedUserNames.add(distributor.userName);
        }
    }
    for (const userRow of userRows) {
        if (userRow?.isDeleted) continue;
        const row = (userRow?.data || {}) as Record<string, unknown>;
        if (!extractRoleCodes(row).has(DISTRIBUTOR_ROLE_CODE)) continue;

        const dhCode = textValue(row[BOUNDARY_CODE_MANDATORY_COLUMN]) || textValue(row[BOUNDARY_CODE_COLUMN]);
        if (!dhCode) continue;
        if (requestedDhCodes.size > 0 && !requestedDhCodes.has(dhCode)) continue;

        const userName = decryptUserName(textValue(row[USERNAME_COLUMN]), decrypt);
        const name = textValue(row[USER_NAME_COLUMN]);
        const uuid = textValue(userRow?.uniqueIdAfterProcess) || textValue(row[USER_SERVICE_UUID_COLUMN]);
        if (uuid ? listedUuids.has(uuid) : userName !== "" && listedUserNames.has(userName)) continue;
        const dedupeKey = `${uuid || textValue(row[WORKER_ID_COLUMN])}::${userName}::${name}`;

        let group = distributorsByDh.get(dhCode);
        if (!group) {
            group = { dhCode, dhName: textValue(row[BOUNDARY_COLUMN]) || dhCode, distributors: [], dedupe: new Set<string>() };
            distributorsByDh.set(dhCode, group);
        }
        if (group.dedupe.has(dedupeKey)) continue;
        group.dedupe.add(dedupeKey);
        group.distributors.push({ uuid, userName, name });
        if (uuid) listedUuids.add(uuid);
        if (userName) listedUserNames.add(userName);
    }
}

/** DH codes of the requester's own campaign user rows, so a DH user only sees the distributors under their own DH. */
function findRequesterDhCodes(userRows: unknown, requesterUuid: string): Set<string> {
    const dhCodes = new Set<string>();
    if (!requesterUuid || !Array.isArray(userRows)) return dhCodes;
    for (const userRow of userRows) {
        if (userRow?.isDeleted) continue;
        const row = (userRow?.data || {}) as Record<string, unknown>;
        const isRequester = textValue(userRow?.uniqueIdAfterProcess) === requesterUuid
            || textValue(row[USER_SERVICE_UUID_COLUMN]) === requesterUuid;
        if (!isRequester) continue;
        const dhCode = textValue(row[BOUNDARY_CODE_MANDATORY_COLUMN]) || textValue(row[BOUNDARY_CODE_COLUMN]);
        if (dhCode) dhCodes.add(dhCode);
    }
    return dhCodes;
}

function buildRequesterDhCodesByUuid(userRows: unknown): RequesterDhCodesByUuid {
    const map: Record<string, Set<string>> = {};
    if (!Array.isArray(userRows)) return {};
    for (const userRow of userRows) {
        if (userRow?.isDeleted) continue;
        const row = (userRow?.data || {}) as Record<string, unknown>;
        const dhCode = textValue(row[BOUNDARY_CODE_MANDATORY_COLUMN]) || textValue(row[BOUNDARY_CODE_COLUMN]);
        if (!dhCode) continue;

        const userUuids = [
            textValue(userRow?.uniqueIdAfterProcess),
            textValue(row[USER_SERVICE_UUID_COLUMN]),
        ].filter(Boolean);
        for (const uuid of userUuids) {
            if (!map[uuid]) map[uuid] = new Set<string>();
            map[uuid].add(dhCode);
        }
    }

    const normalized: RequesterDhCodesByUuid = {};
    for (const [uuid, dhCodes] of Object.entries(map)) {
        normalized[uuid] = Array.from(dhCodes).sort();
    }
    return normalized;
}

function dhFilterForRequester(
    requesterDhCodes: Set<string>,
    requestedLocalityCodes: Set<string>
): Set<string> {
    return requestedLocalityCodes.size > 0
        ? new Set(Array.from(requesterDhCodes).filter((dhCode) => requestedLocalityCodes.has(dhCode)))
        : requesterDhCodes;
}

function filterGroupsByDh(groups: DistributorGroup[], dhFilter: Set<string>): DistributorGroup[] {
    return groups.filter((group) => dhFilter.has(group.dhCode));
}

async function buildDistributorsByDhCampaignDataset(
    request: any,
    tenantId: string,
    requestedCampaignNumber: string,
    searchCriteria: any,
    preloadedUserRows?: unknown
): Promise<DistributorByDhCampaignDataset> {
    // Lazy: a top-level import closes the campaignUtils -> dataManageService -> campaignManageService cycle.
    const { searchProjectTypeCampaignService } = await import("./campaignManageService");
    const { TemplateClass: AttendanceRegisterUserBulkMappingTemplateClass } =
        await import("../generateFlowClasses/attendanceRegisterUserBulkMapping-generateClass");
    // Lazy: cryptUtils derives its key from config at import time, which must not run for every importer of this service.
    const { decrypt } = await import("../utils/cryptUtils");

    const campaignResponse = await searchProjectTypeCampaignService({ tenantId, campaignNumber: requestedCampaignNumber }, request);
    const matchedCampaigns: any[] = Array.isArray(campaignResponse?.CampaignDetails) ? campaignResponse.CampaignDetails : [];
    const campaignDetail = matchedCampaigns.find((campaign) => !campaign?.parentId) ?? matchedCampaigns[0];
    if (!campaignDetail) {
        throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND", `Campaign not found for campaignNumber ${requestedCampaignNumber}`);
    }
    const campaignId = textValue(campaignDetail?.id);

    const hierarchyType = textValue(searchCriteria.hierarchyType) || textValue(campaignDetail?.hierarchyType);
    if (!hierarchyType) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "hierarchyType is required in SearchCriteria or campaign");
    }

    const userRows = Array.isArray(preloadedUserRows)
        ? preloadedUserRows
        : await getRelatedDataWithCampaign(USER_DATA_TYPE, requestedCampaignNumber, tenantId, dataRowStatuses.completed);
    const requesterDhCodesByUuid = buildRequesterDhCodesByUuid(userRows);
    const hasAnyDhVisibility = Object.keys(requesterDhCodesByUuid).length > 0;

    const sheetMap = !hasAnyDhVisibility
        ? undefined
        : await AttendanceRegisterUserBulkMappingTemplateClass.generate(
            generationtTemplateConfigs.attendanceRegisterUserBulkMapping,
            {
                tenantId,
                campaignId,
                hierarchyType,
                requestInfo: request?.body?.RequestInfo
            },
            {}
        );

    const workerRows = Array.isArray(sheetMap?.[attendanceSheetNames.WORKER]?.data)
        ? sheetMap[attendanceSheetNames.WORKER].data
        : [];

    const userUuidLookup = buildUserUuidLookup(userRows, decrypt, false);
    const unresolvedUserNamesFromWorkerRows = new Set<string>();

    const distributorsByDh = new Map<string, {
        dhCode: string;
        dhName: string;
        distributors: DistributorRecord[];
        dedupe: Set<string>;
    }>();

    for (const rawRow of workerRows) {
        const row = (rawRow || {}) as Record<string, unknown>;
        const roleCodes = extractRoleCodes(row);
        if (!roleCodes.has(DISTRIBUTOR_ROLE_CODE)) continue;

        const dhCode = textValue(row[BOUNDARY_CODE_MANDATORY_COLUMN]) || textValue(row[BOUNDARY_CODE_COLUMN]);
        if (!dhCode) continue;

        const workerId = textValue(row[WORKER_ID_COLUMN]);
        const userName = textValue(row[USERNAME_COLUMN]);
        const name = textValue(row[USER_NAME_COLUMN]);
        const uuid = userUuidLookup.byWorkerId.get(workerId) ?? "";
        if (!uuid && userName) unresolvedUserNamesFromWorkerRows.add(userName);
        const dedupeKey = `${uuid || workerId}::${userName}::${name}`;

        let group = distributorsByDh.get(dhCode);
        if (!group) {
            group = { dhCode, dhName: textValue(row[BOUNDARY_COLUMN]) || dhCode, distributors: [], dedupe: new Set<string>() };
            distributorsByDh.set(dhCode, group);
        }
        if (group.dedupe.has(dedupeKey)) continue;
        group.dedupe.add(dedupeKey);
        group.distributors.push({ uuid, userName, name });
    }

    if (unresolvedUserNamesFromWorkerRows.size > 0) {
        const userNameLookup = buildUserUuidLookup(userRows, decrypt, true, unresolvedUserNamesFromWorkerRows).byUserName;
        for (const group of distributorsByDh.values()) {
            for (const distributor of group.distributors) {
                if (!distributor.uuid && distributor.userName) {
                    distributor.uuid = userNameLookup.get(distributor.userName) ?? "";
                }
            }
        }
    }

    addMissingDistributorsFromUserRows(distributorsByDh, userRows, new Set<string>(), decrypt);

    const unresolvedUserNames = Array.from(distributorsByDh.values())
        .flatMap((group) => group.distributors)
        .filter((distributor) => !distributor.uuid && distributor.userName)
        .map((distributor) => distributor.userName);
    if (unresolvedUserNames.length > 0) {
        // Lazy: userBatchHandler pulls in the user-creation stack, needed only when the campaign data missed a uuid.
        const { fetchUserUuidsByUserName } = await import("../utils/userBatchHandler");
        const uuidsByUserName = await fetchUserUuidsByUserName(unresolvedUserNames, tenantId, request?.body?.RequestInfo);
        for (const group of distributorsByDh.values()) {
            for (const distributor of group.distributors) {
                if (!distributor.uuid) distributor.uuid = uuidsByUserName.get(distributor.userName) ?? "";
            }
        }
    }

    return {
        groups: Array.from(distributorsByDh.values())
        .sort((a, b) => a.dhCode.localeCompare(b.dhCode))
        .map((entry) => ({ dhCode: entry.dhCode, dhName: entry.dhName, distributors: entry.distributors })),
        requesterDhCodesByUuid,
    };
}

async function prewarmDistributorByDhCampaignBaseCache(
    request: any,
    tenantId: string,
    campaignNumber: string
): Promise<void> {
    if (!distributorByDhFeatureEnabled()) return;
    if (!tenantId || !campaignNumber) return;

    const baseCacheKey = distributorByDhCampaignBaseCacheKey(tenantId, campaignNumber);
    if (distributorByDhPrewarmInFlight.has(baseCacheKey)) return distributorByDhPrewarmInFlight.get(baseCacheKey)!;

    const work = (async () => {
        try {
            const existing = await readDistributorByDhCache(baseCacheKey);
            if (existing) return;
            const dataset = await buildDistributorsByDhCampaignDataset(
                request,
                tenantId,
                campaignNumber,
                request?.body?.SearchCriteria || {}
            );
            await writeDistributorByDhCache(baseCacheKey, {
                version: 1,
                groups: dataset.groups,
                requesterDhCodesByUuid: dataset.requesterDhCodesByUuid,
                cachedAt: Date.now(),
            });
            logger.info(`distributor-by-dh base cache prewarmed for tenant=${tenantId}, campaign=${campaignNumber}`);
        } catch (error) {
            logger.warn(`distributor-by-dh base cache prewarm failed for tenant=${tenantId}, campaign=${campaignNumber}`, error);
        } finally {
            distributorByDhPrewarmInFlight.delete(baseCacheKey);
        }
    })();

    distributorByDhPrewarmInFlight.set(baseCacheKey, work);
    return work;
}

const searchDistributorsByDhService = async (request: any) => {
    const searchCriteria = request?.body?.SearchCriteria;
    const pagination = request?.body?.Pagination;

    if (!searchCriteria) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "SearchCriteria is required");
    }

    const tenantId = textValue(searchCriteria.tenantId);
    if (!tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required in SearchCriteria");
    }

    const requestedCampaignNumber = textValue(searchCriteria.campaignNumber);
    if (!requestedCampaignNumber) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "campaignNumber is required in SearchCriteria");
    }

    if (searchCriteria.localityCodes && !Array.isArray(searchCriteria.localityCodes)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "localityCodes must be an array");
    }

    const requesterUuid = textValue(request?.body?.RequestInfo?.userInfo?.uuid);
    if (!requesterUuid) {
        return toDistributorByDhResponse([], pagination);
    }

    const localityCodes = normalizedLocalityCodes(searchCriteria.localityCodes);
    const requestedDhCodes = new Set(localityCodes);
    const cacheKey = distributorByDhCacheKey(tenantId, requestedCampaignNumber, requesterUuid, localityCodes);
    const baseCacheKey = distributorByDhCampaignBaseCacheKey(tenantId, requestedCampaignNumber);

    const startedAt = Date.now();
    const useOptimizedPath = distributorByDhFeatureEnabled();
    if (!useOptimizedPath) {
        const userRows = await getRelatedDataWithCampaign(USER_DATA_TYPE, requestedCampaignNumber, tenantId, dataRowStatuses.completed);
        const requesterDhCodes = findRequesterDhCodes(userRows, requesterUuid);
        const dhFilter = dhFilterForRequester(requesterDhCodes, requestedDhCodes);
        const matchesNothing = dhFilter.size === 0;
        logger.info(`distributor-by-dh DH filter: ${matchesNothing ? "none (requester has no DH in the campaign user data or localityCodes are outside it)" : Array.from(dhFilter).join(",")}`);
        if (matchesNothing) return toDistributorByDhResponse([], pagination);

        const dataset = await buildDistributorsByDhCampaignDataset(
            request,
            tenantId,
            requestedCampaignNumber,
            searchCriteria,
            userRows
        );
        const filteredGroups = filterGroupsByDh(dataset.groups, dhFilter);
        return toDistributorByDhResponse(filteredGroups, pagination);
    }

    let cachedPayload = await readDistributorByDhCache(cacheKey);
    let sortedGroups: DistributorGroup[];
    let requesterDhCodes = new Set<string>();
    let basePayload = await readDistributorByDhCache(baseCacheKey);

    if (cachedPayload) {
        logger.info(`distributor-by-dh cache hit for key=${cacheKey}`);
        sortedGroups = cachedPayload.groups;
    } else {
        if (!basePayload) {
            const userRows = await getRelatedDataWithCampaign(USER_DATA_TYPE, requestedCampaignNumber, tenantId, dataRowStatuses.completed);
            requesterDhCodes = findRequesterDhCodes(userRows, requesterUuid);
            const dhFilter = dhFilterForRequester(requesterDhCodes, requestedDhCodes);
            const matchesNothing = dhFilter.size === 0;
            logger.info(`distributor-by-dh DH filter: ${matchesNothing ? "none (requester has no DH in the campaign user data or localityCodes are outside it)" : Array.from(dhFilter).join(",")}`);
            if (matchesNothing) return toDistributorByDhResponse([], pagination);

            const dataset = await buildDistributorsByDhCampaignDataset(
                request,
                tenantId,
                requestedCampaignNumber,
                searchCriteria,
                userRows
            );
            basePayload = {
                version: 1,
                groups: dataset.groups,
                requesterDhCodesByUuid: dataset.requesterDhCodesByUuid,
                cachedAt: Date.now(),
            };
            await writeDistributorByDhCache(baseCacheKey, basePayload);
            sortedGroups = filterGroupsByDh(dataset.groups, dhFilter);
        } else {
            requesterDhCodes = new Set(basePayload.requesterDhCodesByUuid?.[requesterUuid] || []);
            const dhFilter = dhFilterForRequester(requesterDhCodes, requestedDhCodes);
            const matchesNothing = dhFilter.size === 0;
            logger.info(`distributor-by-dh DH filter: ${matchesNothing ? "none (requester has no DH in the campaign user data or localityCodes are outside it)" : Array.from(dhFilter).join(",")}`);
            if (matchesNothing) return toDistributorByDhResponse([], pagination);
            sortedGroups = filterGroupsByDh(basePayload.groups, dhFilter);
        }
        cachedPayload = {
            version: 1,
            groups: sortedGroups,
            cachedAt: Date.now(),
        };
        await writeDistributorByDhCache(cacheKey, cachedPayload);
    }

    const response = toDistributorByDhResponse(sortedGroups, pagination);
    recordDistributorByDhLatency(Date.now() - startedAt);

    if (shouldRunDistributorByDhShadowComparison()) {
        const freshDataset = await buildDistributorsByDhCampaignDataset(request, tenantId, requestedCampaignNumber, searchCriteria);
        const freshRequesterDhCodes = new Set(freshDataset.requesterDhCodesByUuid[requesterUuid] || []);
        const freshDhFilter = dhFilterForRequester(freshRequesterDhCodes, requestedDhCodes);
        const freshGroups = freshDhFilter.size === 0 ? [] : filterGroupsByDh(freshDataset.groups, freshDhFilter);
        if (!areDistributorByDhResponsesEqual(sortedGroups, freshGroups)) {
            distributorByDhShadowMismatchCount += 1;
            logger.error(
                `distributor-by-dh shadow mismatch for key=${cacheKey}; mismatchCount=${distributorByDhShadowMismatchCount}`
            );
            evaluateDistributorByDhAutoRollback();
        }
    }

    return response;
}

/**
 * Lists the users of a campaign that currently have an ACTIVE egov-user session.
 * Campaign users come from the processed "user" rows; egov-user is then asked about them in
 * bounded chunks. Rows without a resolvable uuid are skipped (no userName fallback).
 */
const searchActiveUsersByCampaignService = async (request: any) => {
    const searchCriteria = request?.body?.SearchCriteria;
    if (!searchCriteria) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "SearchCriteria is required");
    }

    const tenantId = textValue(searchCriteria.tenantId);
    if (!tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required in SearchCriteria");
    }

    const campaignNumber = textValue(searchCriteria.campaignNumber);
    if (!campaignNumber) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "campaignNumber is required in SearchCriteria");
    }

    const userRows = await getRelatedDataWithCampaign(USER_DATA_TYPE, campaignNumber, tenantId, dataRowStatuses.completed);
    const userUuids = new Set<string>();
    let skippedRows = 0;
    for (const userRow of Array.isArray(userRows) ? userRows : []) {
        if (userRow?.isDeleted) continue;
        const uuid = textValue(userRow?.uniqueIdAfterProcess) || textValue(userRow?.data?.[USER_SERVICE_UUID_COLUMN]);
        if (!uuid) {
            skippedRows++;
            continue;
        }
        userUuids.add(uuid);
    }
    if (skippedRows > 0) {
        logger.warn(`${skippedRows} user row(s) skipped, uuid missing for campaign ${campaignNumber}`);
    }
    if (userUuids.size === 0) {
        return { sessions: [], count: 0 };
    }

    // Lazy: the http client pulls in redis/config at import time, needed only on this path.
    const { httpRequest } = await import("../utils/request");
    const allUuids = Array.from(userUuids);
    const chunkSize = Math.min(1000, Math.max(1, config.user?.sessionSearchBatchSize || 500));
    const sessions: any[] = [];
    for (let i = 0; i < allUuids.length; i += chunkSize) {
        const response = await httpRequest(
            config.host.userHost + config.paths.userSessionSearch,
            { RequestInfo: request?.body?.RequestInfo, tenantId, userUuids: allUuids.slice(i, i + chunkSize) },
            { tenantId }
        );
        if (Array.isArray(response?.sessions)) sessions.push(...response.sessions);
    }

    sessions.sort((a, b) => (b?.lastServerContact ?? 0) - (a?.lastServerContact ?? 0));
    return { sessions, count: sessions.length };
}

export {
    generateDataService,
    downloadDataService,
    getBoundaryDataService,
    createDataService,
    searchDataService,
    searchCampaignDataService,
    searchMappingDataService,
    searchDistributorsByDhService,
    searchActiveUsersByCampaignService
}
