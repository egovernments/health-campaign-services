import { RequestInfo } from "../config/models/requestInfoSchema";
import { logger } from './logger';
import { httpRequest } from './request';
import { produceModifiedMessages } from '../kafka/Producer';
import { dataRowStatuses, sheetDataRowStatuses, campaignStatuses, postAdditionalFieldsSchema } from '../config/constants';
import { sendCampaignFailureMessage } from './campaignFailureHandler';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { DataTransformer } from './transFormUtil';
import { transformConfigs } from '../config/transformConfigs';
import config from '../config';

/**
 * Interface for facility batch message
 */
interface FacilityBatchMessage {
    tenantId: string;
    campaignNumber: string;
    campaignId: string;
    parentCampaignId?: string;
    useruuid: string;
    facilityData: Record<string, any>; // { uniqueIdentifier: campaignRecord }
    batchNumber: number;
    totalBatches: number;
    requestInfo: RequestInfo;
}

/**
 * Handle facility batch creation from Kafka message
 */
export async function handleFacilityBatch(messageObject: FacilityBatchMessage): Promise<void> {
    try {
        const { 
            tenantId, 
            campaignNumber, 
            campaignId,
            useruuid, 
            facilityData,
            batchNumber, 
            totalBatches 
        } = messageObject;
        
        // Get unique identifiers from facility data keys
        const uniqueIdentifiers = Object.keys(facilityData);
        
        logger.info(`=== FACILITY BATCH PROCESSING STARTED ===`);
        logger.info(`Processing facility batch ${batchNumber}/${totalBatches}: ${uniqueIdentifiers.length} facilities`);
        logger.info(`Campaign: ${campaignNumber}, Tenant: ${tenantId}`);
        
        // Get campaign details for transformation
        const campaignResponse = await searchProjectTypeCampaignService({
            tenantId,
            ids: [campaignId]
        });
        const campaignDetails = campaignResponse?.CampaignDetails?.[0];
        
        if (!campaignDetails) {
            throw new Error(`Campaign not found for ID: ${campaignId}`);
        }

        if (campaignDetails.status === campaignStatuses.failed) {
            logger.warn(`Campaign ${campaignId} is already failed. Skipping facility batch ${batchNumber}/${totalBatches}`);
            return;
        }

        // Transform facility data from campaign records
        const facilityRowDatas = uniqueIdentifiers.map(uniqueIdentifier => {
            const campaignRecord = facilityData[uniqueIdentifier];
            return campaignRecord?.data;
        });
        
        const transformConfig = JSON.parse(JSON.stringify(transformConfigs?.["FacilityUnified"]));
        if (!transformConfig) {
            throw new Error('Facility transform configuration not found');
        }
        
        transformConfig.metadata.tenantId = tenantId;
        transformConfig.metadata.hierarchy = campaignDetails.hierarchyType;
        const transformer = new DataTransformer(transformConfig);
        const transformedFacilities = await transformer.transform(facilityRowDatas, messageObject.requestInfo);
        
        logger.info(`Transformed ${transformedFacilities.length} facilities`);
        
        // Create facilities in parallel using Promise.allSettled
        const facilityPromises = transformedFacilities.map((transformedItem : any) => {
            const facilityBody = transformedItem?.Facility;
            return createSingleFacilityFromBatch(facilityBody, useruuid, messageObject.requestInfo);
        });
        
        const batchResults = await Promise.allSettled(facilityPromises);
        
        // Process results and update campaign data
        let successCount = 0;
        let failureCount = 0;
        const updatedFacilities: any[] = [];
        
        batchResults.forEach((result, index) => {
            const uniqueIdentifier = uniqueIdentifiers[index];
            const campaignRecord = facilityData[uniqueIdentifier];
            
            if (result.status === 'fulfilled' && result.value) {
                // Success - facility created
                campaignRecord.status = dataRowStatuses.completed;
                campaignRecord.data = {
                    ...campaignRecord.data,
                    HCM_ADMIN_CONSOLE_FACILITY_CODE: result.value.id
                };
                campaignRecord.uniqueIdAfterProcess = result.value.id;
                updatedFacilities.push(campaignRecord);
                successCount++;
                
                logger.info(`✅ Facility created: ${result.value.name} with ID: ${result.value.id}`);
            } else {
                // Failure - mark facility as failed
                campaignRecord.status = dataRowStatuses.failed;
                updatedFacilities.push(campaignRecord);
                failureCount++;
                
                const facilityName = campaignRecord?.data?.HCM_ADMIN_CONSOLE_FACILITY_NAME;
                logger.error(`❌ Failed to create facility ${facilityName}:`, 
                    result.status === 'rejected' ? result.reason : 'Unknown error');
            }
        });
        
        logger.info(`Facility batch ${batchNumber}/${totalBatches} completed: ${successCount} success, ${failureCount} failed`);
        
        // Update all facilities in campaign data table via persister
        if (updatedFacilities.length > 0) {
            await produceModifiedMessages(
                { datas: updatedFacilities }, 
                config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, 
                tenantId
            );
            logger.info(`Updated ${updatedFacilities.length} facilities in campaign data via persister`);
        }
        
        // If any facilities failed, send campaign failure message
        if (failureCount > 0) {
            logger.error(`Facility batch processing had ${failureCount} failures. Sending campaign failure message.`);
            const batchError = new Error(`Facility creation failed: ${failureCount} out of ${uniqueIdentifiers.length} facilities failed to create in batch ${batchNumber}/${totalBatches}`);
            await sendCampaignFailureMessage(campaignId, tenantId, batchError);
        }
        
        logger.info(`=== FACILITY BATCH PROCESSING COMPLETED ===`);
        
    } catch (error) {
        logger.error('Error in handleFacilityBatch:', error);
        const errMsg = error instanceof Error ? error.message : String(error);

        // Mark all non-completed rows in this batch as failed so the sheet reflects the error
        const allRecords = Object.values(messageObject.facilityData);
        const nonCompletedRecords = allRecords.filter((r: any) => r.status !== dataRowStatuses.completed);
        if (nonCompletedRecords.length > 0) {
            for (const record of nonCompletedRecords as any[]) {
                record.status = dataRowStatuses.failed;
                record.data["#status#"] = sheetDataRowStatuses.INVALID;
                record.data["#errorDetails#"] = errMsg;
            }
            try {
                await produceModifiedMessages(
                    { datas: nonCompletedRecords },
                    config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC,
                    messageObject.tenantId
                );
            } catch (persistError) {
                logger.error("Failed to persist failed row statuses after facility batch error:", persistError);
            }
        }

        const batchError = new Error(`Facility batch processing error: ${errMsg}`);
        await sendCampaignFailureMessage(
            messageObject.campaignId,
            messageObject.tenantId,
            batchError
        );
    }
}

