import { RequestInfo, withUserInfo } from "../config/models/requestInfoSchema";
import { logger } from './logger';
import { httpRequest } from './request';
import { produceModifiedMessages } from '../kafka/Producer';
import { dataRowStatuses, sheetDataRowStatuses } from '../config/constants';
import { sendCampaignFailureMessage } from './campaignFailureHandler';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { DataTransformer } from './transFormUtil';
import { transformConfigs } from '../config/transformConfigs';
import { encrypt } from './cryptUtils';
import config from '../config';
import { WorkerData, createOrUpdateWorkers } from './workerRegistryUtils';

/** Shape of a row in the eg_cm_campaign_data table */
export interface CampaignRecord {
    status: string;
    data: Record<string, string>;
    uniqueIdAfterProcess?: string;
    campaignNumber?: string;
    uniqueIdentifier?: string;
    type?: string;
}

/**
 * Interface for user batch message
 */
interface UserBatchMessage {
    tenantId: string;
    campaignNumber: string;
    campaignId: string;
    parentCampaignId?: string;
    useruuid: string;
    userData: Record<string, CampaignRecord>;
    batchNumber: number;
    totalBatches: number;
    requestInfo: RequestInfo;
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
        
        if (!messageObject.requestInfo?.userInfo) {
            throw new Error(`User batch ${batchNumber}/${totalBatches} missing requestInfo.userInfo — cannot generate usernames via IDGen`);
        }

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
        const transformedUsers = await transformer.transform(userRowDatas, messageObject.requestInfo);
        
        logger.info(`Transformed ${transformedUsers.length} users`);
        
        // Create users via HRMS API
        const createResult = await createUsersViaHrmsApi(transformedUsers, useruuid, messageObject.requestInfo);

        // Build worker data for worker registry integration
        const workerDataList: WorkerData[] = [];

        // Process results and update campaign data
        let successCount = 0;
        let failureCount = 0;
        const updatedUsers: CampaignRecord[] = [];

