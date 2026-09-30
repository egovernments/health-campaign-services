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
import { attendanceSheetNames, generatedResourceStatuses } from "../config/constants";
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
const ALWAYS_FRESH_DOWNLOAD_TYPES = new Set<string>([
    "attendanceRegisterUserBulkMapping"
]);

const DISTRIBUTOR_ROLE_CODE = "DISTRIBUTOR";
const WORKER_ID_COLUMN = "HCM_ADMIN_CONSOLE_USER_WORKER_ID";
const USERNAME_COLUMN = "UserName";
const USER_NAME_COLUMN = "HCM_ADMIN_CONSOLE_USER_NAME";
const ROLE_COLUMN = "HCM_ADMIN_CONSOLE_USER_ROLE";
const BOUNDARY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_NAME";
const BOUNDARY_CODE_MANDATORY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY";
const BOUNDARY_CODE_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
const MAX_ROLE_COLUMNS = 5;

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
    const shouldBypassReuse = ALWAYS_FRESH_DOWNLOAD_TYPES.has(type);

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
            !shouldBypassReuse
            && latestResource?.status === generatedResourceStatuses.inprogress
            && !isStaleInProgressResource(latestResource);
        const hasReusableCompletedResource =
            !shouldBypassReuse
            && !forceUpdate
            && isReusableCompletedResource(latestResource);

        if (hasFreshInProgressResource || hasReusableCompletedResource) {
            responseData = [latestResource];
            logger.info(
                `Reusing generation id=${latestResource.id} status=${latestResource.status} `
                + `for localityCode=${requestLocalityKey}.`
            );
        } else {
            if (latestResource?.status === generatedResourceStatuses.inprogress) {
                const reason = shouldBypassReuse
                    ? "reuse disabled for this type"
                    : "stale in-progress resource";
                logger.warn(`Found in-progress generation id=${latestResource.id}; creating a fresh one (${reason}).`);
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

    const campaignId = textValue(searchCriteria.campaignId);
    if (!campaignId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "campaignId is required in SearchCriteria");
    }

    if (searchCriteria.localityCodes && !Array.isArray(searchCriteria.localityCodes)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "localityCodes must be an array");
    }

    // Lazy: a top-level import closes the campaignUtils -> dataManageService -> campaignManageService cycle.
    const { searchProjectTypeCampaignService } = await import("./campaignManageService");
    const { TemplateClass: AttendanceRegisterUserBulkMappingTemplateClass } =
        await import("../generateFlowClasses/attendanceRegisterUserBulkMapping-generateClass");

    const campaignResponse = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] }, request);
    const campaignDetail = campaignResponse?.CampaignDetails?.[0];
    if (!campaignDetail) {
        throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND", `Campaign not found for campaignId ${campaignId}`);
    }

    const hierarchyType = textValue(searchCriteria.hierarchyType) || textValue(campaignDetail?.hierarchyType);
    if (!hierarchyType) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "hierarchyType is required in SearchCriteria or campaign");
    }

    const rawLocalityCodes = Array.isArray(searchCriteria.localityCodes) ? searchCriteria.localityCodes : [];
    const requestedDhCodes = new Set(
        rawLocalityCodes
            .map((code: unknown) => textValue(code))
            .filter(Boolean)
    );

    const sheetMap = await AttendanceRegisterUserBulkMappingTemplateClass.generate(
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

    const distributorsByDh = new Map<string, {
        dhCode: string;
        dhName: string;
        distributors: Array<{ workerId: string; userName: string; name: string; roles: string[] }>;
        dedupe: Set<string>;
    }>();

    for (const rawRow of workerRows) {
        const row = (rawRow || {}) as Record<string, unknown>;
        const roleCodes = extractRoleCodes(row);
        if (!roleCodes.has(DISTRIBUTOR_ROLE_CODE)) continue;

        const dhCode = textValue(row[BOUNDARY_CODE_MANDATORY_COLUMN]) || textValue(row[BOUNDARY_CODE_COLUMN]);
        if (!dhCode) continue;
        if (requestedDhCodes.size > 0 && !requestedDhCodes.has(dhCode)) continue;

        const workerId = textValue(row[WORKER_ID_COLUMN]);
        const userName = textValue(row[USERNAME_COLUMN]);
        const name = textValue(row[USER_NAME_COLUMN]);
        const dedupeKey = `${workerId}::${userName}::${name}`;

        let group = distributorsByDh.get(dhCode);
        if (!group) {
            group = {
                dhCode,
                dhName: textValue(row[BOUNDARY_COLUMN]) || dhCode,
                distributors: [],
                dedupe: new Set<string>()
            };
            distributorsByDh.set(dhCode, group);
        }

        if (group.dedupe.has(dedupeKey)) continue;
        group.dedupe.add(dedupeKey);
        group.distributors.push({
            workerId,
            userName,
            name,
            roles: Array.from(roleCodes)
        });
    }

    const sortedGroups = Array.from(distributorsByDh.values())
        .sort((a, b) => a.dhCode.localeCompare(b.dhCode))
        .map((entry) => ({
            dhCode: entry.dhCode,
            dhName: entry.dhName,
            distributors: entry.distributors
        }));

    let offset = 0;
    let limit = sortedGroups.length || 100;
    if (pagination) {
        offset = pagination.offset || 0;
        limit = pagination.limit || 100;
        if (offset < 0) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "offset cannot be negative");
        }
        if (limit < 1 || limit > 1000) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "limit must be between 1 and 1000");
        }
    }

    const pagedGroups = sortedGroups.slice(offset, offset + limit);

    return {
        DHDistributors: pagedGroups,
        TotalCount: sortedGroups.length,
        Pagination: {
            offset,
            limit
        }
    };
}

export {
    generateDataService,
    downloadDataService,
    getBoundaryDataService,
    createDataService,
    searchDataService,
    searchCampaignDataService,
    searchMappingDataService,
    searchDistributorsByDhService
}
