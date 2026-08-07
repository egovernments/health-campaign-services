import { RequestInfo, withUserInfo } from "../config/models/requestInfoSchema";
import { logger } from './logger';
import { httpRequest } from './request';
import { produceModifiedMessages } from '../kafka/Producer';
import { dataRowStatuses, sheetDataRowStatuses, campaignStatuses, campaignDataRowFields, userDataFields, userCredentialFields, errorCodes } from '../config/constants';
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
 * Mark an already-existing (adopted) user row as terminally completed — symmetric with the
 * newly-created path — so a retry of a partially-created campaign converges (pendingRows → 0)
 * instead of leaving adopted rows stuck 'pending'.
 */
export function markAdoptedUserRecordCompleted(campaignRecord: CampaignRecord, serviceUuid: string, userName?: string): void {
    campaignRecord.status = dataRowStatuses.completed;
    campaignRecord.data = {
        ...campaignRecord.data,
        [userCredentialFields.userServiceUuids]: serviceUuid,
        [campaignDataRowFields.status]: sheetDataRowStatuses.EXISTING,
        // Adopted users have no campaign-generated credentials; surface their real login id
        // (authoritative from egov-user) on the credential sheet. Password stays blank (unrecoverable).
        ...(userName ? { [userCredentialFields.userName]: userName } : {}),
    };
    campaignRecord.uniqueIdAfterProcess = serviceUuid;
}

/**
 * Reconcile-selector: from a set of still-`pending` user rows and a phone→existing-HRMS-user map,
 * mark every row whose user already exists as terminally completed and return only those rows.
 * Pure (no I/O) so the convergence rule is unit-testable; the DB fetch / HRMS search / persist
 * are orchestrated by the caller (see reconcilePendingUserRows in processingResultHandler).
 */
export function selectReconcilableUserRows(
    pendingRows: CampaignRecord[],
    existingByPhone: Record<string, ExistingHrmsUser>
): CampaignRecord[] {
    const reconciled: CampaignRecord[] = [];
    for (const row of pendingRows) {
        const existing = existingByPhone[String(row?.uniqueIdentifier ?? '')];
        if (existing?.serviceUuid) {
            markAdoptedUserRecordCompleted(row, existing.serviceUuid, existing.userName);
            reconciled.push(row);
        }
    }
    return reconciled;
}