        uniqueIdentifiers.forEach((uniqueIdentifier, index) => {
            const campaignRecord = userData[uniqueIdentifier];
            const phoneNumber = String(uniqueIdentifier);
            const serviceUuid = createResult.mobileToUserServiceMap[phoneNumber];
            const individualId = createResult.mobileToIndividualIdMap[phoneNumber];
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
                    "Password": password ? encrypt(password) : campaignRecord.data["Password"],
                    ...(individualId != null ? { "individualId": individualId } : {})
                };
                campaignRecord.uniqueIdAfterProcess = serviceUuid;
                updatedUsers.push(campaignRecord);
                successCount++;

                // Collect worker data from campaign record
                if (individualId) {
                    const recordData = campaignRecord.data;
                    workerDataList.push({
                        name: recordData["HCM_ADMIN_CONSOLE_USER_NAME"] || "",
                        payeePhoneNumber: String(recordData["HCM_ADMIN_CONSOLE_USER_PAYEE_PHONE_NUMBER"] || ""),
                        paymentProvider: recordData["HCM_ADMIN_CONSOLE_USER_PAYMENT_PROVIDER"] || "",
                        payeeName: recordData["HCM_ADMIN_CONSOLE_USER_PAYEE_NAME"] || "",
                        bankAccount: String(recordData["HCM_ADMIN_CONSOLE_USER_BANK_ACCOUNT"] || ""),
                        bankCode: String(recordData["HCM_ADMIN_CONSOLE_USER_BANK_CODE"] || ""),
                        beneficiaryCode: String(recordData["HCM_ADMIN_CONSOLE_USER_BENEFICIARY_CODE"] || ""),
                        id: recordData["HCM_ADMIN_CONSOLE_USER_WORKER_ID"] || "",
                        individualId,
                        tenantId,
                    });
                }

                logger.info(`User created: ${transformedUser?.user?.userName} with service UUID: ${serviceUuid}`);
            } else {
                // Failure - mark user as failed
                campaignRecord.status = dataRowStatuses.failed;
                updatedUsers.push(campaignRecord);
                failureCount++;

                logger.error(`Failed to create user with phone: ${phoneNumber}`);
            }
        });

        // Create/update workers in worker registry and capture worker IDs
        if (workerDataList.length > 0) {
            // Build individualId → campaignRecords map (multiple phones can map to same individualId)
            const individualIdToRecords = new Map<string, CampaignRecord[]>();
            for (const [phone, indId] of Object.entries(createResult.mobileToIndividualIdMap)) {
                const record = userData[phone];
                if (record) {
                    const list = individualIdToRecords.get(indId) || [];
                    list.push(record);
                    individualIdToRecords.set(indId, list);
                }
            }

            try {
                const workerRequestInfo = withUserInfo(messageObject.requestInfo, { tenantId });
                const { individualIdToWorkerIdMap, errors } = await createOrUpdateWorkers(workerDataList, workerRequestInfo);
                logger.info(`Worker registry integration completed for ${workerDataList.length} workers`);

                // Store only worker IDs back in campaign data — payee fields are fetched fresh
                // from worker registry at credential sheet generation time to avoid storing
                // potentially encrypted values that would corrupt subsequent updates.
                for (const workerData of workerDataList) {
                    const workerId = individualIdToWorkerIdMap.get(workerData.individualId);
                    if (workerId) {
                        const records = individualIdToRecords.get(workerData.individualId) || [];
                        for (const record of records) {
                            record.data["HCM_ADMIN_CONSOLE_USER_WORKER_ID"] = workerId;
                        }
                    }
                }

                // Mark rows as failed for workers that didn't get an ID back (partial failure)
                if (errors.length > 0) {
                    const errMsg = errors.join("; ");
                    logger.error("Worker registry integration had errors:", errMsg);
                    const processedIds = new Set<string>();
                    for (const w of workerDataList) {
                        if (processedIds.has(w.individualId)) continue;
                        processedIds.add(w.individualId);
                        if (!individualIdToWorkerIdMap.has(w.individualId)) {
                            const records = individualIdToRecords.get(w.individualId) || [];
                            const demoted = markWorkerRecordsFailed(records, errMsg);
                            successCount -= demoted;
                            failureCount += demoted;
                        }
                    }
                }
            } catch (workerError: unknown) {
                const errMsg = workerError instanceof Error ? workerError.message : String(workerError);
                logger.error("Worker registry integration failed:", errMsg);
                const processedIds = new Set<string>();
                for (const w of workerDataList) {
                    if (processedIds.has(w.individualId)) continue;
                    processedIds.add(w.individualId);
                    const records = individualIdToRecords.get(w.individualId) || [];
                    const demoted = markWorkerRecordsFailed(records, errMsg);
                    successCount -= demoted;
                    failureCount += demoted;
                }
            }
        }

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
            const batchError = new Error(`User batch processing failed: ${failureCount} out of ${uniqueIdentifiers.length} users failed in batch ${batchNumber}/${totalBatches}`);
            await sendCampaignFailureMessage(campaignId, tenantId, batchError);
        }
        
        logger.info(`=== USER BATCH PROCESSING COMPLETED ===`);
        
    } catch (error) {
        logger.error('Error in handleUserBatch:', error);
        const errMsg = error instanceof Error ? error.message : String(error);

        // Mark all non-completed rows in this batch as failed so the sheet reflects the error
        const allRecords = Object.values(messageObject.userData);
        const nonCompletedRecords = allRecords.filter(r => r.status !== dataRowStatuses.completed);
        if (nonCompletedRecords.length > 0) {
            for (const record of nonCompletedRecords) {
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
                logger.error("Failed to persist failed row statuses after batch error:", persistError);
            }
        }

        const batchError = new Error(`User batch processing error: ${errMsg}`);
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
    userUuid: string,
    requestInfo: RequestInfo
): Promise<{ mobileToUserServiceMap: Record<string, string>; mobileToIndividualIdMap: Record<string, string> }> {
    try {
        if (transformedUsers.length === 0) {
            return { mobileToUserServiceMap: {}, mobileToIndividualIdMap: {} };
        }

        const url = config.host.hrmsHost + config.paths.hrmsEmployeeCreate;
        const RequestInfo = requestInfo;

        const requestBody = {
            RequestInfo,
            Employees: transformedUsers,
        };

        logger.info(`Creating ${transformedUsers.length} employees via HRMS API`);

        const response = await httpRequest(url, requestBody);

        // Build mobile to service UUID and individualId mappings
        const mobileToUserServiceMap: Record<string, string> = {};
        const mobileToIndividualIdMap: Record<string, string> = {};
        if (response?.Employees) {
            for (const employee of response.Employees) {
                const mobileNumber = employee?.user?.mobileNumber;
                const serviceUuid = employee?.user?.userServiceUuid;
                const individualId = employee?.user?.uuid;
                if (mobileNumber && serviceUuid) {
                    mobileToUserServiceMap[String(mobileNumber)] = serviceUuid;
                }
                if (mobileNumber && individualId) {
                    mobileToIndividualIdMap[String(mobileNumber)] = individualId;
                }
            }
        }

        logger.info(`Successfully created ${Object.keys(mobileToUserServiceMap).length} users via HRMS`);

        return { mobileToUserServiceMap, mobileToIndividualIdMap };

    } catch (error: any) {
        logger.error("HRMS employee creation failed :: " + (error?.stack || error?.message || error));
        throw new Error(`HRMS API failed: ${error.message || error}`);
    }
}

/**
 * Marks all records as failed due to worker registry error.
 * Returns the number of records demoted so the caller can adjust success/failure counters.
 */
function markWorkerRecordsFailed(records: CampaignRecord[], errMsg: string): number {
    for (const record of records) {
        record.status = dataRowStatuses.failed;
        record.data["#status#"] = sheetDataRowStatuses.INVALID;
        record.data["#errorDetails#"] = errMsg;
    }
    return records.length;
}