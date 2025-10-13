import express from "express";
import { processGenericRequest } from "../api/campaignApis";
import { createAndUploadFile, getBoundarySheetData } from "../api/genericApis";
import { getLocalizedName, getResourceDetails, processDataSearchRequest } from "../utils/campaignUtils";
import { addDataToSheet, enrichResourceDetails, getLocalizedMessagesHandler, searchGeneratedResources, processGenerate, throwError, searchCampaignData, searchMappingData } from "../utils/genericUtils";
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { validateCreateRequest, validateDownloadRequest, validateSearchRequest } from "../validators/campaignValidators";
import { validateGenerateRequest } from "../validators/genericValidator";
import { getLocaleFromRequestInfo, getLocalisationModuleName } from "../utils/localisationUtils";
import { getBoundaryTabName } from "../utils/boundaryUtils";
import { getNewExcelWorkbook } from "../utils/excelUtils";
import { redis, checkRedisConnection } from "../utils/redisUtils"; // Importing checkRedisConnection function
import config from '../config/index'
import { buildGenerateRequest, callGenerate, triggerGenerate } from "../utils/generateUtils";
import { generatedResourceStatuses } from "../config/constants";
import { isCampaignIdOfMicroplan } from "../utils/campaignUtils";


const generateDataService = async (request: express.Request) => {
    // Validate the generate request
    await validateGenerateRequest(request);
    logger.info("VALIDATED THE DATA GENERATE REQUEST");
    await processGenerate(request);
    // Send response with generated resource details
    return request?.body?.generatedResource;
};


const downloadDataService = async (request: express.Request) => {
    await validateDownloadRequest(request);
    logger.info("VALIDATED THE DATA DOWNLOAD REQUEST");

    var type = String(request.query.type);
    // Get response data from the database
    const responseData = await searchGeneratedResources(request?.query, getLocaleFromRequestInfo(request?.body?.RequestInfo));
    const resourceDetails = await getResourceDetails(request);



    // Check if response data is available
    if (
        !responseData ||
        (responseData.length === 0 && !request?.query?.id) ||
        responseData?.[0]?.status === generatedResourceStatuses.failed
    ) {
        logger.error(`No data of type '${type}' with status 'Completed' or the provided ID is present in the database.`)
        // Throw error if data is not found
        // const newRequestToGenerate = buildGenerateRequest(request);

        // Added auto generate since no previous generate request found
        const locale = getLocaleFromRequestInfo(request?.body?.RequestInfo);
        logger.info(`Triggering auto generate since no resources got generated for the given Campaign Id ${request?.query?.campaignId} & type ${request?.query?.type}`)
        // callGenerate(newRequestToGenerate, request?.query?.type);
        const tenantId = String(request?.query?.tenantId);
        const hierarchyType = String(request?.query?.hierarchyType);
        const campaignId = String(request?.query?.campaignId);

        // Use DB-level microplan check
        let isMicroplan = false;
        try {
            isMicroplan = await isCampaignIdOfMicroplan(tenantId, campaignId);
        } catch (e) {
            throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", "Error checking if campaign id is of microplan");
        }

        if(type !== 'boundaryManagement'){
            if (isMicroplan) {
                const newRequestToGenerate = {
                    ...request,
                    query: {
                        ...request.query,
                        type,
                        tenantId,
                        hierarchyType,
                        campaignId,
                        forceUpdate: 'true'
                    }
                };
                await callGenerate(newRequestToGenerate, type);
            } else {
                triggerGenerate(type, tenantId, hierarchyType, campaignId, request?.body?.RequestInfo?.userInfo?.uuid || "null", locale);
            }
        }
        else{
            const newRequestToGenerate = buildGenerateRequest(request);
            callGenerate(newRequestToGenerate, request?.query?.type);
        }
    }


        // Send response with resource details
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
        const cacheTTL = config?.cacheTime; // TTL in seconds (5 minutes)
        const cacheKey = `${campaignId}-${hierarchyType}`;
        let isRedisConnected = false;
        let cachedData: any = null;
        if (cacheKey && enableCaching) {
            isRedisConnected = await checkRedisConnection();
            cachedData = await redis.get(cacheKey); // Get cached data
        }
        if (cachedData) {
            logger.info("CACHE HIT :: " + cacheKey);
            logger.debug(`CACHED DATA :: ${getFormattedStringForDebug(cachedData)}`);

            // Reset the TTL for the cache key
            if (config.cacheValues.resetCache) {
                await redis.expire(cacheKey, cacheTTL);
            }

            return JSON.parse(cachedData); // Return parsed cached data if available
        } else {
            logger.info("NO CACHE FOUND :: REQUEST :: " + cacheKey);
        }
        const workbook = getNewExcelWorkbook();
        const localizationMapHierarchy = hierarchyType && await getLocalizedMessagesHandler(request, request?.query?.tenantId, getLocalisationModuleName(hierarchyType), true);
        const localizationMapModule = await getLocalizedMessagesHandler(request, request?.query?.tenantId);
        const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };
        // Retrieve boundary sheet data
        const boundarySheetData: any = await getBoundarySheetData(request, localizationMap,enableCaching === true);
        const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap);
        const boundarySheet = workbook.addWorksheet(localizedBoundaryTab);
        addDataToSheet(request, boundarySheet, boundarySheetData, '93C47D', 40, true);
        const boundaryFileDetails: any = await createAndUploadFile(workbook, request);
        // Return boundary file details
        logger.info("RETURNS THE BOUNDARY RESPONSE");
        if (cacheKey && isRedisConnected) {
            await redis.set(cacheKey, JSON.stringify(boundaryFileDetails), "EX", cacheTTL); // Cache the response data with TTL
        }
        return boundaryFileDetails;
    } catch (e: any) {
        console.log(e)
        logger.error(String(e))
        // Handle errors and send error response
        throw (e);
    }
};


