import config from "../config";
import { getCurrentProcesses, throwError } from "./genericUtils";
import { logger } from "./logger";
import { httpRequest } from "./request";
import { produceModifiedMessages } from "../kafka/Producer";
import { enrichAndPersistCampaignWithError, enrichAndPersistCampaignWithErrorProcessingTask } from "./campaignUtils";
import { allProcesses, processStatuses } from "../config/constants";
import { createProjectFacilityHelper, createProjectResourceHelper, createProjectStaffHelper } from "../api/genericApis";
import { startResourceMapping } from "./resourceMappingUtils";
import { startUserMappingAndDemapping } from "./userMappingUtils";
import { startFacilityMappingAndDemapping } from "./facilityMappingUtils";

/** Collect the distinct product-variant IDs referenced across a campaign's delivery rules. */
export function getPvarIds(messageObject: any) {
    logger.info("campaign product resource mapping started");
    const deliveryRules = messageObject?.CampaignDetails?.deliveryRules;
    const uniquePvarIds = new Set();
    if (deliveryRules) {
        for (const deliveryRule of deliveryRules) {
            const products = deliveryRule?.resources;
            if (products) {
                for (const product of products) {
                    uniquePvarIds.add(product?.productVariantId);
                }
            }
        }
    }
    logger.info(`campaign product resource found items : ${JSON.stringify(uniquePvarIds)}`);
    return Array.from(uniquePvarIds);
}

/** Confirm a campaign exists for the given id before mapping proceeds, else throw. */
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

/** Create project-staff mappings in batches; on failure persist campaign error and rethrow. */
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
    let totalCreated = 0;
    let batchCount = 0;
    let createHelperFn: any;
    if (type === 'resource') {
        createHelperFn = createProjectResourceHelper;
    } else if (type === 'staff') {
        createHelperFn = createProjectStaffHelper;
    } else if (type === 'facility') {
        createHelperFn = createProjectFacilityHelper;
    } else {
        logger.error(`Unsupported type: ${type}`);
        return;
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
                    await Promise.all(promises);
                } catch (error) {
                    logger.error(`Batch ${batchCount} failed:`, error);
                    throw error;
                } promises = [];
            }
        }
    }

    if (promises.length > 0) {
        batchCount++;
        logger.info(`Processing final batch ${batchCount} with ${promises.length} promises.`);
        await Promise.all(promises);
    }

    logger.info(`Processing completed. Total resources created: ${totalCreated}`);
}


/** Create project-resource mappings in batches; on failure persist campaign error and rethrow. */
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

/** Create project-facility mappings in batches; on failure persist campaign error and rethrow. */
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

/** Legacy Kafka mapping-task handler: runs the per-process mapping and records task status. */
export async function handleMappingTaskForCampaign(messageObject: any) {
    try {
        const { CampaignDetails, task, requestInfo } = messageObject;
        const processName = task?.processName;
        const useruuid = requestInfo?.userInfo?.uuid;
        logger.info(`Mapping for campaign ${CampaignDetails?.id} : ${processName} started..`);

        // Idempotency guard for at-least-once delivery: this legacy create path has no
        // adopt-existing pre-pass, so a crash-redelivered mapping task could create duplicate
        // project staff/facility/resource records. Re-read live status and skip if completed.
        const campaignNumber = task?.campaignNumber || CampaignDetails?.campaignNumber;
        if (campaignNumber && processName) {
            const alreadyCompleted = await getCurrentProcesses(campaignNumber, CampaignDetails?.tenantId, processName, processStatuses.completed);
            if (alreadyCompleted.length > 0) {
                logger.info(`Mapping SKIP campaign=${CampaignDetails?.id} process=${processName} — already completed (redelivery-safe)`);
                return;
            }
        }

        if(processName == allProcesses.resourceMapping) {
            await startResourceMapping(CampaignDetails, useruuid, requestInfo);
        }
        else if(processName == allProcesses.facilityMapping) {
            await startFacilityMappingAndDemapping(CampaignDetails, useruuid, requestInfo);
        }
        else if (processName == allProcesses.userMapping) {
            await startUserMappingAndDemapping(CampaignDetails, useruuid, requestInfo);
        }
        task.status = processStatuses.completed;
        const currentTime = Date.now();
        task.auditDetails = {
            createdBy: task.auditDetails?.createdBy || useruuid,
            createdTime: task.auditDetails?.createdTime || currentTime,
            lastModifiedBy: useruuid,
            lastModifiedTime: currentTime
        };
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC, CampaignDetails?.tenantId);
    } catch (error) {
        let task = messageObject?.task;
        task.status = processStatuses.failed;
        const currentTime = Date.now();
        task.auditDetails = {
            createdBy: task.auditDetails?.createdBy || messageObject?.requestInfo?.userInfo?.uuid,
            createdTime: task.auditDetails?.createdTime || currentTime,
            lastModifiedBy: messageObject?.requestInfo?.userInfo?.uuid,
            lastModifiedTime: currentTime
        };
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC, messageObject?.CampaignDetails?.tenantId);
        logger.error(`Error in campaign mapping: ${error}`);
        await enrichAndPersistCampaignWithErrorProcessingTask(messageObject?.CampaignDetails, messageObject?.parentCampaign, messageObject?.requestInfo, error);
    }
}