/**
 * Kafka handler for one user batch: idempotently creates HRMS users (adopting existing ones), then gates and
 * creates worker-registry records, marking per-row status. Failures here are non-blocking for the campaign.
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

        // userData is keyed by phone number
        const uniqueIdentifiers = Object.keys(userData);

        logger.info(`=== USER BATCH PROCESSING STARTED ===`);
        logger.info(`Processing user batch ${batchNumber}/${totalBatches}: ${uniqueIdentifiers.length} users`);
        logger.info(`Campaign: ${campaignNumber}, Tenant: ${tenantId}`);

        const campaignResponse = await searchProjectTypeCampaignService({
            tenantId,
            ids: [campaignId]
        });
        const campaignDetails = campaignResponse?.CampaignDetails?.[0];
        
        if (!campaignDetails) {
            throw new Error(`Campaign not found for ID: ${campaignId}`);
        }

        if (campaignDetails.status === campaignStatuses.failed) {
            logger.warn(`Campaign ${campaignId} is already failed. Skipping user batch ${batchNumber}/${totalBatches}`);
            return;
        }

        // Defence-in-depth: even though the dispatcher filters out sheet-invalid
        // rows before publishing, re-check here so HRMS is never called for a
        // row tagged INVALID by excel-ingestion regardless of how the batch was
        // produced.
        const sheetInvalidIdentifiers = uniqueIdentifiers.filter(
            id => userData[id]?.data?.[campaignDataRowFields.status] === sheetDataRowStatuses.INVALID
        );
        if (sheetInvalidIdentifiers.length > 0) {
            logger.warn(
                `Skipping HRMS create for ${sheetInvalidIdentifiers.length} sheet-invalid row(s) in batch ${batchNumber}/${totalBatches}: ${JSON.stringify(sheetInvalidIdentifiers)}`
            );
            sheetInvalidIdentifiers.forEach(id => {
                const record = userData[id];
                if (record) {
                    record.status = dataRowStatuses.failed;
                }
            });
        }
        const eligibleIdentifiers = uniqueIdentifiers.filter(
            id => userData[id]?.data?.[campaignDataRowFields.status] !== sheetDataRowStatuses.INVALID
        );

        // Check which users already exist in HRMS — skip those to stay idempotent on retry
        const alreadyExistingMap = await fetchExistingUsersByPhone(eligibleIdentifiers, tenantId, messageObject.requestInfo);
        const phoneNumbersNeedingCreation = eligibleIdentifiers.filter(p => !alreadyExistingMap[String(p)]);
        const retryRowCount = uniqueIdentifiers.filter(id => userData[id]?.status === dataRowStatuses.failed).length;
        logger.info(`HRMS pre-check: ${Object.keys(alreadyExistingMap).length}/${eligibleIdentifiers.length} phone(s) already in HRMS; ${phoneNumbersNeedingCreation.length} need HRMS create; ${retryRowCount} of these are retries of previously-failed rows`);

        // Mark already-existing users as completed immediately without calling HRMS.
        // If the phone exists under a different name in HRMS, log the discrepancy
        // and treat HRMS as source of truth (do not overwrite HRMS).
        uniqueIdentifiers.forEach(uniqueIdentifier => {
            const existing = alreadyExistingMap[String(uniqueIdentifier)];
            if (!existing) return;

            const campaignRecord = userData[uniqueIdentifier];
            const sheetName = normalizeNameForCompare(campaignRecord?.data?.[userDataFields.name]);
            const hrmsName = normalizeNameForCompare(existing.existingName);

            const wasRetry = campaignRecord.status === dataRowStatuses.failed;
            markAdoptedUserRecordCompleted(campaignRecord, existing.serviceUuid, existing.userName);

            if (sheetName && hrmsName && sheetName !== hrmsName) {
                const reason = `${errorCodes.hrmsPhoneReusedDifferentUser}: phone exists in HRMS as '${existing.existingName}' but sheet provided '${campaignRecord?.data?.[userDataFields.name] ?? ''}'. HRMS user kept as source of truth.`;
                logger.warn(`Phone ${uniqueIdentifier} → ${reason}`);
                // Surface the discrepancy on the row so operators can see it on the credential sheet's error column,
                // without flipping status back to failed (HRMS user is still valid for this row's purposes).
                campaignRecord.data[campaignDataRowFields.errorDetails] = reason;
            } else {
                logger.info(`Row for phone ${uniqueIdentifier} already in HRMS (serviceUuid ${existing.serviceUuid}) — marking completed without create${wasRetry ? ' (retry=true)' : ''}`);
            }
        });

        // Transform only the rows that need creation (existing users already handled above)
        const userRowDatas = phoneNumbersNeedingCreation.map(uniqueIdentifier => {
            const campaignRecord = userData[uniqueIdentifier];
            return campaignRecord?.data;
        });

        const transformConfig = JSON.parse(JSON.stringify(transformConfigs?.["employeeHrmsUnified"]));
        if (!transformConfig) {
            throw new Error('User transform configuration not found');
        }

        transformConfig.metadata.tenantId = tenantId;
        transformConfig.metadata.hierarchy = campaignDetails.hierarchyType;
        const transformer = new DataTransformer(transformConfig);
        const transformedUsers = await transformer.transform(userRowDatas, messageObject.requestInfo);

        logger.info(`Transformed ${transformedUsers.length} users (${uniqueIdentifiers.length - phoneNumbersNeedingCreation.length} already existed)`);

        const createResult = await createUsersViaHrmsApi(transformedUsers, useruuid, messageObject.requestInfo);

        // Per-user failures from the HRMS per-user fallback are handed back via a global (see createUsersViaHrmsApi)
        const failedHrmsUsers: Record<string, string> = (global as any).__hrmsFailedUsers || {};
        delete (global as any).__hrmsFailedUsers;

        // Merge already-existing users into the result so downstream logic treats them as created
        for (const [phone, existing] of Object.entries(alreadyExistingMap)) {
            createResult.mobileToUserServiceMap[phone] = existing.serviceUuid;
            if (existing.individualId) {
                createResult.mobileToIndividualIdMap[phone] = existing.individualId;
            }
        }

        // Build phone → transformedUser map indexed by phoneNumbersNeedingCreation order.
        // transformedUsers[i] aligns with phoneNumbersNeedingCreation[i], NOT uniqueIdentifiers[i],
        // so we must not use the outer forEach index directly.
        const phoneToTransformedUser = new Map<string, any>();
        phoneNumbersNeedingCreation.forEach((phone, i) => {
            phoneToTransformedUser.set(phone, transformedUsers[i]);
        });

        const workerDataList: WorkerData[] = [];

        let successCount = 0;
        let failureCount = 0;
        const updatedUsers: CampaignRecord[] = [];

        uniqueIdentifiers.forEach((uniqueIdentifier) => {
            const campaignRecord = userData[uniqueIdentifier];
            const phoneNumber = String(uniqueIdentifier);
            const serviceUuid = createResult.mobileToUserServiceMap[phoneNumber];
            const individualId = createResult.mobileToIndividualIdMap[phoneNumber];
            const transformedUser = phoneToTransformedUser.get(phoneNumber);
            const hrmsError = failedHrmsUsers[phoneNumber];

            const wasRetry = campaignRecord.status === dataRowStatuses.failed;

            if (hrmsError) {
                campaignRecord.status = dataRowStatuses.failed;
                campaignRecord.data[campaignDataRowFields.status] = sheetDataRowStatuses.FAILED;
                campaignRecord.data[campaignDataRowFields.errorDetails] = hrmsError;
                updatedUsers.push(campaignRecord);
                failureCount++;
                logger.warn(`HRMS create failed for phone ${phoneNumber}${wasRetry ? ' (retry=true)' : ''}: ${hrmsError}`);
            } else if (serviceUuid) {
                campaignRecord.status = dataRowStatuses.completed;
                const userName = transformedUser?.user?.userName;
                const password = transformedUser?.user?.password;
                campaignRecord.data = {
                    ...campaignRecord.data,
                    [userCredentialFields.userServiceUuids]: serviceUuid,
                    [userCredentialFields.userName]: userName ? encrypt(userName) : campaignRecord.data[userCredentialFields.userName],
                    [userCredentialFields.password]: password ? encrypt(password) : campaignRecord.data[userCredentialFields.password],
                    // Preserve EXISTING for adopted rows; only newly-created rows are CREATED.
                    [campaignDataRowFields.status]: campaignRecord.data[campaignDataRowFields.status] === sheetDataRowStatuses.EXISTING
                        ? sheetDataRowStatuses.EXISTING
                        : sheetDataRowStatuses.CREATED
                };
                campaignRecord.uniqueIdAfterProcess = serviceUuid;
                updatedUsers.push(campaignRecord);
                successCount++;
                if (wasRetry) {
                    logger.info(`HRMS create succeeded on retry for phone ${phoneNumber}: serviceUuid ${serviceUuid}`);
                }

                if (individualId) {
                    const recordData = campaignRecord.data;
                    workerDataList.push({
                        name: recordData[userDataFields.name] || "",
                        payeePhoneNumber: String(recordData[userDataFields.payeePhoneNumber] || ""),
                        paymentProvider: recordData[userDataFields.paymentProvider] || "",
                        payeeName: recordData[userDataFields.payeeName] || "",
                        bankAccount: String(recordData[userDataFields.bankAccount] || ""),
                        bankCode: String(recordData[userDataFields.bankCode] || ""),
                        beneficiaryCode: String(recordData[userDataFields.beneficiaryCode] || ""),
                        id: recordData[userDataFields.workerId] || "",
                        individualId,
                        tenantId,
                    });
                }

                logger.info(`User created: ${transformedUser?.user?.userName} with service UUID: ${serviceUuid}`);
            } else {
                // HRMS batch returned without a serviceUuid for this user — treat
                // as an HRMS-side failure so the errors worksheet shows FAILED (not
                // INVALID) and the retry gate allows re-creation on the next upload.
                campaignRecord.status = dataRowStatuses.failed;
                campaignRecord.data[campaignDataRowFields.status] = sheetDataRowStatuses.FAILED;
                campaignRecord.data[campaignDataRowFields.errorDetails] = "HRMS did not return a service UUID for this user";
                updatedUsers.push(campaignRecord);
                failureCount++;

                logger.error(`Failed to create user with phone: ${phoneNumber}${wasRetry ? ' (retry=true)' : ''}`);
            }
        });

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

            // Consistency gate runs outside the try: waitForIndividualsSearchable is fail-open (never throws),
            // so creatable/deferred are in scope for the catch below — deferred rows are already demoted and
            // must not be double-counted if the create throws.
            const workerRequestInfo = withUserInfo(messageObject.requestInfo, { tenantId });
            const { missing } = await waitForIndividualsSearchable(workerDataList.map(w => w.individualId), tenantId, workerRequestInfo);
            const { creatable, deferred } = partitionWorkersByIndividualSearchability(workerDataList, missing);

            // Defer workers whose individual is not yet searchable — never send them to worker-registry
            // (worker/v1/bulk/_create returns terminal NON_RECOVERABLE INDIVIDUAL_NOT_FOUND); mark them
            // retryable so a later upload/retry re-attempts them once individual indexing catches up.
            if (deferred.length > 0) {
                const deferMsg = `Individual not searchable after ${config.user.individualConsistencyMaxPollAttempts} consistency poll attempt(s); worker creation deferred for retry`;
                const deferredIds = new Set<string>();
                for (const w of deferred) {
                    if (deferredIds.has(w.individualId)) continue;
                    deferredIds.add(w.individualId);
                    const records = individualIdToRecords.get(w.individualId) || [];
                    const demoted = markWorkerRecordsFailed(records, deferMsg);
                    successCount -= demoted;
                    failureCount += demoted;
                }
                logger.warn(`Deferred ${deferredIds.size} worker(s) for retry — individual(s) not yet searchable`);
            }

            try {
                const { individualIdToWorkerIdMap, errors } = creatable.length > 0
                    ? await createOrUpdateWorkers(creatable, workerRequestInfo)
                    : { individualIdToWorkerIdMap: new Map<string, string>(), errors: [] as string[] };
                logger.info(`Worker registry integration completed for ${creatable.length} worker(s) (${deferred.length} deferred)`);

                // Store only worker IDs back in campaign data — payee fields are fetched fresh
                // from worker registry at credential sheet generation time to avoid storing
                // potentially encrypted values that would corrupt subsequent updates.
                for (const workerData of creatable) {
                    const workerId = individualIdToWorkerIdMap.get(workerData.individualId);
                    if (workerId) {
                        const records = individualIdToRecords.get(workerData.individualId) || [];
                        for (const record of records) {
                            record.data[userDataFields.workerId] = workerId;
                        }
                    }
                }

                // Mark rows as failed for workers that didn't get an ID back (partial failure)
                if (errors.length > 0) {
                    const errMsg = errors.join("; ");
                    logger.error("Worker registry integration had errors:", errMsg);
                    const processedIds = new Set<string>();
                    for (const w of creatable) {
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
                // Only the creatable set was sent to worker-registry; deferred rows are already demoted above.
                for (const w of creatable) {
                    if (processedIds.has(w.individualId)) continue;
                    processedIds.add(w.individualId);
                    const records = individualIdToRecords.get(w.individualId) || [];
                    const demoted = markWorkerRecordsFailed(records, errMsg);
                    successCount -= demoted;
                    failureCount += demoted;
                }
            }
        }

        logger.info(`User batch ${batchNumber}/${totalBatches} completed: ${successCount} success, ${failureCount} failed (failures at HRMS/user level only — not campaign-blocking)`);

        // Update all users in campaign data table via persister
        if (updatedUsers.length > 0) {
            try {
                await produceModifiedMessages(
                    { datas: updatedUsers },
                    config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC,
                    tenantId
                );
                logger.info(`Updated ${updatedUsers.length} users in campaign data via persister`);
            } catch (kafkaError) {
                logger.error(`Kafka publish failed while updating user batch results. Sending campaign failure message.`);
                const systemError = new Error(`Failed to persist user batch results: ${kafkaError instanceof Error ? kafkaError.message : String(kafkaError)}`);
                await sendCampaignFailureMessage(campaignId, tenantId, systemError);
                throw kafkaError;
            }
        }

        // Per-user HRMS failures do NOT trigger campaign failure
        // Only system-level errors (Kafka, etc.) cause campaign failure
        // Campaign will be marked as 'created' as long as processing completes
        
        logger.info(`=== USER BATCH PROCESSING COMPLETED ===`);
        
    } catch (error) {
        logger.error('Error in handleUserBatch:', error);
        const errMsg = error instanceof Error ? error.message : String(error);

        // Mark all non-completed rows in this batch as failed so the credential
        // sheet reflects the error. These are HRMS-side failures, distinct from
        // sheet-validation INVALID rows — keep them tagged FAILED.
        const allRecords = Object.values(messageObject.userData);
        const nonCompletedRecords = allRecords.filter(r => r.status !== dataRowStatuses.completed);
        if (nonCompletedRecords.length > 0) {
            for (const record of nonCompletedRecords) {
                record.status = dataRowStatuses.failed;
                record.data[campaignDataRowFields.status] = sheetDataRowStatuses.FAILED;
                record.data[campaignDataRowFields.errorDetails] = errMsg;
            }
            try {
                await produceModifiedMessages(
                    { datas: nonCompletedRecords },
                    config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC,
                    messageObject.tenantId
                );
            } catch (persistError) {
                // Persistence failed — without this update the campaign monitor
                // would never observe the failed rows and would hang. Escalate.
                logger.error("Failed to persist failed row statuses after batch error:", persistError);
                await sendCampaignFailureMessage(
                    messageObject.campaignId,
                    messageObject.tenantId,
                    new Error(`User batch persistence failed after error '${errMsg}': ${persistError instanceof Error ? persistError.message : String(persistError)}`)
                );
                return;
            }
        }

        // User-batch failures are non-blocking by policy — the per-row failures
        // are now visible to the data/mapping monitors, which apply non-blocking
        // semantics for user-type rows. Do NOT mark the campaign as failed.
        logger.error(`User batch ${messageObject.batchNumber}/${messageObject.totalBatches} failed (non-blocking): ${errMsg}`);
    }
}

/**
 * Create users via HRMS API in batch, with per-user fallback on failure
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

        logger.info(`Creating ${transformedUsers.length} employees via HRMS API (batch)`);

        try {
            // Try batch creation first
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

            logger.info(`Successfully created ${Object.keys(mobileToUserServiceMap).length} users via HRMS (batch)`);

            return { mobileToUserServiceMap, mobileToIndividualIdMap };

        } catch (batchError: any) {
            // Batch failed, try per-user fallback
            logger.warn(`HRMS batch failed; falling back to per-user calls for batch of ${transformedUsers.length}`);

            const mobileToUserServiceMap: Record<string, string> = {};
            const mobileToIndividualIdMap: Record<string, string> = {};

            // One per-user create. Kept as a thunk (not pre-mapped) so requests are
            // only started when their chunk is awaited — an unbounded
            // Promise.allSettled over the whole batch caused a thundering herd that
            // exhausted the downstream DB connection pool ("Failed to obtain JDBC
            // Connection") on large uploads.
            const maxRetries = config.user.hrmsFallbackMaxRetries >= 0 ? config.user.hrmsFallbackMaxRetries : 2;
            const backoffMs = config.user.hrmsFallbackBackoffMs > 0 ? config.user.hrmsFallbackBackoffMs : 500;
            const createOne = async (transformedUser: any): Promise<{ success: boolean; mobileNumber: string; error?: string }> => {
                const mobileNumber = String(transformedUser?.user?.mobileNumber ?? '');
                const singleRequestBody = { RequestInfo, Employees: [transformedUser] };
                let lastError = 'Unknown error';
                // Retry transient downstream failures (e.g. "Failed to obtain JDBC
                // Connection") with exponential backoff + jitter; permanent errors
                // (already-exists, validation) are returned immediately.
                for (let attempt = 0; attempt <= maxRetries; attempt++) {
                    try {
                        const response = await httpRequest(url, singleRequestBody);
                        if (response?.Employees?.[0]) {
                            const employee = response.Employees[0];
                            const serviceUuid = employee?.user?.userServiceUuid;
                            const individualId = employee?.user?.uuid;
                            if (mobileNumber && serviceUuid) mobileToUserServiceMap[mobileNumber] = serviceUuid;
                            if (mobileNumber && individualId) mobileToIndividualIdMap[mobileNumber] = individualId;
                            return { success: true, mobileNumber };
                        }
                        return { success: false, mobileNumber, error: 'No employee data in response' };
                    } catch (perUserError: any) {
                        lastError = extractHrmsErrorMessage(perUserError);
                        if (attempt < maxRetries && isRetryableHrmsError(lastError)) {
                            const delay = backoffMs * (2 ** attempt) + Math.floor((attempt + 1) * 37);
                            logger.warn(`Per-user create for ${mobileNumber} hit a transient error (attempt ${attempt + 1}/${maxRetries + 1}), backing off ${delay}ms: ${lastError}`);
                            await sleep(delay);
                            continue;
                        }
                        return { success: false, mobileNumber, error: lastError };
                    }
                }
                return { success: false, mobileNumber, error: lastError };
            };

            // Run per-user creates in bounded concurrency windows (never the whole batch
            // at once), with a throttle pause between windows to smooth downstream load.
            const fallbackConcurrency = config.user.hrmsFallbackConcurrency > 0 ? config.user.hrmsFallbackConcurrency : 5;
            const windowDelayMs = config.user.hrmsFallbackWindowDelayMs >= 0 ? config.user.hrmsFallbackWindowDelayMs : 200;
            const results: PromiseSettledResult<{ success: boolean; mobileNumber: string; error?: string }>[] = [];
            for (let i = 0; i < transformedUsers.length; i += fallbackConcurrency) {
                const window = transformedUsers.slice(i, i + fallbackConcurrency);
                const settled = await Promise.allSettled(window.map((tu) => createOne(tu)));
                results.push(...settled);
                if (windowDelayMs > 0 && i + fallbackConcurrency < transformedUsers.length) {
                    await sleep(windowDelayMs);
                }
            }

            // Log outcomes and track failures
            let successCount = 0;
            let failureCount = 0;
            const failedMobiles: Record<string, string> = {};

            for (const result of results) {
                if (result.status === 'fulfilled') {
                    const outcome = result.value;
                    if (outcome?.success) {
                        successCount++;
                        logger.debug(`Per-user creation succeeded for ${outcome.mobileNumber}`);
                    } else if (outcome?.error) {
                        failureCount++;
                        failedMobiles[outcome.mobileNumber] = outcome.error;
                        logger.warn(`Per-user creation failed for ${outcome.mobileNumber}: ${outcome.error}`);
                    }
                } else {
                    failureCount++;
                    logger.error(`Per-user promise rejected:`, result.reason);
                }
            }

            logger.info(`Per-user fallback complete: ${successCount} succeeded, ${failureCount} failed`);

            // Store failed mobile numbers and errors in global state for caller to process
            (global as any).__hrmsFailedUsers = failedMobiles;

            return { mobileToUserServiceMap, mobileToIndividualIdMap };
        }

    } catch (error: any) {
        logger.error("HRMS employee creation failed :: " + (error?.stack || error?.message || error));
        throw new Error(`HRMS API failed: ${error.message || error}`);
    }
}

/** Promise-based delay used for fallback throttling and backoff. */
function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * True when an HRMS create error is transient/overload-related and worth retrying
 * (e.g. DB connection-pool exhaustion, timeouts, 5xx). Permanent errors like
 * already-exists / validation are NOT retryable.
 */
