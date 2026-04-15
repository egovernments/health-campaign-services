import { logger } from './logger';
import { defaultRequestInfo } from '../api/coreApis';
import { getRelatedDataWithCampaign } from './genericUtils';
import { mappingStatuses } from '../config/constants';
import { createProjectResource, createProjectFacility, createStaff } from '../api/genericApis';
import { produceModifiedMessages } from '../kafka/Producer';
import config from '../config';
import { httpRequest } from './request';
import { sendCampaignFailureMessage } from './campaignFailureHandler';

/**
 * Handle mapping batch from Kafka - processes mappings based on type and status
 */
export async function handleMappingBatch(messageObject: any) {
    const { campaignId, tenantId } = messageObject;
    try {
        logger.info('=== PROCESSING MAPPING BATCH ===');
        
        const { tenantId, campaignNumber,  useruuid, mappings, batchNumber, totalBatches } = messageObject;
        
        logger.info(`Processing mapping batch ${batchNumber}/${totalBatches} with ${mappings.length} mappings`);
        
        if (!mappings || mappings.length === 0) {
            logger.warn('No mappings found in batch');
            return;
        }
        
        // Get required data mappings for all types
        const boundaryData = await getRelatedDataWithCampaign('boundary', campaignNumber, tenantId);
        const facilityData = await getRelatedDataWithCampaign('facility', campaignNumber, tenantId);
        const userData = await getRelatedDataWithCampaign('user', campaignNumber, tenantId);
        
        // Create lookup maps
        const boundaryToProjectId = createLookupMap(boundaryData, 'uniqueIdentifier', 'uniqueIdAfterProcess');
        const facilityMap = createLookupMap(facilityData, 'uniqueIdentifier', 'uniqueIdAfterProcess');
        const userMap = createLookupMap(userData, 'uniqueIdentifier', 'uniqueIdAfterProcess');
        
        // Group mappings by type and status for batch processing
        const mappingGroups = groupMappings(mappings);
        
        // Process each group with Promise.all
        const promises: Promise<void>[] = [];
        
        // Process toBeMapped
        if (mappingGroups.resourceToBeMapped.length > 0) {
            promises.push(processResourceMappings(mappingGroups.resourceToBeMapped, boundaryToProjectId, tenantId, useruuid));
        }
        if (mappingGroups.facilityToBeMapped.length > 0) {
            promises.push(processFacilityMappings(mappingGroups.facilityToBeMapped, boundaryToProjectId, facilityMap, tenantId, useruuid));
        }
        if (mappingGroups.userToBeMapped.length > 0) {
            promises.push(processUserMappings(mappingGroups.userToBeMapped, boundaryToProjectId, userMap, tenantId, useruuid));
        }
        
        // Process toBeDeMapped
        if (mappingGroups.resourceToBeDeMapped.length > 0) {
            promises.push(processResourceDemappings(mappingGroups.resourceToBeDeMapped, tenantId, useruuid));
        }
        if (mappingGroups.facilityToBeDeMapped.length > 0) {
            promises.push(processFacilityDemappings(mappingGroups.facilityToBeDeMapped, boundaryToProjectId, facilityMap, tenantId, useruuid));
        }
        if (mappingGroups.userToBeDeMapped.length > 0) {
            promises.push(processUserDemappings(mappingGroups.userToBeDeMapped, boundaryToProjectId, userMap, tenantId, useruuid));
        }
        
        // Execute all mappings in parallel
        await Promise.all(promises);
        
        logger.info(`Mapping batch ${batchNumber}/${totalBatches} completed successfully`);
        
    } catch (error) {
        logger.error('Error processing mapping batch:', error);
        
        // Send campaign failure message due to mapping batch processing error
        const batchError = new Error(`Mapping batch processing error: ${error instanceof Error ? error.message : String(error)}`);
        await sendCampaignFailureMessage(
            campaignId,
            tenantId,
            batchError
        );
    }
}

/**
 * Create lookup map from array
 */
function createLookupMap(data: any[], keyField: string, valueField: string): Record<string, string> {
    const map: Record<string, string> = {};
    data.forEach(item => {
        if (item[keyField] && item[valueField]) {
            map[item[keyField]] = item[valueField];
        }
    });
    return map;
}

/**
 * Group mappings by type and status
 */
