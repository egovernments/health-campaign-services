import express from "express";
import { processBasedOnAction, processFetchMicroPlan, searchProjectCampaignResourcData, updateCampaignAfterSearch } from "../utils/campaignUtils";
import { logger } from "../utils/logger";
import { validateMicroplanRequest, validateProjectCampaignRequest } from "../validators/campaignValidators";
import { validateCampaignRequest } from "../validators/genericValidator";
import { createRelatedResouce } from "../api/genericApis";
import { enrichCampaign } from "../api/campaignApis";

async function createProjectTypeCampaignService(request: express.Request) {
    // Validate the request for creating a project type campaign
    logger.info("VALIDATING:: for project type");
    await validateProjectCampaignRequest(request, "create");
    logger.info("VALIDATED:: THE PROJECT TYPE CREATE REQUEST");

    // Process the action based on the request type
    await processBasedOnAction(request, "create");
    return request?.body?.CampaignDetails;
}

async function updateProjectTypeCampaignService(request: express.Request) {

    await validateProjectCampaignRequest(request, "update");
    logger.info("VALIDATED THE PROJECT TYPE UPDATE REQUEST");

    // Process the action based on the request type
    await processBasedOnAction(request, "update");
    return request?.body?.CampaignDetails;
}

async function searchProjectTypeCampaignService(campaignDetails: any
) {
    // await validateSearchProjectCampaignRequest(request);
    logger.info("VALIDATED THE PROJECT TYPE SEARCH REQUEST");

    // Search for project campaign resource data
    const { responseData, totalCount } = await searchProjectCampaignResourcData(campaignDetails);
    const responseBody: any = { CampaignDetails: responseData, totalCount: totalCount }
    return responseBody;
};

async function createCampaignService(
    requestBody: any
) {
    await validateCampaignRequest(requestBody)
    logger.info("VALIDATED THE CAMPAIGN CREATE REQUEST");

    // Create related resource
    await createRelatedResouce(requestBody)

    // Enrich the campaign
    await enrichCampaign(requestBody)
    return requestBody?.Campaign
};

async function fetchFromMicroplanService(request: express.Request) {
    logger.info("FETCHING DATA FROM MICROPLAN");
    await validateMicroplanRequest(request);
    logger.info("UPDATE CAMPAIGN OBJECT")
    await updateCampaignAfterSearch(request, "MICROPLAN_FETCHING")
    logger.info("Validated request successfully");
    processFetchMicroPlan(request);
    return request.body.CampaignDetails;
}


export {
    createProjectTypeCampaignService,
    updateProjectTypeCampaignService,
    searchProjectTypeCampaignService,
    createCampaignService,
    fetchFromMicroplanService
}