function isRetryableHrmsError(message: string): boolean {
    const m = String(message ?? '').toLowerCase();
    if (m.includes('already exist') || m.includes('duplicate') || m.includes('conflict')) return false;
    return m.includes('failed to obtain jdbc')
        || m.includes('jdbc')
        || m.includes('database_error')
        || m.includes('user creation failed at the user service')
        || m.includes('timeout')
        || m.includes('econnreset')
        || m.includes('econnrefused')
        || m.includes('connection refused')
        || m.includes('socket hang up')
        || / 5\d\d\b/.test(m)
        || m.includes('http 502') || m.includes('http 503') || m.includes('http 504');
}

/**
 * Extract concise error message from HRMS error response
 */
function extractHrmsErrorMessage(error: any): string {
    if (error?.response?.data?.errorDetails?.[0]?.message) {
        return error.response.data.errorDetails[0].message;
    }
    if (error?.response?.data?.error?.message) {
        return error.response.data.error.message;
    }
    if (error?.response?.status === 400) {
        return `HTTP 400: ${error.response.data?.message || 'Bad Request'}`;
    }
    if (error?.response?.status === 409) {
        return `HTTP 409: Conflict (username or email already exists)`;
    }
    if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') {
        return 'Request timeout';
    }
    if (error?.message) {
        return error.message;
    }
    return 'Unknown error';
}

