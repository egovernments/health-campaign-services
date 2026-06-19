import { logger } from './logger';
import { httpRequest } from './request';
import { produceModifiedMessages } from '../kafka/Producer';
import { dataRowStatuses } from '../config/constants';
import { defaultRequestInfo } from '../api/coreApis';
import { sendCampaignFailureMessage } from './campaignFailureHandler';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { DataTransformer } from './transFormUtil';
import { transformConfigs } from '../config/transformConfigs';
import { encrypt } from './cryptUtils';
import config from '../config';

/**
 * Interface for user batch message
 */
interface UserBatchMessage {
    tenantId: string;
    campaignNumber: string;
    campaignId: string;
    parentCampaignId?: string;
    useruuid: string;
    userData: Record<string, any>; // { uniqueIdentifier: campaignRecord }
    batchNumber: number;
    totalBatches: number;
}

/**
 * Handle user batch creation from Kafka message
 */
export async function handleUserBatch(messageObject: UserBatchMessage): Promise<void> {
    try {
        const { 
            tenantId, 
            campaignNumber, 
            campaignId,
            useruuid, 
            userData,
            batchNumber, 
            totalBatches 
        } = messageObject;
        
        // Get unique identifiers from user data keys (phone numbers)
        const uniqueIdentifiers = Object.keys(userData);
        
        logger.info(`=== USER BATCH PROCESSING STARTED ===`);
        logger.info(`Processing user batch ${batchNumber}/${totalBatches}: ${uniqueIdentifiers.length} users`);
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
        
        // Transform user data from campaign records
        const userRowDatas = uniqueIdentifiers.map(uniqueIdentifier => {
            const campaignRecord = userData[uniqueIdentifier];
            return campaignRecord?.data;
        });
        
        const transformConfig = { ...transformConfigs?.["employeeHrmsUnified"] };
        if (!transformConfig) {
            throw new Error('User transform configuration not found');
        }
        
        transformConfig.metadata.tenantId = tenantId;
        transformConfig.metadata.hierarchy = campaignDetails.hierarchyType;
        const transformer = new DataTransformer(transformConfig);
        const transformedUsers = await transformer.transform(userRowDatas);
        
        logger.info(`Transformed ${transformedUsers.length} users`);
        
        // Create users via HRMS API
        const createResult = await createUsersViaHrmsApi(transformedUsers, useruuid);
        
        // Process results and update campaign data
        let successCount = 0;
        let failureCount = 0;
        const updatedUsers: any[] = [];
        
        uniqueIdentifiers.forEach((uniqueIdentifier, index) => {
            const campaignRecord = userData[uniqueIdentifier];
            const phoneNumber = String(uniqueIdentifier);
            const serviceUuid = createResult.mobileToUserServiceMap[phoneNumber];
            const transformedUser = transformedUsers[index];
            
            if (serviceUuid) {
                // Success - user created
                campaignRecord.status = dataRowStatuses.completed;
                const userName = transformedUser?.user?.userName;
                const password = transformedUser?.user?.password;
                campaignRecord.data = {
                    ...campaignRecord.data,
                    "UserService Uuids": serviceUuid,
                    "UserName": userName ? encrypt(userName) : campaignRecord.data["UserName"],
                    "Password": password ? encrypt(password) : campaignRecord.data["Password"]
                };
                campaignRecord.uniqueIdAfterProcess = serviceUuid;
                updatedUsers.push(campaignRecord);
                successCount++;
                
                logger.info(`✅ User created: ${transformedUser?.user?.userName} with service UUID: ${serviceUuid}`);
            } else {
                // Failure - mark user as failed
                campaignRecord.status = dataRowStatuses.failed;
                updatedUsers.push(campaignRecord);
                failureCount++;
                
                logger.error(`❌ Failed to create user with phone: ${phoneNumber}`);
            }
        });
        
        logger.info(`User batch ${batchNumber}/${totalBatches} completed: ${successCount} success, ${failureCount} failed`);
        
        // Update all users in campaign data table via persister
        if (updatedUsers.length > 0) {
            await produceModifiedMessages(
                { datas: updatedUsers }, 
                config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, 
                tenantId
            );
            logger.info(`Updated ${updatedUsers.length} users in campaign data via persister`);
        }
        
        // If any users failed, send campaign failure message
        if (failureCount > 0) {
            logger.error(`User batch processing had ${failureCount} failures. Sending campaign failure message.`);
            const batchError = new Error(`User creation failed: ${failureCount} out of ${uniqueIdentifiers.length} users failed to create in batch ${batchNumber}/${totalBatches}`);
            await sendCampaignFailureMessage(campaignId, tenantId, batchError);
        }
        
        logger.info(`=== USER BATCH PROCESSING COMPLETED ===`);
        
    } catch (error) {
        logger.error('Error in handleUserBatch:', error);
        
        // Send campaign failure message due to batch processing error
        const batchError = new Error(`User batch processing error: ${error instanceof Error ? error.message : String(error)}`);
        await sendCampaignFailureMessage(
            messageObject.campaignId,
            messageObject.tenantId,
            batchError
        );
    }
}

/**
 * Create users via HRMS API in batch
 */
async function createUsersViaHrmsApi(
    transformedUsers: any[], 
    userUuid: string
): Promise<{ mobileToUserServiceMap: Record<string, string> }> {
    try {
        if (transformedUsers.length === 0) {
            return { mobileToUserServiceMap: {} };
        }
        
        const url = config.host.hrmsHost + config.paths.hrmsEmployeeCreate;
        const RequestInfo = { ...defaultRequestInfo?.RequestInfo };
        RequestInfo.userInfo.uuid = userUuid;
        
        const requestBody = {
            RequestInfo,
            Employees: transformedUsers,
        };
        
        logger.info(`Creating ${transformedUsers.length} employees via HRMS API`);
        
        const response = await httpRequest(url, requestBody);
        
        // Build mobile to service UUID mapping
        const mobileToUserServiceMap: Record<string, string> = {};
        if (response?.Employees) {
            for (const employee of response.Employees) {
                const mobileNumber = employee?.user?.mobileNumber;
                const serviceUuid = employee?.user?.userServiceUuid;
                if (mobileNumber && serviceUuid) {
                    mobileToUserServiceMap[String(mobileNumber)] = serviceUuid;
                }
            }
        }
        
        logger.info(`Successfully created ${Object.keys(mobileToUserServiceMap).length} users via HRMS`);
        
        return { mobileToUserServiceMap };
        
    } catch (error: any) {
        logger.error("HRMS employee creation failed:", error);
        throw new Error(`HRMS API failed: ${error.message || error}`);
    }
}