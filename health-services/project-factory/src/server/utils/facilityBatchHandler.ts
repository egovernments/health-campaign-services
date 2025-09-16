import { logger } from './logger';
import { httpRequest } from './request';
import { produceModifiedMessages } from '../kafka/Producer';
import { dataRowStatuses } from '../config/constants';
import { defaultRequestInfo } from '../api/coreApis';
import { enrichAndPersistCampaignWithError } from './campaignUtils';
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
            parentCampaignId,
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
        
        // Transform facility data from campaign records
        const facilityRowDatas = uniqueIdentifiers.map(uniqueIdentifier => {
            const campaignRecord = facilityData[uniqueIdentifier];
            return campaignRecord?.data;
        });
        
        const transformConfig = transformConfigs?.["FacilityUnified"];
        if (!transformConfig) {
            throw new Error('Facility transform configuration not found');
        }
        
        transformConfig.metadata.tenantId = tenantId;
        transformConfig.metadata.hierarchy = campaignDetails.hierarchyType;
        const transformer = new DataTransformer(transformConfig);
        const transformedFacilities = await transformer.transform(facilityRowDatas);
        
        logger.info(`Transformed ${transformedFacilities.length} facilities`);
        
        // Create facilities in parallel using Promise.allSettled
        const facilityPromises = transformedFacilities.map((transformedItem : any) => {
            const facilityBody = transformedItem?.Facility;
            return createSingleFacilityFromBatch(facilityBody, useruuid);
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
        
        // If any facilities failed, mark campaign as failed
        if (failureCount > 0) {
            logger.error(`Facility batch processing had ${failureCount} failures. Marking campaign as failed.`);
            await markCampaignAsFailed(tenantId, campaignId, parentCampaignId, useruuid, {
                message: `Facility creation failed`,
                description: `${failureCount} out of ${uniqueIdentifiers.length} facilities failed to create in batch ${batchNumber}/${totalBatches}`
            });
        }
        
        logger.info(`=== FACILITY BATCH PROCESSING COMPLETED ===`);
        
    } catch (error) {
        logger.error('Error in handleFacilityBatch:', error);
        
        // Mark campaign as failed due to batch processing error
        await markCampaignAsFailed(
            messageObject.tenantId,
            messageObject.campaignId, 
            messageObject.parentCampaignId,
            messageObject.useruuid, 
            {
                message: `Facility batch processing error`,
                description: error instanceof Error ? error.message : String(error)
            }
        );
        
        throw error;
    }
}

/**
 * Create a single facility from batch processing
 */
async function createSingleFacilityFromBatch(
    facilityBody: any,
    userUuid: string
): Promise<any | null> {
    try {
        const facilityName = facilityBody?.name;
        
        if (!facilityName) {
            throw new Error('No facility name found in facility body');
        }
        
        // Create facility via API
        const response = await createFacilityOneByOne(facilityBody, userUuid);
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
 * Create facility via API call
 */
async function createFacilityOneByOne(facility: any, userUuid: string): Promise<any> {
    const url = config.host.facilityHost + config.paths.facilityCreate;
    
    const requestBody = {
        RequestInfo: JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo)),
        Facility: facility
    };
    requestBody.RequestInfo.userInfo.uuid = userUuid;
    
    try {
        const response = await httpRequest(url, requestBody);
        return response;
    } catch (error: any) {
        logger.error("Facility creation failed:", error);
        throw new Error(error);
    }
}

/**
 * Mark campaign as failed with error details
 */
async function markCampaignAsFailed(
    tenantId: string,
    campaignId: string, 
    parentCampaignId: string | undefined, 
    useruuid: string, 
    error: { message: string; description: string }
): Promise<void> {
    try {
        // Fetch campaign details
        const campaignResponse = await searchProjectTypeCampaignService({
            tenantId,
            ids: [campaignId]
        });
        const campaignDetails = campaignResponse?.CampaignDetails?.[0];
        
        if (!campaignDetails) {
            logger.error(`Campaign not found for ID: ${campaignId}`);
            return;
        }
        
        // Fetch parent campaign if exists
        let parentCampaign = null;
        if (parentCampaignId) {
            const parentResponse = await searchProjectTypeCampaignService({
                tenantId,
                ids: [parentCampaignId]
            });
            parentCampaign = parentResponse?.CampaignDetails?.[0];
        }
        
        const mockRequestBody = {
            CampaignDetails: [campaignDetails],
            RequestInfo: { userInfo: { uuid: useruuid } },
            parentCampaign: parentCampaign
        };
        
        logger.info(`Marking campaign ${campaignDetails.campaignNumber} as failed due to facility creation errors`);
        await enrichAndPersistCampaignWithError(mockRequestBody, error);
        
    } catch (markingError) {
        logger.error('Error marking campaign as failed:', markingError);
    }
}