/**
 * Create a single facility from batch processing
 */
async function createSingleFacilityFromBatch(
    facilityBody: any,
    userUuid: string,
    requestInfo: RequestInfo
): Promise<any | null> {
    try {
        const facilityName = facilityBody?.name;

        if (!facilityName) {
            throw new Error('No facility name found in facility body');
        }

        // Posts carry a clientReferenceId: adopt an existing undeleted facility instead of
        // recreating it (idempotent on retry/redelivery). A deleted one must never be recreated.
        if (facilityBody?.clientReferenceId) {
            const existing = await findFacilityByClientReferenceId(facilityBody.clientReferenceId, facilityBody.tenantId, requestInfo);
            if (existing) {
                if (existing.isDeleted) {
                    throw new Error(`Post facility with clientReferenceId ${facilityBody.clientReferenceId} was deleted (Remove); it cannot be recreated`);
                }
                logger.info(`Adopting existing facility ${existing.id} for post clientReferenceId ${facilityBody.clientReferenceId}`);
                return existing;
            }
        }

        // Create facility via API
        const response = await createFacilityOneByOne(facilityBody, userUuid, requestInfo);
        const createdFacility = response?.Facility;
        
        if (createdFacility) {
            return createdFacility;
        } else {
            throw new Error('No facility returned from API');
        }
        
    } catch (error) {
        logger.error(`Error creating single facility from batch:`, error);
        throw error;
    }
}