/**
 * Marks all records as failed due to worker registry error.
 * Returns the number of records demoted so the caller can adjust success/failure counters.
 */
function markWorkerRecordsFailed(records: CampaignRecord[], errMsg: string): number {
    for (const record of records) {
        record.status = dataRowStatuses.failed;
        // Worker-registry failures are an HRMS-side error, not a sheet-validation
        // error — tag as FAILED so the errors worksheet shows the correct status
        // and so the HRMS retry gate (data["#status#"] !== INVALID) doesn't block
        // re-creation on a future upload.
        record.data["#status#"] = sheetDataRowStatuses.FAILED;
        record.data["#errorDetails#"] = errMsg;
    }
    return records.length;
}

/**
 * Shape returned by the HRMS idempotency pre-check.
 * existingName is the constructed full name from Individual.name parts
 * (givenName + otherNames + familyName, blanks trimmed) — used to detect
 * phone-reused-for-different-user mismatches.
 */
export interface ExistingHrmsUser {
    serviceUuid: string;
    individualId: string;
    existingName: string;
    /** Existing egov-user login username, resolved by serviceUuid — shown on the credential sheet
     *  for adopted users (their password is unrecoverable, but the login id is). */
    userName?: string;
}

/**
 * Resolve existing egov-user login usernames by user uuid. Adopted (already-in-HRMS) users have no
 * campaign-generated credentials, so we surface their real login id on the credential sheet.
 * Non-fatal: a failed lookup just leaves the username blank.
 */