function groupMappings(mappings: any[]) {
    const groups = {
        resourceToBeMapped: [] as any[],
        resourceToBeDeMapped: [] as any[],
        facilityToBeMapped: [] as any[],
        facilityToBeDeMapped: [] as any[],
        userToBeMapped: [] as any[],
        userToBeDeMapped: [] as any[]
    };

    mappings.forEach(mapping => {
        const type = mapping.type;
        const status = mapping.status;

        if (type === 'resource') {
            if (status === mappingStatuses.toBeMapped) {
                groups.resourceToBeMapped.push(mapping);
            } else if (status === mappingStatuses.toBeDeMapped) {
                groups.resourceToBeDeMapped.push(mapping);
            }
        } else if (type === 'facility') {
            if (status === mappingStatuses.toBeMapped) {
                groups.facilityToBeMapped.push(mapping);
            } else if (status === mappingStatuses.toBeDeMapped) {
                groups.facilityToBeDeMapped.push(mapping);
            }
        } else if (type === 'user') {
            if (status === mappingStatuses.toBeMapped) {
                groups.userToBeMapped.push(mapping);
            } else if (status === mappingStatuses.toBeDeMapped) {
                groups.userToBeDeMapped.push(mapping);
            }
        }
    });

    return groups;
}

/**
 * Persist data in batches to Kafka with chunking and wait time
 * Prevents Kafka message size limits and ensures proper persistence
 */
async function persistInBatches(datas: any[], topic: string, tenantId: string): Promise<void> {
    if (datas.length === 0) {
        return;
    }

    const BATCH_SIZE = 100;
    for (let i = 0; i < datas.length; i += BATCH_SIZE) {
        const batch = datas.slice(i, i + BATCH_SIZE);
        await produceModifiedMessages({ datas: batch }, topic, tenantId);
    }
    const waitTime = Math.max(5000, datas.length * 8);
    logger.info(`Waiting for ${waitTime} ms for persistence...`);
    await new Promise((res) => setTimeout(res, waitTime));
}

/**
 * Process resource mappings
 */
