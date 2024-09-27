import express from "express";
import { processGenericRequest } from "../api/campaignApis";
import { createAndUploadFile, getBoundarySheetData } from "../api/genericApis";
import { getLocalizedName, processDataSearchRequest } from "../utils/campaignUtils";
import { addDataToSheet, enrichResourceDetails, getLocalizedMessagesHandler, searchGeneratedResources, processGenerate, throwError } from "../utils/genericUtils";
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { validateCreateRequest, validateDownloadRequest, validateSearchRequest } from "../validators/campaignValidators";
import { validateGenerateRequest } from "../validators/genericValidator";
import { getLocalisationModuleName } from "../utils/localisationUtils";
import { getBoundaryTabName } from "../utils/boundaryUtils";
import { getNewExcelWorkbook } from "../utils/excelUtils";
import { redis, checkRedisConnection } from "../utils/redisUtils"; // Importing checkRedisConnection function
import config from '../config/index'



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

    const type = request.query.type;
    // Get response data from the database
    const responseData = await searchGeneratedResources(request);
    // Check if response data is available
    if (!responseData || responseData.length === 0 && !request?.query?.id) {
        logger.error("No data of type  " + type + " with status Completed or with given id presnt in db ")
        // Throw error if data is not found
        throwError("CAMPAIGN", 500, "GENERATION_REQUIRE");
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
        const localizationMapHierarchy = hierarchyType && await getLocalizedMessagesHandler(request, request?.query?.tenantId, getLocalisationModuleName(hierarchyType));
        const localizationMapModule = await getLocalizedMessagesHandler(request, request?.query?.tenantId);
        const localizationMap = { ...localizationMapHierarchy, ...localizationMapModule };
        // Retrieve boundary sheet data
        const boundarySheetData: any = await getBoundarySheetData(request, localizationMap);
        const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap);
        const boundarySheet = workbook.addWorksheet(localizedBoundaryTab);
        addDataToSheet(boundarySheet, boundarySheetData);
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

    const localizationMap = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId);
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

export {
    generateDataService,
    downloadDataService,
    getBoundaryDataService,
    createDataService,
    searchDataService
}