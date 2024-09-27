import express from "express";
import { processBasedOnAction, searchProjectCampaignResourcData } from "../utils/campaignUtils";
import { logger } from "../utils/logger";
import { validateProjectCampaignRequest, validateSearchProcessTracksRequest, validateSearchProjectCampaignRequest } from "../validators/campaignValidators";
import { validateCampaignRequest } from "../validators/genericValidator";
import { createRelatedResouce } from "../api/genericApis";
import { enrichCampaign } from "../api/campaignApis";
import { getProcessDetails, modifyProcessDetails } from "../utils/processTrackUtils";

async function createProjectTypeCampaignService(request: express.Request) {
    // Validate the request for creating a project type campaign
    await validateProjectCampaignRequest(request, "create");
    logger.info("VALIDATED THE PROJECT TYPE CREATE REQUEST");

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

async function searchProjectTypeCampaignService(
    request: express.Request,
) {
    // Validate the search request for project type campaigns
    await validateSearchProjectCampaignRequest(request);
    logger.info("VALIDATED THE PROJECT TYPE SEARCH REQUEST");

    // Search for project campaign resource data
    await searchProjectCampaignResourcData(request);
    const responseBody: any = { CampaignDetails: request?.body?.CampaignDetails, totalCount: request?.body?.totalCount }
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

async function searchProcessTracksService(
    request: express.Request
) {
    await validateSearchProcessTracksRequest(request)
    logger.info("VALIDATED THE PROCESS SEARCH REQUEST");

    // Search and return related process tracks
    const processDetailsArray = await getProcessDetails(request?.query?.campaignId as string)

    // sort and modify process details so that details with status as toBeCompleted comes in last
    const resultArray = modifyProcessDetails(processDetailsArray)

    return resultArray
};


export {
    createProjectTypeCampaignService,
    updateProjectTypeCampaignService,
    searchProjectTypeCampaignService,
    createCampaignService,
    searchProcessTracksService
}
