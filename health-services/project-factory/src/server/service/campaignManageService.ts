import express from "express";
import { prepareAndProduceCancelMessage, processBasedOnAction, processFetchMicroPlan, searchProjectCampaignResourcData, updateCampaignAfterSearch, validateAndFetchCampaign } from "../utils/campaignUtils";
import { logger } from "../utils/logger";
import { validateMicroplanRequest, validateProjectCampaignRequest, validateAddResourcesRequest } from "../validators/campaignValidators";
import { campaignStatuses, processStatuses } from "../config/constants";
import { createResourceDetail, updateResourceDetail } from "./resourceDetailsService";
import { findActiveResourceByUpsertKey } from "../utils/resourceDetailsUtils";
import { prepareProcessesForResourceTypes, getCurrentProcesses } from "../utils/genericUtils";
import config from "../config";
import { produceModifiedMessages } from "../kafka/Producer";
import { getRegistryEntry } from "../config/resourceTypeRegistry";
import { CampaignResource } from "../config/models/resourceTypes";

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

    // Backward compat: if resources are in the request body, upsert into eg_cm_resource_details
    const requestResources = request?.body?.CampaignDetails?.resources || [];
    const tenantId = request?.body?.CampaignDetails?.tenantId;
    const campaignId = request?.body?.CampaignDetails?.id;
    const useruuid = request?.body?.RequestInfo?.userInfo?.uuid || "system";
    if (requestResources.length > 0 && tenantId && campaignId) {
        for (const res of requestResources as CampaignResource[]) {
            const fileStoreId = res.fileStoreId || res.filestoreId;
            if (!res.type || !fileStoreId) continue;
            try {
                const existing = await findActiveResourceByUpsertKey(tenantId, campaignId, res.type, res.parentResourceId || null);
                if (existing) {
                    // Update existing resource instead of deactivating + creating new
                    await updateResourceDetail({
                        id: existing.id,
                        tenantId,
                        campaignId,
                        fileStoreId,
                        filename: res.filename !== undefined ? res.filename : undefined
                    }, useruuid);
                    logger.info(`Updated existing resource id=${existing.id} type=${res.type} for campaign ${campaignId} via update`);
                } else {
                    await createResourceDetail({
                        tenantId,
                        campaignId,
                        type: res.type,
                        parentResourceId: res.parentResourceId || null,
                        fileStoreId,
                        filename: res.filename || null,
                        additionalDetails: res.additionalDetails || {}
                    }, useruuid);
                    logger.info(`Created new resource type=${res.type} for campaign ${campaignId} via update`);
                }
            } catch (err) {
                logger.warn(`Failed to upsert resource type=${res.type} for campaign ${campaignId}: ${err}`);
            }
        }
        // Re-fetch campaign so response includes full resources from table
        const updatedResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        return updatedResp?.CampaignDetails?.[0] || request?.body?.CampaignDetails;
    }

    return request?.body?.CampaignDetails;
}

async function searchProjectTypeCampaignService(campaignDetails: any , request? :any
) {
    // await validateSearchProjectCampaignRequest(request);
    logger.info("VALIDATED THE PROJECT TYPE SEARCH REQUEST");

    // Search for project campaign resource data
    const { responseData, totalCount } = await searchProjectCampaignResourcData(campaignDetails , request);
    const responseBody: any = { CampaignDetails: responseData, totalCount: totalCount }
    return responseBody;
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

async function cancelCampaignService(request: any) {
    const campaignToCancel = await validateAndFetchCampaign(request);
    const cancelledCampaign = await prepareAndProduceCancelMessage(campaignToCancel, request?.body?.RequestInfo, request);
    return cancelledCampaign;
}

/**
 * Add resources to an existing campaign.
 * Delegates to the new resource details service.
 * Resources are stored in eg_cm_resource_details, not in the campaign JSONB.
 * Preserves backward-compatible request/response shape.
 */
async function addResourcesToCampaignService(request: express.Request) {
    // Validate request
    validateAddResourcesRequest(request);

    const requestCampaignDetails = request?.body?.CampaignDetails;
    const tenantId = requestCampaignDetails?.tenantId;
    const campaignId = requestCampaignDetails?.id;
    const useruuid = request?.body?.RequestInfo?.userInfo?.uuid || "system";
    const newResources = requestCampaignDetails?.resources || [];

    // Search for the existing campaign
    const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
    const existingCampaign = campaignResp?.CampaignDetails?.[0];

    if (!existingCampaign) {
        throw Object.assign(new Error("Campaign not found"), { status: 404, code: "CAMPAIGN_NOT_FOUND" });
    }

    const campaignStatus = existingCampaign?.status;
    if (campaignStatus !== campaignStatuses.drafted && campaignStatus !== campaignStatuses.inprogress) {
        throw Object.assign(
            new Error(`Cannot add resources to campaign in "${campaignStatus}" status. Campaign must be in "drafted" or "inprogress" status.`),
            { status: 400, code: "VALIDATION_ERROR" }
        );
    }

    // Delegate to resource details service — stores in eg_cm_resource_details
    const createdResources = [];
    const createdResourceTypes: string[] = [];
    for (const res of newResources as CampaignResource[]) {
        if (!res.type || !res.filestoreId) {
            logger.warn(`Skipping resource with missing type or filestoreId: ${JSON.stringify(res)}`);
            continue;
        }
        const created = await createResourceDetail({
            tenantId,
            campaignId,
            type: res.type,
            parentResourceId: res.parentResourceId || null,
            fileStoreId: res.filestoreId,
            filename: res.filename || null,
            additionalDetails: res.additionalDetails || {}
        }, useruuid);
        createdResources.push(created);
        createdResourceTypes.push(res.type);
        logger.info(`Stored resource type=${res.type} id=${created.id} for campaign ${campaignId}`);
    }

    // Re-fetch campaign enriched with new resources from table
    const updatedCampaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
    const updatedCampaign = updatedCampaignResp?.CampaignDetails?.[0] || existingCampaign;

    // For in-progress campaigns, trigger processing for newly added resources.
    // Use updatedCampaign so CampaignDetails in the task message includes the new resources.
    if (campaignStatus === campaignStatuses.inprogress && createdResourceTypes.length > 0) {
        const campaignNumber = existingCampaign?.campaignNumber;
        if (campaignNumber) {
            await prepareProcessesForResourceTypes(campaignNumber, tenantId, createdResourceTypes, useruuid);
            const allCurrentProcesses = await getCurrentProcesses(campaignNumber, tenantId);
            for (const resType of createdResourceTypes) {
                const registryEntry = getRegistryEntry(resType);
                if (!registryEntry) continue;
                const task = allCurrentProcesses.find((p: any) => p?.processName === registryEntry.processName);
                if (task && task?.status === processStatuses.pending) {
                    await produceModifiedMessages({
                        task,
                        CampaignDetails: updatedCampaign,
                        useruuid,
                        requestInfo: request?.body?.RequestInfo
                    }, config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC, tenantId, registryEntry.kafkaKey);
                    logger.info(`Triggered processing task for type=${resType} on in-progress campaign ${campaignId}`);
                }
            }
        }
    }

    logger.info(`Added ${createdResources.length} resource(s) to campaign ${campaignId}: ${createdResourceTypes.join(", ")}`);
    return updatedCampaign;
}

export {
    createProjectTypeCampaignService,
    updateProjectTypeCampaignService,
    searchProjectTypeCampaignService,
    fetchFromMicroplanService,
    cancelCampaignService,
    addResourcesToCampaignService
}
