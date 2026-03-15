import express from "express";
import { prepareAndProduceCancelMessage, processBasedOnAction, processFetchMicroPlan, searchProjectCampaignResourcData, updateCampaignAfterSearch, validateAndFetchCampaign, updateResourceStatus, persistCampaignUpdate } from "../utils/campaignUtils";
import { logger } from "../utils/logger";
import { validateMicroplanRequest, validateProjectCampaignRequest, validateAddResourcesRequest } from "../validators/campaignValidators";
import { campaignStatuses, resourceStatuses } from "../config/constants";
import { prepareProcessesForResourceTypes, getCurrentProcesses } from "../utils/genericUtils";
import { getRegistryEntry, hasDependenciesMet } from "../config/resourceTypeRegistry";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";

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
 * Get the effective status of a resource entry.
 * Reads resource.status (new field) falling back to additionalDetails.resourceStatuses[type].
 */
function getResourceEntryStatus(resource: any, campaignDetails: any): string | undefined {
    return resource?.status || campaignDetails?.additionalDetails?.resourceStatuses?.[resource?.type];
}

/**
 * Add resources to an existing campaign.
 * - For "drafted" campaigns: replaces resource by type (existing behavior)
 * - For "created" campaigns: multi-record merge with status-based rules
 */
async function addResourcesToCampaignService(request: express.Request) {
    // Validate request
    validateAddResourcesRequest(request);

    const requestCampaignDetails = request?.body?.CampaignDetails;
    const tenantId = requestCampaignDetails?.tenantId;
    const campaignId = requestCampaignDetails?.id;
    const useruuid = request?.body?.RequestInfo?.userInfo?.uuid;
    const requestInfo = request?.body?.RequestInfo;
    const newResources = requestCampaignDetails?.resources || [];

    // Search for the existing campaign
    const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
    const existingCampaign = campaignResp?.CampaignDetails?.[0];

    if (!existingCampaign) {
        throw Object.assign(new Error("Campaign not found"), { status: 404, code: "CAMPAIGN_NOT_FOUND" });
    }

    const campaignStatus = existingCampaign?.status;
    const newResourceTypes = newResources.map((r: any) => r.type);

    if (campaignStatus === campaignStatuses.drafted) {
        // For drafted campaigns: replace by type (existing behavior)
        const resourceMap = new Map((existingCampaign?.resources || []).map((r: any) => [r.type, r]));
        for (const newRes of newResources) {
            resourceMap.set(newRes.type, { ...newRes, status: resourceStatuses.toCreate });
        }
        existingCampaign.resources = Array.from(resourceMap.values());
        for (const type of newResourceTypes) {
            updateResourceStatus(existingCampaign, type, resourceStatuses.toCreate, tenantId, useruuid);
        }
        await persistCampaignUpdate(existingCampaign, requestInfo);
        logger.info(`Added ${newResourceTypes.length} resource(s) to drafted campaign ${campaignId}: ${newResourceTypes.join(", ")}`);
        return existingCampaign;
    }

    if (campaignStatus === campaignStatuses.inprogress) {
        // For created campaigns: multi-record merge with status-based rules
        for (const newRes of newResources) {
            const existingOfType = (existingCampaign?.resources || []).filter((r: any) => r?.type === newRes.type);
            const hasCreating = existingOfType.some((r: any) => getResourceEntryStatus(r, existingCampaign) === resourceStatuses.creating);

            if (hasCreating) {
                throw Object.assign(
                    new Error(`Resource type "${newRes.type}" is currently being created. Cannot add.`),
                    { status: 400, code: "VALIDATION_ERROR" }
                );
            }

            // Remove any toCreate/failed/undefined-status entries of same type (will be replaced by newRes)
            existingCampaign.resources = (existingCampaign?.resources || []).filter(
                (r: any) => !(r?.type === newRes.type && [resourceStatuses.toCreate, resourceStatuses.failed, undefined].includes(getResourceEntryStatus(r, existingCampaign)))
            );

            // Add the new entry with toCreate status (completed entries are preserved above)
            existingCampaign.resources.push({ ...newRes, status: resourceStatuses.toCreate });
        }

        // Prepare processes and trigger creation for newly added resources
        const triggerableTypes = newResourceTypes.filter((type: string) => {
            const entry = (existingCampaign?.resources || []).find(
                (r: any) => r?.type === type && getResourceEntryStatus(r, existingCampaign) === resourceStatuses.toCreate
            );
            return !!entry;
        });

        if (triggerableTypes.length > 0) {
            await prepareProcessesForResourceTypes(existingCampaign.campaignNumber, tenantId, triggerableTypes, useruuid);

            const allCurrentProcesses = await getCurrentProcesses(existingCampaign.campaignNumber, tenantId);
            const completedProcessNames = new Set(
                allCurrentProcesses
                    .filter((p: any) => p?.status === "completed")
                    .map((p: any) => p?.processName)
            );

            for (const type of triggerableTypes) {
                const registryEntry = getRegistryEntry(type);
                if (!registryEntry) {
                    logger.warn(`No registry entry found for resource type: ${type}. Skipping.`);
                    continue;
                }

                if (!hasDependenciesMet(type, completedProcessNames)) {
                    logger.warn(`Dependencies not met for ${type}. Resource status set to toCreate.`);
                    updateResourceStatus(existingCampaign, type, resourceStatuses.toCreate, tenantId, useruuid);
                    continue;
                }

                const task = allCurrentProcesses.find((p: any) => p?.processName === registryEntry.processName);
                if (task) {
                    logger.info(`Triggering creation for resource type: ${type} (${registryEntry.processName})`);
                    await produceModifiedMessages({
                        task,
                        CampaignDetails: existingCampaign,
                        parentCampaign: null,
                        requestInfo
                    }, config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC, tenantId, registryEntry.kafkaKey);
                    updateResourceStatus(existingCampaign, type, resourceStatuses.creating, tenantId, useruuid);
                    // Also set status on the resource entry itself
                    const resEntry = (existingCampaign?.resources || []).find(
                        (r: any) => r?.type === type && r?.status === resourceStatuses.toCreate
                    );
                    if (resEntry) resEntry.status = resourceStatuses.creating;
                }
            }
        }

        await persistCampaignUpdate(existingCampaign, requestInfo);
        logger.info(`Added and triggered ${newResourceTypes.length} resource(s) for created campaign ${campaignId}: ${newResourceTypes.join(", ")}`);
        return existingCampaign;
    }

    // Other statuses: reject
    throw Object.assign(
        new Error(`Cannot add resources to campaign in "${campaignStatus}" status. Campaign must be in "drafted" or "created" status.`),
        { status: 400, code: "VALIDATION_ERROR" }
    );
}

export {
    createProjectTypeCampaignService,
    updateProjectTypeCampaignService,
    searchProjectTypeCampaignService,
    fetchFromMicroplanService,
    cancelCampaignService,
    addResourcesToCampaignService
}