export async function fetchUserNamesByUuid(
    uuids: string[],
    tenantId: string,
    requestInfo: RequestInfo
): Promise<Record<string, string>> {
    const result: Record<string, string> = {};
    const unique = Array.from(new Set(uuids.filter(Boolean)));
    if (unique.length === 0) return result;

    const batchSize = config.user.individualSearchBatchSize;
    for (let i = 0; i < unique.length; i += batchSize) {
        const batch = unique.slice(i, i + batchSize);
        try {
            const response = await httpRequest(
                config.host.userHost + config.paths.userSearch,
                { RequestInfo: requestInfo, tenantId, uuid: batch },
                { tenantId }
            );
            for (const user of response?.user ?? []) {
                if (user?.uuid && user?.userName) result[String(user.uuid)] = String(user.userName);
            }
        } catch (err) {
            logger.warn(`Existing-username lookup failed for batch starting at index ${i}: ${err}`);
        }
    }
    return result;
}

/**
 * Build a full-name string from the Individual service's Name sub-object so
 * we can compare against the sheet's single-column HCM_ADMIN_CONSOLE_USER_NAME.
 */
function buildFullNameFromIndividual(individual: any): string {
    const parts = [
        individual?.name?.givenName,
        individual?.name?.otherNames,
        individual?.name?.familyName,
    ].filter((p: any) => typeof p === 'string' && p.trim().length > 0);
    return parts.join(' ').trim();
}

