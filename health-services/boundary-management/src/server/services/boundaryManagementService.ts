import express from "express";
import {getLocalizedMessagesHandler,enrichResourceDetails,processGenerate} from "../utils/genericUtils";
import { getLocalisationModuleName } from "../utils/localisationUtils";
import { logger ,getFormattedStringForDebug} from "../utils/logger";
import config from "../config/index";
import { getNewExcelWorkbook, addDataToSheet } from "../utils/excelUtils";
import { redis, checkRedisConnection } from "../utils/redisUtils"; // Importing checkRedisConnection function
import { validateProcessRequest } from "../validators/boundaryValidators";
import { processRequest } from "../api/boundaryApis";
import {validateGenerateRequest} from "../validators/genericValidator";
import {getBoundarySheetData,createAndUploadFile} from "../api/genericApis";
import {getLocalizedName,getBoundaryTabName} from "../utils/boundaryUtils";


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
        const cacheKey = `${hierarchyType}`;
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

export { processBoundaryService,generateDataService ,getBoundaryDataService};