const createDataService = async (request: any) => {

    const hierarchyType = request?.body?.ResourceDetails?.hierarchyType;
    const localizationMapHierarchy = hierarchyType && await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId, getLocalisationModuleName(hierarchyType), true);
    const localizationMapModule = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId);
    const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };
    // Validate the create request
    logger.info("Validating data create request")
    await validateCreateRequest(request, localizationMap);
    logger.info("VALIDATED THE DATA CREATE REQUEST");

    // Enrich resource details
    await enrichResourceDetails(request);

    // Process the generic request
    await processGenericRequest(request, localizationMap);
    return request?.body?.ResourceDetails;
}

const searchDataService = async (request: any) => {
    // Validate the search request
    await validateSearchRequest(request);
    logger.info("VALIDATED THE DATA GENERATE REQUEST");
    // Process the data search request
    await processDataSearchRequest(request);
    // Send response with resource details
    return request?.body?.ResourceDetails;
}

/**
 * Search campaign data service with proper validation
 */
const searchCampaignDataService = async (request: any) => {
    try {
        const searchCriteria = request?.body?.SearchCriteria;
        const pagination = request?.body?.Pagination;
        
        // Validate required fields
        if (!searchCriteria) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "SearchCriteria is required");
        }
        
        if (!searchCriteria.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required in SearchCriteria");
        }
        
        // Validate uniqueIdentifiers is array if provided
        if (searchCriteria.uniqueIdentifiers && !Array.isArray(searchCriteria.uniqueIdentifiers)) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "uniqueIdentifiers must be an array");
        }
        
        // Build search parameters
        const searchParams: any = {
            tenantId: searchCriteria.tenantId,
            type: searchCriteria.type,
            campaignNumber: searchCriteria.campaignNumber,
            status: searchCriteria.status,
            uniqueIdentifiers: searchCriteria.uniqueIdentifiers
        };
        
        // Add pagination if provided
        if (pagination) {
            searchParams.offset = pagination.offset || 0;
            searchParams.limit = pagination.limit || 100;
            
            // Validate pagination values
            if (searchParams.limit > 1000) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "limit cannot exceed 1000");
            }
            if (searchParams.offset < 0) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "offset cannot be negative");
            }
        }
        
        logger.info(`Searching campaign data: ${getFormattedStringForDebug(searchParams)}`);
        
        // Execute search
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
        
        // Validate required fields
        if (!searchCriteria) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "SearchCriteria is required");
        }
        
        if (!searchCriteria.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required in SearchCriteria");
        }
        
        // Build search parameters
        const searchParams: any = {
            tenantId: searchCriteria.tenantId,
            type: searchCriteria.type,
            campaignNumber: searchCriteria.campaignNumber,
            status: searchCriteria.status,
            boundaryCode: searchCriteria.boundaryCode,
            uniqueIdentifierForData: searchCriteria.uniqueIdentifierForData
        };
        
        // Add pagination if provided
        if (pagination) {
            searchParams.offset = pagination.offset || 0;
            searchParams.limit = pagination.limit || 100;
            
            // Validate pagination values
            if (searchParams.limit > 1000) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "limit cannot exceed 1000");
            }
            if (searchParams.offset < 0) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "offset cannot be negative");
            }
        }
        
        logger.info(`Searching mapping data: ${getFormattedStringForDebug(searchParams)}`);
        
        // Execute search
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
