import express from "express";
import { processGenericRequest } from "../api/campaignApis";
import { createAndUploadFile, getBoundarySheetData } from "../api/genericApis";
import { processDataSearchRequest } from "../utils/campaignUtils";
import { enrichResourceDetails, getLocalizedMessagesHandler, getResponseFromDb, processGenerate, throwError } from "../utils/genericUtils";
import { getLocalisationModuleName } from "../utils/localisationUtils";
import { logger } from "../utils/logger";
import { validateCreateRequest, validateDownloadRequest, validateSearchRequest } from "../validators/campaignValidators";
import { validateGenerateRequest } from "../validators/genericValidator";

const generateDataService = async (request: express.Request) => {
    // Validate the generate request
    await validateGenerateRequest(request);
    logger.info("VALIDATED THE DATA GENERATE REQUEST");
    const { hierarchyType } = request.query;
    // Process the data generation
    // localize the codes present in boundary modules
    request?.query?.hierarchyType && await getLocalizedMessagesHandler(request, request?.query?.tenantId, getLocalisationModuleName(hierarchyType));
    const localizationMap = await getLocalizedMessagesHandler(request, request?.query?.tenantId);
    await processGenerate(request, localizationMap);
    // Send response with generated resource details
    return request?.body?.generatedResource;
};


const downloadDataService = async (request: express.Request) => {
    await validateDownloadRequest(request);
    logger.info("VALIDATED THE DATA DOWNLOAD REQUEST");

    const type = request.query.type;
    // Get response data from the database
    const responseData = await getResponseFromDb(request);
    // Check if response data is available
    if (!responseData || responseData.length === 0 && !request?.query?.id) {
        logger.error("No data of type  " + type + " with status Completed or with given id presnt in db ")
        // Throw error if data is not found
        throwError("CAMPAIGN", 500, "GENERATION_REQUIRE");
    }
    return responseData;
}

const getBoundaryDataService = async (
    request: express.Request
) => {
    const localizationMap = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId || request?.query?.tenantId || 'mz');
    // Retrieve boundary sheet data
    const boundarySheetData: any = await getBoundarySheetData(request, localizationMap);
    // Create and upload file
    const BoundaryFileDetails: any = await createAndUploadFile(boundarySheetData?.wb, request);
    // Return boundary file details
    logger.info("RETURNS THE BOUNDARY RESPONSE");
    return BoundaryFileDetails;
};


const createDataService = async (request: any) => {
    // Validate the create request
    await validateCreateRequest(request);
    logger.info("VALIDATED THE DATA CREATE REQUEST");


    const localizationMap = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId);

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