import config from "../config";
import { throwError } from "./genericUtils";
import { logger } from "./logger";
import { httpRequest } from "./request";
import { produceModifiedMessages } from "../kafka/Producer";
import { enrichAndPersistCampaignWithError, enrichAndPersistCampaignWithErrorProcessingTask } from "./campaignUtils";
import { allProcesses, processStatuses } from "../config/constants";
import { createProjectFacilityHelper, createProjectResourceHelper, createProjectStaffHelper } from "../api/genericApis";
import { startResourceMapping } from "./resourceMappingUtils";
import { startUserMappingAndDemapping } from "./userMappingUtils";
import { startFacilityMappingAndDemapping } from "./facilityMappingUtils";

export function getPvarIds(messageObject: any) {
    //update to set now
    logger.info("campaign product resource mapping started");
    const deliveryRules = messageObject?.CampaignDetails?.deliveryRules;
    const uniquePvarIds = new Set(); // Create a Set to store unique pvar IDs
    if (deliveryRules) {
        for (const deliveryRule of deliveryRules) {
            const products = deliveryRule?.resources;
            if (products) {
                for (const product of products) {
                    uniquePvarIds.add(product?.productVariantId); // Add pvar ID to the Set
                }
            }
        }
    }
    logger.info(`campaign product resource found items : ${JSON.stringify(uniquePvarIds)}`);
    return Array.from(uniquePvarIds); // Convert Set to array before returning
}

export async function validateMappingId(messageObject: any, id: string) {
    const searchBody = {
        RequestInfo: messageObject?.RequestInfo,
        CampaignDetails: {
            ids: [id],
            tenantId: messageObject?.Campaign?.tenantId,
        }
    }
    const response: any = await httpRequest(config.host.projectFactoryBff + "project-factory/v1/project-type/search", searchBody);
    if (!response?.CampaignDetails?.[0]) {
        throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "Campaign with id " + id + " does not exist");
    }
    return response?.CampaignDetails?.[0];
}

export async function handleStaffMapping(mappingArray: any[], campaignId: string, messageObject: any, type: string) {
    try {
        logger.debug(`staff mapping count: ${mappingArray.length}`);
        await processResourceOrFacilityOrUserMappingsInBatches(type, mappingArray, config?.batchSize || 100);
    } catch (error: any) {
        logger.error("Error in staff mapping: " + error);
        await enrichAndPersistCampaignWithError(messageObject, error);
        throw new Error(error)
    }
}

async function processResourceOrFacilityOrUserMappingsInBatches(type: string, mappingArray: any, batchSize: number) {
    logger.info("Processing resource mappings in batches...");
    let promises: Promise<void>[] = [];
    let totalCreated = 0; // To keep track of the total number of created resources
    let batchCount = 0;   // To log batch-wise progress
    // Determine the helper function to use based on the type
    let createHelperFn: any;
    if (type === 'resource') {
        createHelperFn = createProjectResourceHelper;
    } else if (type === 'staff') {
        createHelperFn = createProjectStaffHelper;
    } else if (type === 'facility') {
        createHelperFn = createProjectFacilityHelper;
    } else {
        logger.error(`Unsupported type: ${type}`);
        return;  // Exit the function if the type is unsupported
    }

    for (const mapping of mappingArray) {
        const { resource, projectId, resouceBody, tenantId, startDate, endDate } = mapping;

        for (const resourceId of resource?.resourceIds || []) {
                promises.push(
                    createHelperFn(resourceId, projectId, resouceBody, tenantId, startDate, endDate).then(() => {
                        totalCreated++;
                    })
                );

            if (promises.length >= batchSize) {
                batchCount++;
                logger.info(`Processing batch ${batchCount} with ${promises.length} promises.`);
                try {
                    await Promise.all(promises); // Wait for all promises in the current batch
                } catch (error) {
                    logger.error(`Batch ${batchCount} failed:`, error);
                    throw error; // Ensure any error in the batch is propagated
                } promises = []; // Reset the array for the next batch
            }
        }
    }

    // Process any remaining promises
    if (promises.length > 0) {
        batchCount++;
        logger.info(`Processing final batch ${batchCount} with ${promises.length} promises.`);
        await Promise.all(promises);
    }

    logger.info(`Processing completed. Total resources created: ${totalCreated}`);
}


export async function handleResourceMapping(mappingArray: any[], campaignId: any, messageObject: any, type: string) {
    try {
        logger.debug(`Resource mapping count: ${mappingArray.length}`);
        await processResourceOrFacilityOrUserMappingsInBatches(type, mappingArray, config?.batchSize || 100);
    } catch (error: any) {
        logger.error("Error in resource mapping: " + error);
        await enrichAndPersistCampaignWithError(messageObject, error);
        throw new Error(error)
    }
}

export async function handleFacilityMapping(mappingArray: any, campaignId: any, messageObject: any, type: string) {
    try {
        logger.debug(`facility mapping count: ${mappingArray.length}`);
        await processResourceOrFacilityOrUserMappingsInBatches(type, mappingArray, config?.batchSize || 100);
    } catch (error: any) {
        logger.error("Error in facility mapping: " + error);
        await enrichAndPersistCampaignWithError(messageObject, error);
        throw new Error(error)
    }
}

export async function handleMappingTaskForCampaign(messageObject: any) {
    try {
        const { CampaignDetails, task, useruuid } = messageObject;
        const processName = task?.processName
        logger.info(`Mapping for campaign ${CampaignDetails?.id} : ${processName} started..`);
        if(processName == allProcesses.resourceMapping) {
            await startResourceMapping(CampaignDetails, useruuid);
        }
        else if(processName == allProcesses.facilityMapping) {
            await startFacilityMappingAndDemapping(CampaignDetails, useruuid);
        }
        else if (processName == allProcesses.userMapping) {
            await startUserMappingAndDemapping(CampaignDetails, useruuid);
        }
        task.status = processStatuses.completed;
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC, CampaignDetails?.tenantId);
    } catch (error) {
        let task = messageObject?.task;
        task.status = processStatuses.failed;
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC, messageObject?.CampaignDetails?.tenantId);
        logger.error(`Error in campaign mapping: ${error}`);
        await enrichAndPersistCampaignWithErrorProcessingTask(messageObject?.CampaignDetails, messageObject?.parentCampaign, messageObject?.useruuid, error);
    }
}
