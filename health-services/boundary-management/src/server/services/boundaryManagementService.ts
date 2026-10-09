import express from "express";
import {getLocalizedMessagesHandler,enrichResourceDetails,processGenerate,searchGeneratedBoundaryResources,callGenerate} from "../utils/genericUtils";
import { getLocalisationModuleName ,getLocaleFromRequestInfo} from "../utils/localisationUtils";
import { logger ,getFormattedStringForDebug} from "../utils/logger";
import config from "../config/index";
import { getNewExcelWorkbook, addDataToSheet } from "../utils/excelUtils";
import { redis, checkRedisConnection } from "../utils/redisUtils"; // Importing checkRedisConnection function
import { validateProcessRequest,validateDownloadRequest,validateSearchRequest } from "../validators/boundaryValidators";
import { processRequest } from "../api/boundaryApis";
import {validateGenerateRequest} from "../validators/genericValidator";
import {getBoundarySheetData,createAndUploadFile} from "../api/genericApis";
import {getLocalizedName,getBoundaryTabName,buildGenerateRequest,processDataSearchRequest} from "../utils/boundaryUtils";
import {generatedResourceStatuses} from "../config/constants";


const processBoundaryService = async (request: any) => {
  const hierarchyType = request?.body?.ResourceDetails?.hierarchyType;
  const localizationMapHierarchy = hierarchyType && await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId, getLocalisationModuleName(hierarchyType), true);
  const localizationMapModule = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId);
  const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };

  // Validate the create request
    logger.info("Validating data process request")
    await validateProcessRequest(request, localizationMap);
    logger.info("VALIDATED THE DATA PROCESS REQUEST");

    // Enrich resource details
    await enrichResourceDetails(request);

    // Process the generic request
    await processRequest(request, localizationMap);
    return request?.body?.ResourceDetails;
  

};

const generateDataService = async (request: express.Request) => {
    // Validate the generate request
    await validateGenerateRequest(request);
    logger.info("VALIDATED THE DATA GENERATE REQUEST");
    await processGenerate(request);
    // Send response with generated resource details
    return request?.body?.generatedResource;
};


/**
 * Retrieves boundary sheet data based on the provided request.
 * @param {express.Request} request - Request object
 * @param {boolean} enableCaching - Whether to enable caching or not
 * @returns {Promise<object>} - Promise object containing the boundary sheet data
 * @throws {Error} - Throws an error if the request is invalid
 */
const getBoundaryDataService = async (
    request: express.Request, enableCaching = false) => {
    try {
        const { hierarchyType } = request?.query;
        const cacheTTL = config?.cacheTime; // TTL in seconds (5 minutes)
        const cacheKey = `boundary-management-cacheKey-${hierarchyType}`;
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

const downloadDataService = async (request: express.Request) => {
    await validateDownloadRequest(request);
    logger.info("VALIDATED THE DATA DOWNLOAD REQUEST");
    // Get response data from the database
    const responseData = await searchGeneratedBoundaryResources(request?.query, getLocaleFromRequestInfo(request?.body?.RequestInfo));
    const hierarchyType = String(request?.query?.hierarchyType);

    // Check if response data is available
    if (
        !responseData ||
        (responseData.length === 0 && !request?.query?.id) ||
        responseData?.[0]?.status === generatedResourceStatuses.failed
    ) {
        logger.error(`No data of hierarchyType ${hierarchyType} - found with status 'Completed' or the provided ID is present in the database.`)
        // Throw error if data is not found
        // const newRequestToGenerate = buildGenerateRequest(request);

        // Added auto generate since no previous generate request found
        logger.info(`Triggering auto generate since no resources got generated for the given Campaign Id ${request?.query?.campaignId} & type ${request?.query?.type}`)


            const newRequestToGenerate = buildGenerateRequest(request);
            callGenerate(newRequestToGenerate, request?.query?.type);
    }


        return responseData;
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

export { processBoundaryService,generateDataService ,getBoundaryDataService,downloadDataService,searchDataService};