async function processResourceMappings(
    mappings: any[],
    boundaryToProjectId: Record<string, string>,
    tenantId: string,
    useruuid: string
): Promise<void> {
    logger.info(`Processing ${mappings.length} resource mappings`);

    const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
    RequestInfo.userInfo.uuid = useruuid;

    const updateBatch: any[] = []; // Collect all updates for batch sending

    const promises = mappings.map(async (mapping) => {
        try {
            const projectId = boundaryToProjectId[mapping.boundaryCode];
            if (!projectId) {
                throw new Error(`Project not found for boundary ${mapping.boundaryCode}`);
            }

            const ProjectResource = {
                tenantId,
                projectId,
                resource: {
                    productVariantId: mapping.uniqueIdentifierForData,
                    type: "DRUG",
                    isBaseUnitVariant: false,
                },
                startDate: null, // Set appropriate dates
                endDate: null,
            };

            const response = await createProjectResource({ RequestInfo, ProjectResource });

            mapping.status = mappingStatuses.mapped;
            if (response?.ProjectResource?.id) {
                mapping.mappingId = response.ProjectResource.id;
            }

            updateBatch.push(mapping); // Collect instead of sending immediately
        } catch (error) {
            logger.error(`Failed to create project resource for ${mapping.uniqueIdentifierForData}:`, error);

            // Mark mapping as failed
            mapping.status = mappingStatuses.failed;
            updateBatch.push(mapping); // Collect failed ones too
        }
    });

    await Promise.all(promises);

    // Send all updates in batches with chunking - prevents Kafka message size limits
    await persistInBatches(updateBatch, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
}

/**
 * Process facility mappings
 */
async function processFacilityMappings(
    mappings: any[],
    boundaryToProjectId: Record<string, string>,
    facilityMap: Record<string, string>,
    tenantId: string,
    useruuid: string
): Promise<void> {
    logger.info(`Processing ${mappings.length} facility mappings`);

    const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
    RequestInfo.userInfo.uuid = useruuid;

    const updateBatch: any[] = []; // Collect all updates for batch sending

    const promises = mappings.map(async (mapping) => {
        try {
            const projectId = boundaryToProjectId[mapping.boundaryCode];
            const facilityId = facilityMap[mapping.uniqueIdentifierForData];

            if (!projectId || !facilityId) {
                throw new Error(`Missing project/facility ID for ${mapping.uniqueIdentifierForData}`);
            }

            const ProjectFacility = {
                tenantId: tenantId.split(".")?.[0],
                projectId,
                facilityId,
                startDate: null,
                endDate: null
            };

            const response = await createProjectFacility({ RequestInfo, ProjectFacility });

            mapping.status = mappingStatuses.mapped;
            if (response?.ProjectFacility?.id) {
                mapping.mappingId = response.ProjectFacility.id;
            }

            updateBatch.push(mapping); // Collect instead of sending immediately
        } catch (error) {
            logger.error(`Failed to create project facility for ${mapping.uniqueIdentifierForData}:`, error);

            // Mark mapping as failed
            mapping.status = mappingStatuses.failed;
            updateBatch.push(mapping); // Collect failed ones too
        }
    });

    await Promise.all(promises);

    // Send all updates in batches with chunking - prevents Kafka message size limits
    await persistInBatches(updateBatch, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
}

/**
 * Process user mappings
 */
async function processUserMappings(
    mappings: any[],
    boundaryToProjectId: Record<string, string>,
    userMap: Record<string, string>,
    tenantId: string,
    useruuid: string
): Promise<void> {
    logger.info(`Processing ${mappings.length} user mappings`);

    const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
    RequestInfo.userInfo.uuid = useruuid;

    const updateBatch: any[] = []; // Collect all updates for batch sending

    const promises = mappings.map(async (mapping) => {
        try {
            const projectId = boundaryToProjectId[mapping.boundaryCode];
            const userId = userMap[mapping.uniqueIdentifierForData];

            if (!projectId || !userId) {
                throw new Error(`Missing project/user ID for ${mapping.uniqueIdentifierForData}`);
            }

            const ProjectStaff = {
                tenantId,
                projectId,
                userId,
                startDate: null,
                endDate: null,
            };

            const response = await createStaff({ RequestInfo, ProjectStaff });

            mapping.status = mappingStatuses.mapped;
            if (response?.ProjectStaff?.id) {
                mapping.mappingId = response.ProjectStaff.id;
            }

            updateBatch.push(mapping); // Collect instead of sending immediately
        } catch (error) {
            logger.error(`Failed to create project staff for ${mapping.uniqueIdentifierForData}:`, error);

            // Mark mapping as failed
            mapping.status = mappingStatuses.failed;
            updateBatch.push(mapping); // Collect failed ones too
        }
    });

    await Promise.all(promises);

    // Send all updates in batches with chunking - prevents Kafka message size limits
    await persistInBatches(updateBatch, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
}

/**
 * Process resource demappings (just delete from DB since resources can't be demapped)
 */
async function processResourceDemappings(
    mappings: any[],
    tenantId: string,
    useruuid: string
): Promise<void> {
    logger.info(`Processing ${mappings.length} resource demappings (direct deletion)`);

    const deleteBatch: any[] = []; // Collect successful deletions
    const failedBatch: any[] = []; // Collect failed ones

    // For resources, just delete the mapping entries as they can't be actually demapped
    const promises = mappings.map(async (mapping) => {
        try {
            deleteBatch.push(mapping); // Collect for batch deletion
        } catch (error) {
            logger.error(`Failed to delete resource mapping for ${mapping.uniqueIdentifierForData}:`, error);

            // Mark mapping as failed
            mapping.status = mappingStatuses.failed;
            failedBatch.push(mapping); // Collect failed ones
        }
    });

    await Promise.all(promises);

    // Send deletions and failures in batches with chunking
    await persistInBatches(deleteBatch, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC, tenantId);
    await persistInBatches(failedBatch, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
}

/**
 * Process facility demappings
 */
async function processFacilityDemappings(
    mappings: any[],
    boundaryToProjectId: Record<string, string>,
    facilityMap: Record<string, string>,
    tenantId: string,
    useruuid: string
): Promise<void> {
    logger.info(`Processing ${mappings.length} facility demappings`);

    const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
    RequestInfo.userInfo.uuid = useruuid;

    const deleteBatch: any[] = []; // Collect successful deletions
    const failedBatch: any[] = []; // Collect failed ones

    const promises = mappings.map(async (mapping) => {
        try {
            const projectId = boundaryToProjectId[mapping.boundaryCode];
            const facilityId = facilityMap[mapping.uniqueIdentifierForData];
            const mappingId = mapping.mappingId;

            if (!projectId || !facilityId || !mappingId) {
                // Direct delete for invalid mappings
                deleteBatch.push(mapping); // Collect for batch deletion
                return;
            }

            await fetchAndDeleteProjectFacility(RequestInfo, tenantId, projectId, facilityId);
            deleteBatch.push(mapping); // Collect for batch deletion
        } catch (error) {
            logger.error(`Failed to demap facility ${mapping.uniqueIdentifierForData}:`, error);

            // Mark mapping as failed instead of deleting
            mapping.status = mappingStatuses.failed;
            failedBatch.push(mapping); // Collect failed ones
        }
    });

    await Promise.all(promises);

    // Send deletions and failures in batches with chunking
    await persistInBatches(deleteBatch, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC, tenantId);
    await persistInBatches(failedBatch, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
}

/**
 * Process user demappings
 */
async function processUserDemappings(
    mappings: any[],
    boundaryToProjectId: Record<string, string>,
    userMap: Record<string, string>,
    tenantId: string,
    useruuid: string
): Promise<void> {
    logger.info(`Processing ${mappings.length} user demappings`);

    const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
    RequestInfo.userInfo.uuid = useruuid;

    const deleteBatch: any[] = []; // Collect successful deletions
    const failedBatch: any[] = []; // Collect failed ones

    const promises = mappings.map(async (mapping) => {
        try {
            const projectId = boundaryToProjectId[mapping.boundaryCode];
            const userId = userMap[mapping.uniqueIdentifierForData];
            const mappingId = mapping.mappingId;

            if (!projectId || !userId || !mappingId) {
                // Direct delete for invalid mappings
                deleteBatch.push(mapping); // Collect for batch deletion
                return;
            }

            await fetchAndDeleteProjectStaff(RequestInfo, tenantId, projectId, userId);
            deleteBatch.push(mapping); // Collect for batch deletion
        } catch (error) {
            logger.error(`Failed to demap user ${mapping.uniqueIdentifierForData}:`, error);

            // Mark mapping as failed instead of deleting
            mapping.status = mappingStatuses.failed;
            failedBatch.push(mapping); // Collect failed ones
        }
    });

    await Promise.all(promises);

    // Send deletions and failures in batches with chunking
    await persistInBatches(deleteBatch, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC, tenantId);
    await persistInBatches(failedBatch, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
}

/**
 * Fetch and delete project facility mapping
 */
async function fetchAndDeleteProjectFacility(RequestInfo: any, tenantId: string, projectId: string, facilityId: string) {
    const searchBody = {
        RequestInfo,
        ProjectFacility: {
            projectId: [projectId],
            facilityId: [facilityId]
        }
    };
    
    const searchParams = {
        tenantId,
        offset: 0,
        limit: 1
    };
    
    const response = await httpRequest(
        config?.host?.projectHost + config?.paths?.projectFacilitySearch,
        searchBody,
        searchParams
    );
    
    if (response?.ProjectFacilities?.length > 0) {
        const deleteBody = {
            RequestInfo,
            ProjectFacilities: [response?.ProjectFacilities[0]]
        };
        
        await httpRequest(config?.host?.projectHost + config?.paths?.projectFacilityDelete, deleteBody);
    }
}

/**
 * Fetch and delete project staff mapping
 */
async function fetchAndDeleteProjectStaff(RequestInfo: any, tenantId: string, projectId: string, userId: string) {
    const searchBody = {
        RequestInfo,
        ProjectStaff: {
            projectId: [projectId],
            staffId: [userId]
        }
    };
    
    const searchParams = {
        tenantId,
        offset: 0,
        limit: 1
    };
    
    const response = await httpRequest(
        config?.host?.projectHost + config?.paths?.projectStaffSearch,
        searchBody,
        searchParams
    );
    
    if (response?.ProjectStaff?.length > 0) {
        const deleteBody = {
            RequestInfo,
            ProjectStaff: [response?.ProjectStaff[0]]
        };
        
        await httpRequest(config?.host?.projectHost + config?.paths?.projectStaffDelete, deleteBody);
    }
}