/**
 * Normalize a name for case/whitespace-insensitive equality comparison.
 * Returns lowercased, single-space-collapsed string.
 */
export function normalizeNameForCompare(raw: any): string {
    if (raw == null) return '';
    return String(raw).trim().toLowerCase().replace(/\s+/g, ' ');
}

/**
 * Search Individual service for the given phone numbers and return a map of
 * phone → { serviceUuid, individualId, existingName } for those that already
 * exist in HRMS. Used to make batch processing idempotent on retry and to
 * detect phone-reused-for-different-user cases.
 */
export async function fetchExistingUsersByPhone(
    phoneNumbers: string[],
    tenantId: string,
    requestInfo: RequestInfo
): Promise<Record<string, ExistingHrmsUser>> {
    const result: Record<string, ExistingHrmsUser> = {};
    if (phoneNumbers.length === 0) return result;

    const searchBatchSize = config.user.individualSearchBatchSize;
    logger.info(`Retry pre-check: searching Individual service for ${phoneNumbers.length} phone(s) in ${Math.ceil(phoneNumbers.length / searchBatchSize)} batch(es)`);

    for (let i = 0; i < phoneNumbers.length; i += searchBatchSize) {
        const batch = phoneNumbers.slice(i, i + searchBatchSize);
        try {
            const response = await httpRequest(
                config.host.healthIndividualHost + config.paths.healthIndividualSearch,
                { RequestInfo: requestInfo, Individual: { mobileNumber: batch } },
                { tenantId, limit: searchBatchSize + 5, offset: 0, includeDeleted: false }
            );
            for (const individual of response?.Individual ?? []) {
                const phone = String(individual?.mobileNumber ?? '');
                const serviceUuid = individual?.userUuid ?? '';
                const individualId = individual?.id ?? '';
                if (phone && serviceUuid) {
                    result[phone] = {
                        serviceUuid,
                        individualId,
                        existingName: buildFullNameFromIndividual(individual),
                    };
                }
            }
            logger.info(`Retry pre-check batch [${i}–${i + batch.length}]: queried ${batch.length} phone(s), matched ${Object.keys(result).length - (i === 0 ? 0 : Object.keys(result).length - batch.length)} cumulative`);
        } catch (err) {
            // Non-fatal: if the lookup fails, proceed with creation — HRMS will reject duplicates
            logger.warn(`Idempotency pre-check failed for batch starting at index ${i}: ${err}`);
        }
    }

    // Resolve the existing login usernames (by serviceUuid) so adopted users show their real
    // user id on the credential sheet. Non-fatal — leaves userName blank on failure.
    const uuidToName = await fetchUserNamesByUuid(
        Object.values(result).map(u => u.serviceUuid),
        tenantId,
        requestInfo
    );
    for (const existing of Object.values(result)) {
        const name = uuidToName[existing.serviceUuid];
        if (name) existing.userName = name;
    }

    return result;
}

