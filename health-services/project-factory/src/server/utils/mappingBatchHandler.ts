import { RequestInfo } from "../config/models/requestInfoSchema";
import { logger } from './logger';
import { getRelatedDataWithCampaign } from './genericUtils';
import { mappingStatuses } from '../config/constants';
import { createProjectResource, createProjectFacility, createStaff, searchProjectResourcesByProjects, searchProjectFacilitiesByProjects, searchProjectStaffByProjects } from '../api/genericApis';
import { produceModifiedMessages } from '../kafka/Producer';
import config from '../config';
import { httpRequest } from './request';
import { sendCampaignFailureMessage } from './campaignFailureHandler';
import { getCurrentMappingGeneration } from './mappingGenerationUtils';
import { executeQuery, getTableName } from './db';

/**
 * Handle mapping batch from Kafka - processes mappings based on type and status
 */
export async function handleMappingBatch(messageObject: any) {
    const { campaignId, tenantId } = messageObject;
    try {
        logger.info('=== PROCESSING MAPPING BATCH ===');

        const { tenantId, campaignNumber, useruuid, mappings, batchNumber, totalBatches, requestInfo, generation } = messageObject;

        logger.info(`Processing mapping batch ${batchNumber}/${totalBatches} with ${mappings.length} mappings`);

        if (!mappings || mappings.length === 0) {
            logger.warn('No mappings found in batch');
            return;
        }

        if (generation != null) {
            const currentGeneration = await getCurrentMappingGeneration(tenantId, campaignNumber);
            if (currentGeneration != null && currentGeneration !== generation) {
                logger.warn(`Dropping stale mapping batch ${batchNumber}/${totalBatches} (generation ${generation}, current ${currentGeneration}) for campaign ${campaignNumber}`);
                return;
            }
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
            promises.push(processResourceMappings(mappingGroups.resourceToBeMapped, boundaryToProjectId, tenantId, useruuid, requestInfo));
        }
        if (mappingGroups.facilityToBeMapped.length > 0) {
            promises.push(processFacilityMappings(mappingGroups.facilityToBeMapped, boundaryToProjectId, facilityMap, tenantId, useruuid, requestInfo));
        }
        if (mappingGroups.userToBeMapped.length > 0) {
            promises.push(processUserMappings(mappingGroups.userToBeMapped, boundaryToProjectId, userMap, tenantId, useruuid, requestInfo));
        }
        
        // Process toBeDeMapped
        if (mappingGroups.resourceToBeDeMapped.length > 0) {
            promises.push(processResourceDemappings(mappingGroups.resourceToBeDeMapped, tenantId, useruuid));
        }
        if (mappingGroups.facilityToBeDeMapped.length > 0) {
            promises.push(processFacilityDemappings(mappingGroups.facilityToBeDeMapped, boundaryToProjectId, facilityMap, tenantId, useruuid, requestInfo));
        }
        if (mappingGroups.userToBeDeMapped.length > 0) {
            promises.push(processUserDemappings(mappingGroups.userToBeDeMapped, boundaryToProjectId, userMap, tenantId, useruuid, requestInfo));
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

async function runBoundedCreates(items: any[], worker: (item: any) => Promise<void>): Promise<void> {
    const CONCURRENCY = config.mapping.createConcurrency;
    for (let i = 0; i < items.length; i += CONCURRENCY) {
        await Promise.all(items.slice(i, i + CONCURRENCY).map(worker));
    }
}

/**
 * Persists per-row failure reasons via direct SQL — the mapping persister contract
 * (Kafka message fields) stays unchanged; lastError lives only in the DB.
 */
async function persistMappingErrors(failures: { mapping: any; errorMessage: string }[], tenantId: string): Promise<void> {
    if (failures.length === 0) return;
    const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME, tenantId);
    const CHUNK_SIZE = config.mapping.persistBatchSize;
    for (let i = 0; i < failures.length; i += CHUNK_SIZE) {
        const chunk = failures.slice(i, i + CHUNK_SIZE);
        const values: string[] = [];
        const params: any[] = [];
        chunk.forEach(({ mapping, errorMessage }, idx) => {
            const base = idx * 5;
            values.push(`($${base + 1}, $${base + 2}, $${base + 3}, $${base + 4}, $${base + 5})`);
            params.push(errorMessage, mapping.campaignNumber, mapping.uniqueIdentifierForData, mapping.boundaryCode, mapping.type);
        });
        try {
            await executeQuery(
                `UPDATE ${tableName} t SET lastError = v.err
                 FROM (VALUES ${values.join(', ')}) AS v(err, campaignNumber, uniqueIdentifierForData, boundaryCode, type)
                 WHERE t.campaignNumber = v.campaignNumber
                   AND t.uniqueIdentifierForData = v.uniqueIdentifierForData
                   AND t.boundaryCode = v.boundaryCode
                   AND t.type = v.type`,
                params
            );
        } catch (error) {
            logger.warn(`Could not persist lastError for ${chunk.length} mappings: ${error}`);
        }
    }
}

/**
 * Persist data in batches to Kafka with chunking and wait time
 * Prevents Kafka message size limits and ensures proper persistence.
 * skipWait is for reconciler-path batches whose convergence is verified by the
 * observe loop anyway (e.g. pure pre-pass adoptions) — never skip elsewhere.
 */
async function persistInBatches(datas: any[], topic: string, tenantId: string, skipWait: boolean = false): Promise<void> {
    if (datas.length === 0) {
        return;
    }

    const BATCH_SIZE = config.mapping.persistBatchSize;
    for (let i = 0; i < datas.length; i += BATCH_SIZE) {
        const batch = datas.slice(i, i + BATCH_SIZE);
        await produceModifiedMessages({ datas: batch }, topic, tenantId);
    }
    if (skipWait) {
        return;
    }
    const waitTime = Math.max(5000, datas.length * 8);
    logger.info(`Waiting for ${waitTime} ms for persistence...`);
    await new Promise((res) => setTimeout(res, waitTime));
}

type MappingCreateAdapter = {
    type: string;
    searchExisting: (projectIds: string[], entityIds: string[], tenantId: string, requestInfo: RequestInfo) => Promise<Map<string, string>>;
    entityIdFor: (mapping: any) => string;
    create: (mapping: any, projectId: string, entityId: string, tenantId: string, requestInfo: RequestInfo) => Promise<string | undefined>;
};

/**
 * Shared map-direction driver: adopt-existing pre-pass, bounded creates, status
 * persistence, and lastError capture. Demap flows stay entity-specific — their
 * semantics differ per type.
 */
async function processToBeMappedGroup(
    mappings: any[],
    boundaryToProjectId: Record<string, string>,
    tenantId: string,
    requestInfo: RequestInfo,
    adapter: MappingCreateAdapter
): Promise<void> {
    logger.info(`Processing ${mappings.length} ${adapter.type} mappings`);

    const updateBatch: any[] = [];
    const failures: { mapping: any; errorMessage: string }[] = [];

    const projectIds: string[] = [];
    const entityIds: string[] = [];
    for (const mapping of mappings) {
        const projectId = boundaryToProjectId[mapping.boundaryCode];
        if (projectId) projectIds.push(projectId);
        try {
            entityIds.push(adapter.entityIdFor(mapping));
        } catch {
            // Unresolvable rows are excluded from the pre-pass and fail with a
            // descriptive error in the create stage below.
        }
    }

    const existing = await adapter.searchExisting(projectIds, entityIds, tenantId, requestInfo);

    const toCreate: any[] = [];
    for (const mapping of mappings) {
        const projectId = boundaryToProjectId[mapping.boundaryCode];
        let entityId: string | undefined;
        try {
            entityId = adapter.entityIdFor(mapping);
        } catch {
            entityId = undefined;
        }
        const existingId = projectId && entityId ? existing.get(`${entityId}|${projectId}`) : undefined;
        if (existingId) {
            mapping.status = mappingStatuses.mapped;
            mapping.mappingId = existingId;
            updateBatch.push(mapping);
        } else {
            toCreate.push(mapping);
        }
    }
    if (updateBatch.length > 0) {
        logger.info(`Adopted ${updateBatch.length} already-existing project ${adapter.type} mappings`);
    }

    await runBoundedCreates(toCreate, async (mapping) => {
        try {
            const projectId = boundaryToProjectId[mapping.boundaryCode];
            if (!projectId) {
                throw new Error(`Project not found for boundary ${mapping.boundaryCode}`);
            }
            const entityId = adapter.entityIdFor(mapping);

            const mappingId = await adapter.create(mapping, projectId, entityId, tenantId, requestInfo);

            mapping.status = mappingStatuses.mapped;
            if (mappingId) {
                mapping.mappingId = mappingId;
            }
            updateBatch.push(mapping);
        } catch (error) {
            logger.error(`Failed to create project ${adapter.type} mapping for ${mapping.uniqueIdentifierForData}:`, error);
            mapping.status = mappingStatuses.failed;
            updateBatch.push(mapping);
            failures.push({ mapping, errorMessage: error instanceof Error ? error.message : String(error) });
        }
    });

    await persistInBatches(updateBatch, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId, toCreate.length === 0);
    await persistMappingErrors(failures, tenantId);
}

/**
 * Process resource mappings
 */
async function processResourceMappings(
    mappings: any[],
    boundaryToProjectId: Record<string, string>,
    tenantId: string,
    useruuid: string,
    requestInfo: RequestInfo
): Promise<void> {
    await processToBeMappedGroup(mappings, boundaryToProjectId, tenantId, requestInfo, {
        type: 'resource',
        searchExisting: (projectIds, _entityIds, tenant, RequestInfo) =>
            searchProjectResourcesByProjects(projectIds, tenant, RequestInfo),
        entityIdFor: (mapping) => {
            if (!mapping?.uniqueIdentifierForData) {
                throw new Error(`Missing product variant ID for resource mapping`);
            }
            return mapping.uniqueIdentifierForData;
        },
        create: async (mapping, projectId, entityId, tenant, RequestInfo) => {
            const ProjectResource = {
                tenantId: tenant,
                projectId,
                resource: {
                    productVariantId: entityId,
                    type: "DRUG",
                    isBaseUnitVariant: false,
                },
                startDate: null,
                endDate: null,
            };
            const response = await createProjectResource({ RequestInfo, ProjectResource });
            return response?.ProjectResource?.id;
        },
    });
}

/**
 * Process facility mappings — project facilities live under the root tenant,
 * so both search and create use tenantId.split(".")[0] inside the adapter.
 */
async function processFacilityMappings(
    mappings: any[],
    boundaryToProjectId: Record<string, string>,
    facilityMap: Record<string, string>,
    tenantId: string,
    useruuid: string,
    requestInfo: RequestInfo
): Promise<void> {
    await processToBeMappedGroup(mappings, boundaryToProjectId, tenantId, requestInfo, {
        type: 'facility',
        searchExisting: (projectIds, entityIds, tenant, RequestInfo) =>
            searchProjectFacilitiesByProjects(projectIds, tenant.split(".")?.[0], RequestInfo, entityIds),
        entityIdFor: (mapping) => {
            const facilityId = facilityMap[mapping.uniqueIdentifierForData];
            if (!facilityId) {
                throw new Error(`Missing facility ID for ${mapping.uniqueIdentifierForData}`);
            }
            return facilityId;
        },
        create: async (mapping, projectId, entityId, tenant, RequestInfo) => {
            const ProjectFacility = {
                tenantId: tenant.split(".")?.[0],
                projectId,
                facilityId: entityId,
                startDate: null,
                endDate: null
            };
            const response = await createProjectFacility({ RequestInfo, ProjectFacility });
            return response?.ProjectFacility?.id;
        },
    });
}

/**
 * Process user mappings — rows whose user was never created (HRMS failure or
 * sheet-invalid row) are marked `skipped` before delegation so the campaign can
 * still complete; the shared driver handles the rest.
 */
async function processUserMappings(
    mappings: any[],
    boundaryToProjectId: Record<string, string>,
    userMap: Record<string, string>,
    tenantId: string,
    useruuid: string,
    requestInfo: RequestInfo
): Promise<void> {
    const skipped: any[] = [];
    const eligible: any[] = [];
    for (const mapping of mappings) {
        if (!userMap[mapping.uniqueIdentifierForData]) {
            mapping.status = mappingStatuses.skipped;
            skipped.push(mapping);
            logger.info(`Skipping user mapping for ${mapping.uniqueIdentifierForData} — user not created`);
        } else {
            eligible.push(mapping);
        }
    }
    if (skipped.length > 0) {
        await persistInBatches(skipped, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId, true);
    }
    if (eligible.length === 0) {
        return;
    }

    await processToBeMappedGroup(eligible, boundaryToProjectId, tenantId, requestInfo, {
        type: 'user',
        searchExisting: (projectIds, entityIds, tenant, RequestInfo) =>
            searchProjectStaffByProjects(projectIds, tenant, RequestInfo, entityIds),
        entityIdFor: (mapping) => {
            const userId = userMap[mapping.uniqueIdentifierForData];
            if (!userId) {
                throw new Error(`Missing user ID for ${mapping.uniqueIdentifierForData}`);
            }
            return userId;
        },
        create: async (mapping, projectId, entityId, tenant, RequestInfo) => {
            const ProjectStaff = {
                tenantId: tenant,
                projectId,
                userId: entityId,
                startDate: null,
                endDate: null,
            };
            const response = await createStaff({ RequestInfo, ProjectStaff });
            return response?.ProjectStaff?.id;
        },
    });
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

            mapping.status = mappingStatuses.deMapFailed;
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
    useruuid: string,
    requestInfo: RequestInfo
): Promise<void> {
    logger.info(`Processing ${mappings.length} facility demappings`);

    const RequestInfo = requestInfo;

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

            mapping.status = mappingStatuses.deMapFailed;
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
    useruuid: string,
    requestInfo: RequestInfo
): Promise<void> {
    logger.info(`Processing ${mappings.length} user demappings`);

    const RequestInfo = requestInfo;

    const deleteBatch: any[] = []; // Collect successful deletions
    const failedBatch: any[] = []; // Collect failed ones

    const promises = mappings.map(async (mapping) => {
        try {
            const projectId = boundaryToProjectId[mapping.boundaryCode];
            const userId = userMap[mapping.uniqueIdentifierForData];
            const mappingId = mapping.mappingId;

            // No user means there was nothing to demap server-side — drop the
            // local mapping row directly.
            if (!userId) {
                deleteBatch.push(mapping);
                return;
            }

            if (!projectId || !mappingId) {
                // Direct delete for invalid mappings
                deleteBatch.push(mapping); // Collect for batch deletion
                return;
            }

            await fetchAndDeleteProjectStaff(RequestInfo, tenantId, projectId, userId);
            deleteBatch.push(mapping); // Collect for batch deletion
        } catch (error) {
            logger.error(`Failed to demap user ${mapping.uniqueIdentifierForData}:`, error);

            mapping.status = mappingStatuses.deMapFailed;
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