/**
 * Searches the facility service by clientReferenceId (deleted included, so a deleted post facility
 * can be told apart from one that was never created).
 */
export async function findFacilityByClientReferenceId(clientReferenceId: string, tenantId: string, requestInfo: RequestInfo): Promise<any | null> {
    const url = config.host.facilityHost + config.paths.facilitySearch;
    const response = await httpRequest(
        url,
        { RequestInfo: requestInfo, Facility: { clientReferenceId: [clientReferenceId] } },
        { limit: 1, offset: 0, tenantId, includeDeleted: true }
    );
    const facilities = Array.isArray(response?.Facilities) ? response.Facilities : [];
    return facilities[0] ?? null;
}

/**
 * Soft-deletes one post's facility, found by clientReferenceId. Only deletes when it still carries
 * the Post marker; never a facility that is not found, already deleted, or not a post. Best effort —
 * every failure is logged and never thrown, so a campaign never fails because a post could not be
 * removed.
 */
async function removeOnePostFacility(clientReferenceId: string, campaignNumber: string, tenantId: string, requestInfo: RequestInfo): Promise<void> {
    let facility: any;
    try {
        facility = await findFacilityByClientReferenceId(clientReferenceId, tenantId, requestInfo);
    } catch (error) {
        logger.error(`Could not read post facility with clientReferenceId ${clientReferenceId} to delete it for campaign ${campaignNumber}`, error);
        return;
    }
    if (!facility) {
        logger.error(`Post facility with clientReferenceId ${clientReferenceId} not found; not deleted for campaign ${campaignNumber}`);
        return;
    }
    if (facility.isDeleted) {
        logger.info(`Post facility ${facility.id} is already deleted; skipping (campaign ${campaignNumber})`);
        return;
    }
    if (facility.additionalFields?.schema !== postAdditionalFieldsSchema.schema) {
        logger.error(`Facility ${facility.id} with clientReferenceId ${clientReferenceId} is not a post (additionalFields.schema is not "${postAdditionalFieldsSchema.schema}"); not deleted for campaign ${campaignNumber}`);
        return;
    }
    try {
        await httpRequest(config.host.facilityHost + config.paths.facilityDelete, {
            RequestInfo: requestInfo,
            Facility: facility
        });
        logger.info(`Post facility ${facility.id} deleted for campaign ${campaignNumber}`);
    } catch (error) {
        logger.error(`Could not delete post facility ${facility.id} for campaign ${campaignNumber}`, error);
    }
}

/**
 * Soft-deletes the facilities of posts set to Remove, so later campaigns stop pre-filling them.
 * Each post is found by clientReferenceId (existing facility search); best effort, in batches of
 * config.facility.searchBatchSize.
 */
export async function deleteRemovedPostFacilities(clientReferenceIds: string[], campaignNumber: string, tenantId: string, requestInfo: RequestInfo): Promise<void> {
    const unique = Array.from(new Set((clientReferenceIds || []).filter(Boolean)));
    if (unique.length === 0) return;

    const batchSize = config.facility.searchBatchSize;
    for (let i = 0; i < unique.length; i += batchSize) {
        const batch = unique.slice(i, i + batchSize);
        await Promise.all(batch.map(clientReferenceId =>
            removeOnePostFacility(clientReferenceId, campaignNumber, tenantId, requestInfo)
        ));
    }
}

/**
 * Create facility via API call
 */
async function createFacilityOneByOne(facility: any, userUuid: string, requestInfo: RequestInfo): Promise<any> {
    const url = config.host.facilityHost + config.paths.facilityCreate;

    const requestBody = {
        RequestInfo: requestInfo,
        Facility: facility
    };
    
    try {
        const response = await httpRequest(url, requestBody);
        return response;
    } catch (error: any) {
        logger.error("Facility creation failed:", error);
        throw new Error(error);
    }
}