/** Bounded poll until just-created individuals are searchable, so worker-registry create does not race them into INDIVIDUAL_NOT_FOUND; fail-open (returns the still-missing ids, never throws). */
export async function waitForIndividualsSearchable(
    individualIds: string[],
    tenantId: string,
    requestInfo: RequestInfo
): Promise<{ found: Set<string>; missing: string[] }> {
    const found = new Set<string>();
    const uniqueIds = [...new Set(individualIds.filter(Boolean))];
    if (uniqueIds.length === 0) return { found, missing: [] };

    const searchBatchSize = config.user.individualSearchBatchSize;
    const pollInterval = config.user.individualConsistencyPollIntervalMs;
    const maxAttempts = config.user.individualConsistencyMaxPollAttempts;

    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
        const pending = uniqueIds.filter(id => !found.has(id));

        for (let i = 0; i < pending.length; i += searchBatchSize) {
            const batch = pending.slice(i, i + searchBatchSize);
            try {
                const response = await httpRequest(
                    config.host.healthIndividualHost + config.paths.healthIndividualSearch,
                    { RequestInfo: requestInfo, Individual: { id: batch } },
                    { tenantId, limit: searchBatchSize + 5, offset: 0, includeDeleted: false }
                );
                for (const individual of response?.Individual ?? []) {
                    if (individual?.id) found.add(String(individual.id));
                }
            } catch (err) {
                logger.warn(`Individual consistency poll batch at index ${i} failed (attempt ${attempt}/${maxAttempts}): ${err}`);
            }
        }

        if (found.size >= uniqueIds.length) {
            logger.info(`All ${uniqueIds.length} individual(s) searchable after ${attempt} attempt(s)`);
            return { found, missing: [] };
        }

        logger.info(`Individual consistency poll attempt ${attempt}/${maxAttempts}: ${found.size}/${uniqueIds.length} searchable`);
        if (attempt < maxAttempts) {
            await new Promise(res => setTimeout(res, pollInterval));
        }
    }

    const missing = uniqueIds.filter(id => !found.has(id));
    logger.warn(`${missing.length}/${uniqueIds.length} individual(s) still not searchable after ${maxAttempts} attempt(s); their workers will be deferred for retry (not sent to worker-registry, which would return NON_RECOVERABLE INDIVIDUAL_NOT_FOUND)`);
    return { found, missing };
}

/**
 * Split worker payloads by whether their individual is confirmed searchable: `creatable` are safe to send to
 * worker-registry now, `deferred` reference individuals that lost the read-after-write race and must NOT be
 * sent (worker/v1/bulk/_create would return a terminal NON_RECOVERABLE INDIVIDUAL_NOT_FOUND) — the caller
 * marks them retryable so a later pass re-attempts them once indexing catches up.
 */
export function partitionWorkersByIndividualSearchability(
    workerDataList: WorkerData[],
    missingIndividualIds: string[]
): { creatable: WorkerData[]; deferred: WorkerData[] } {
    if (missingIndividualIds.length === 0) return { creatable: workerDataList, deferred: [] };
    const missingSet = new Set(missingIndividualIds);
    const creatable: WorkerData[] = [];
    const deferred: WorkerData[] = [];
    for (const worker of workerDataList) {
        (missingSet.has(worker.individualId) ? deferred : creatable).push(worker);
    }
    return { creatable, deferred };
}
