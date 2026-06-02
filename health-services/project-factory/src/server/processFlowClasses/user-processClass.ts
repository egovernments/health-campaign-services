import { withUserInfo } from "../config/models/requestInfoSchema";
import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getMappingDataRelatedToCampaign, getRelatedDataWithCampaign, getRelatedDataWithUniqueIdentifiers } from "../utils/genericUtils";
import { dataRowStatuses, mappingStatuses, sheetDataRowStatuses, usageColumnStatus, campaignDataRowFields, userDataFields, userCredentialFields, errorCodes } from "../config/constants";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";
import { DataTransformer } from "../utils/transFormUtil";
import { transformConfigs } from "../config/transformConfigs";
import { httpRequest } from "../utils/request";
import { decrypt, encrypt } from "../utils/cryptUtils";
import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";
import { WorkerData, WorkerRegistryRecord, createOrUpdateWorkers, searchWorkersByIds } from "../utils/workerRegistryUtils";
import { validatePaymentFields } from "../utils/paymentValidationUtils";
import { fetchExistingUsersByPhone, normalizeNameForCompare, type CampaignRecord } from "../utils/userBatchHandler";

// This will be a dynamic template class for different types
export class TemplateClass {
    // Static generate function
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        await validateResourceDetailsBeforeProcess("userValidation", resourceDetails, localizationMap);
        logger.info("Processing file...");
        logger.info(`ResourceDetails: ${JSON.stringify(resourceDetails)}`);

        const campaign = await this.getCampaignDetails(resourceDetails);

        const userSheetData : any[] = wholeSheetData[getLocalizedName("HCM_ADMIN_CONSOLE_USER_LIST", localizationMap)];
        const mobileKey = "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER";
        const mobileNumbersInSheet = userSheetData?.map((u: any) => u?.[mobileKey]);

        const existingUsersForCampaign = await getRelatedDataWithCampaign(resourceDetails?.type, campaign.campaignNumber, resourceDetails?.tenantId);

        const userDataWithMobileNumberButNotOfThisCampaign = await this.getUserDataWithMobileNumberButNotOfThisCampaign(campaign.campaignNumber, mobileNumbersInSheet, resourceDetails);
        const newUsers = await this.extractNewUsers(userSheetData, mobileKey, existingUsersForCampaign, userDataWithMobileNumberButNotOfThisCampaign, campaign.campaignNumber, resourceDetails);
        await this.persistInBatches(newUsers, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC, resourceDetails?.tenantId);

        await this.processBoundaryChanges(userSheetData, existingUsersForCampaign, newUsers, campaign.campaignNumber, resourceDetails);

        const waitTime = Math.max(5000, newUsers.length * 8);
        logger.info(`Waiting for ${waitTime} ms for persistence...`);
        await new Promise((res) => setTimeout(res, waitTime));

        await this.createUserFromTableData(resourceDetails);

        await this.updateWorkerRegistryForCompletedUsers(userSheetData, existingUsersForCampaign, resourceDetails, localizationMap);

        // Read all rows then filter to completed + failed only.
        // Pending users have no encrypted UserName/Password yet — decrypting undefined would throw.
        const allCurrentUsers = await getRelatedDataWithCampaign(resourceDetails?.type, campaign.campaignNumber, resourceDetails?.tenantId);
        const processedUsers = (allCurrentUsers || []).filter(
            (u: any) => u?.status === dataRowStatuses.completed || u?.status === dataRowStatuses.failed
        );
        const allData = await Promise.all(processedUsers.map(async (u: any, idx: number) => {
            logger.info(`Processing item number ${idx + 1} with status ${u?.status}`);
            const data: any = u?.data;
            if (u?.status === dataRowStatuses.failed) {
                // Preserve the failure discriminator already written by the
                // ingest/HRMS path:
                //   sheet-validation invalid → INVALID
                //   HRMS-create failed       → FAILED
                // Default to FAILED for legacy rows that don't carry a tag (HRMS-level failures).
                if (!data["#status#"]) {
                    data["#status#"] = sheetDataRowStatuses.FAILED;
                }
                return data;
            }
            data["#status#"] = sheetDataRowStatuses.CREATED;
            data["UserName"] = decrypt(u?.data?.["UserName"]);
            data["Password"] = decrypt(u?.data?.["Password"]);
            return data;
        }));
        const sheetMap : SheetMap = {};
        sheetMap["HCM_ADMIN_CONSOLE_USER_LIST"] = {
            data : allData,
            dynamicColumns: null
        };
        logger.info(`SheetMap generated for template of type ${resourceDetails.type}.`);
        return sheetMap;
    }

    private static async getUserDataWithMobileNumberButNotOfThisCampaign(campaignNumber: string, mobileNumbersInSheet: string[], resourceDetails: any){
        const existingUsersWithMobileNumber = await getRelatedDataWithUniqueIdentifiers(resourceDetails?.type, mobileNumbersInSheet, resourceDetails?.tenantId, dataRowStatuses.completed);
        const existingUserWithDifferentCampaign = existingUsersWithMobileNumber?.filter((u: any) => u?.campaignNumber !== campaignNumber);
        return existingUserWithDifferentCampaign;
    }

    private static async processBoundaryChanges(userSheetData: any, existingUsers: any, newUsers: any, campaignNumber: string, resourceDetails: any){ 
        const boundaryKey = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY";
        const usageKey = "HCM_ADMIN_CONSOLE_USER_USAGE";
        const phoneKey = "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER";
        const tenantId = resourceDetails?.tenantId;
        const currentMappings = await getMappingDataRelatedToCampaign("user", campaignNumber, resourceDetails?.tenantId);

        const existingUserMobileToDataMapping: any = {};
        for (const user of existingUsers) {
            existingUserMobileToDataMapping[user?.data?.[phoneKey]] = user;
        }

        const boundaryUniqueIdentifierToCurrentMapping: any = {};
        for (const mapping of currentMappings) {
            boundaryUniqueIdentifierToCurrentMapping[`${mapping?.uniqueIdentifierForData}#${mapping?.boundaryCode}`] = mapping;
        }

        await this.processActiveRows(boundaryKey, usageKey, phoneKey, userSheetData, existingUserMobileToDataMapping, boundaryUniqueIdentifierToCurrentMapping, campaignNumber, tenantId);
        await this.processInactiveRows(boundaryKey, usageKey, phoneKey, userSheetData, existingUserMobileToDataMapping, boundaryUniqueIdentifierToCurrentMapping, campaignNumber, tenantId);
    }

    private static async processActiveRows(
        boundaryKey: string,
        usageKey: string,
        phoneKey: string,
        userSheetData: any[],
        existingUserMobileToDataMapping: any,
        boundaryUniqueIdentifierToCurrentMapping: any,
        campaignNumber: string,
        tenantId: string
    ) {
        logger.info(`Processing active rows for user sheet...`);
        const newMappingRow: any[] = [];
        const demappingRows: any[] = [];
        const usersToBeUpdated: any[] = [];

        for (const data of userSheetData) {
            const phone = String(data?.[phoneKey]);
            const sheetUsage = data?.[usageKey];
            const existingEntry = existingUserMobileToDataMapping?.[phone];
            const existingData = existingEntry?.data;

            if (!phone || sheetUsage !== usageColumnStatus.active) continue;

            const sheetBoundaries = data?.[boundaryKey]?.split(",")?.map((b: string) => b.trim()).filter(Boolean) || [];

            if (existingData) {
                const existingUsage = existingData?.[usageKey];
                const existingBoundaries = existingData?.[boundaryKey]?.split(",")?.map((b: string) => b.trim()).filter(Boolean) || [];

                // Case 1: Inactive -> Active
                if (existingUsage === usageColumnStatus.inactive) {
                    for (const boundary of sheetBoundaries) {
                        const key = `${phone}#${boundary}`;
                        if (!boundaryUniqueIdentifierToCurrentMapping?.[key]) {
                            newMappingRow.push({
                                campaignNumber,
                                boundaryCode: boundary,
                                type: "user",
                                uniqueIdentifierForData: phone,
                                mappingId: null,
                                status: mappingStatuses.toBeMapped,
                            });
                        }
                    }

                    existingData[usageKey] = usageColumnStatus.active;
                    existingData[boundaryKey] = sheetBoundaries.join(",");
                    usersToBeUpdated.push(existingEntry); // ✅ Push full entry
                } else {
                    // Case 2: Already active, update boundary diffs
                    for (const boundary of sheetBoundaries) {
                        const key = `${phone}#${boundary}`;
                        if (!boundaryUniqueIdentifierToCurrentMapping?.[key]) {
                            newMappingRow.push({
                                campaignNumber,
                                boundaryCode: boundary,
                                type: "user",
                                uniqueIdentifierForData: phone,
                                mappingId: null,
                                status: mappingStatuses.toBeMapped,
                            });
                        }
                    }

                    for (const boundary of existingBoundaries) {
                        const key = `${phone}#${boundary}`;
                        if (!sheetBoundaries.includes(boundary) && boundaryUniqueIdentifierToCurrentMapping?.[key]) {
                            demappingRows.push({
                                campaignNumber,
                                boundaryCode: boundary,
                                type: "user",
                                uniqueIdentifierForData: phone,
                                mappingId: boundaryUniqueIdentifierToCurrentMapping?.[key]?.mappingId || null,
                                status: mappingStatuses.toBeDeMapped,
                            });
                        }
                    }

                    const sortedSheet = [...sheetBoundaries].sort().join(",");
                    const sortedExisting = [...existingBoundaries].sort().join(",");
                    if (sortedSheet !== sortedExisting) {
                        existingData[boundaryKey] = sortedSheet;
                        existingData[usageKey] = usageColumnStatus.active;
                        usersToBeUpdated.push(existingEntry); // ✅ Push full entry
                    }
                }
            }
            else{
                for (const boundary of sheetBoundaries) {
                    newMappingRow.push({
                        campaignNumber,
                        boundaryCode: boundary,
                        type: "user",
                        uniqueIdentifierForData: phone,
                        mappingId: null,
                        status: mappingStatuses.toBeMapped,
                    });
                }
            }
        }
        let batchSize = config.user.mappingPersistBatchSize;
        for(let i = 0; i < newMappingRow.length; i += batchSize){
            const batch = newMappingRow.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_SAVE_MAPPING_DATA_TOPIC, tenantId);
        }
        for(let i = 0; i < demappingRows.length; i += batchSize){
            const batch = demappingRows.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
        }
        for(let i = 0; i < usersToBeUpdated.length; i += batchSize){
            const batch = usersToBeUpdated.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
        }
        logger.info(`Done processing active rows for user sheet...`);
    }
    

    private static async processInactiveRows(
        boundaryKey: string,
        usageKey: string,
        phoneKey: string,
        userSheetData: any[],
        existingUserMobileToDataMapping: any,
        boundaryUniqueIdentifierToCurrentMapping: any,
        campaignNumber: string,
        tenantId: string
    ) {
        logger.info(`Processing inactive rows for user sheet...`);
        const boundariesToBeDemappedRow: any[] = [];
        const usersToBeUpdated: any[] = [];

        for (const data of userSheetData) {
            const phone = String(data?.[phoneKey]);
            const sheetUsage = data?.[usageKey];
            const existingEntry = existingUserMobileToDataMapping?.[phone];
            const existingData = existingEntry?.data;

            if (!phone || !existingData || sheetUsage !== usageColumnStatus.inactive || existingData?.[usageKey] !== usageColumnStatus.active) continue;

            const existingBoundaries = existingData?.[boundaryKey]?.split(",")?.map((b: string) => b.trim()).filter(Boolean) || [];

            for (const boundary of existingBoundaries) {
                const key = `${phone}#${boundary}`;
                if (boundaryUniqueIdentifierToCurrentMapping?.[key]) {
                    boundariesToBeDemappedRow.push({
                        campaignNumber,
                        boundaryCode: boundary,
                        type: "user",
                        uniqueIdentifierForData: phone,
                        mappingId: null,
                        status: mappingStatuses.toBeDeMapped,
                    });
                }
            }

            // Update to inactive
            existingData[usageKey] = usageColumnStatus.inactive;
            existingData[boundaryKey] = data?.[boundaryKey];
            usersToBeUpdated.push(existingEntry); // ✅ Push full entry
        }

        // Send updates in batches
        const batchSize = config.user.mappingPersistBatchSize;

        for (let i = 0; i < boundariesToBeDemappedRow.length; i += batchSize) {
            const batch = boundariesToBeDemappedRow.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
        }

        for (let i = 0; i < usersToBeUpdated.length; i += batchSize) {
            const batch = usersToBeUpdated.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
        }
        logger.info(`Done processing inactive rows for user sheet...`);
    }

    private static async updateWorkerRegistryForCompletedUsers(
        userSheetData: any[],
        existingUsersForCampaign: any[],
        resourceDetails: any,
        localizationMap: Record<string, string>
    ): Promise<void> {
        const WORKER_BATCH_SIZE = config.workerRegistry.updateBatchSize;
        const workerFieldKeys = [
            "HCM_ADMIN_CONSOLE_USER_PAYEE_PHONE_NUMBER",
            "HCM_ADMIN_CONSOLE_USER_PAYMENT_PROVIDER",
            "HCM_ADMIN_CONSOLE_USER_PAYEE_NAME",
            "HCM_ADMIN_CONSOLE_USER_BANK_ACCOUNT",
            "HCM_ADMIN_CONSOLE_USER_BANK_CODE",
            "HCM_ADMIN_CONSOLE_USER_BENEFICIARY_CODE",
        ];

        // Build map: phone → completed existing campaign record.
        // Use rawPhone != null guard so String(undefined) = "undefined" is never inserted as a key.
        const completedByPhone = new Map<string, any>();
        for (const user of existingUsersForCampaign) {
            if (user?.status === dataRowStatuses.completed) {
                const rawPhone = user?.data?.["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"];
                const phone = rawPhone != null ? String(rawPhone) : "";
                if (phone) completedByPhone.set(phone, user);
            }
        }

        // Find sheet rows that are completed, have a stored workerId, and carry at least one worker field.
        // Same null-safe phone conversion to avoid "undefined" string matching.
        // Use a plain Record instead of Map to avoid Map-iterator issues with ES5 target.
        const workerIdToSheetEntry: Record<string, { row: any; existingUser: any }> = {};
        for (const row of userSheetData) {
            const rawPhone = row?.["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"];
            const phone = rawPhone != null ? String(rawPhone) : "";
            if (!phone) continue;

            const existingUser = completedByPhone.get(phone);
            if (!existingUser) continue;

            const workerId = existingUser?.data?.["HCM_ADMIN_CONSOLE_USER_WORKER_ID"];
            if (!workerId) continue;

            const hasWorkerFields = workerFieldKeys.some(key => !!row?.[key]);
            if (!hasWorkerFields) continue;

            workerIdToSheetEntry[workerId] = { row, existingUser };
        }

        const allWorkerIds = Object.keys(workerIdToSheetEntry);
        if (!allWorkerIds.length) return;

        const tenantId = resourceDetails?.tenantId;
        const workerRequestInfo = withUserInfo(resourceDetails?.requestInfo, { tenantId });

        // Fetch current worker-registry records in batches to get individualIds.
        // Batching avoids sending thousands of IDs in a single API call (timeout / 400).
        const workerByIdMap = new Map<string, WorkerRegistryRecord>();
        for (let i = 0; i < allWorkerIds.length; i += WORKER_BATCH_SIZE) {
            const idBatch = allWorkerIds.slice(i, i + WORKER_BATCH_SIZE);
            try {
                const batchResult = await searchWorkersByIds(idBatch, tenantId, workerRequestInfo);
                for (const worker of batchResult) {
                    if (worker.id) workerByIdMap.set(worker.id, worker);
                }
            } catch (error: unknown) {
                logger.error(`Failed to fetch worker registry batch [${i}–${i + idBatch.length}] for completed users update — skipping this batch:`, error);
                continue;
            }
        }

        // Build WorkerData list directly from already-fetched registry records (avoids double search
        // inside createOrUpdateWorkers by supplying id so it goes through the workersByIdList path,
        // which still re-searches internally — acceptable single extra call per batch).
        const workerDataList: WorkerData[] = [];
        for (const workerId of allWorkerIds) {
            const { row, existingUser } = workerIdToSheetEntry[workerId];
            const existingWorker = workerByIdMap.get(workerId);
            if (!existingWorker) continue;

            const individualId = existingWorker.individualIds?.[0];
            if (!individualId) continue;

            workerDataList.push({
                name: row?.["HCM_ADMIN_CONSOLE_USER_NAME"] || existingUser?.data?.["HCM_ADMIN_CONSOLE_USER_NAME"] || "",
                payeePhoneNumber: String(row?.["HCM_ADMIN_CONSOLE_USER_PAYEE_PHONE_NUMBER"] || ""),
                paymentProvider: row?.["HCM_ADMIN_CONSOLE_USER_PAYMENT_PROVIDER"] || "",
                payeeName: row?.["HCM_ADMIN_CONSOLE_USER_PAYEE_NAME"] || "",
                bankAccount: String(row?.["HCM_ADMIN_CONSOLE_USER_BANK_ACCOUNT"] || ""),
                bankCode: String(row?.["HCM_ADMIN_CONSOLE_USER_BANK_CODE"] || ""),
                beneficiaryCode: String(row?.["HCM_ADMIN_CONSOLE_USER_BENEFICIARY_CODE"]
                    || row?.[getLocalizedName("HCM_ADMIN_CONSOLE_USER_BENEFICIARY_CODE", localizationMap)]
                    || ""),
                id: workerId,
                individualId,
                tenantId,
            });
        }

        if (!workerDataList.length) return;

        // Log warnings for workers with invalid payment fields (don't block — let worker-registry be the hard gate)
        workerDataList.forEach(wd => {
            const result = validatePaymentFields(wd);
            if (!result.valid) {
                logger.warn(`Worker ${wd.individualId} has payment field issues: ${result.errors.join('; ')}`);
            }
        });

        // Call createOrUpdateWorkers in batches to stay within API size limits.
        for (let i = 0; i < workerDataList.length; i += WORKER_BATCH_SIZE) {
            const batch = workerDataList.slice(i, i + WORKER_BATCH_SIZE);
            try {
                await createOrUpdateWorkers(batch, workerRequestInfo);
                logger.info(`Updated worker registry for completed users batch [${i}–${i + batch.length}]`);
            } catch (error: unknown) {
                const errMsg = error instanceof Error ? error.message : String(error);
                logger.error(`Failed to update worker registry for completed users batch [${i}–${i + batch.length}]: ${errMsg}`);
                // Best-effort: one batch failure does not abort remaining batches or the overall upload
            }
        }
    }

    private static async getCampaignDetails(resourceDetails: any): Promise<any> {
        const response = await searchProjectTypeCampaignService({
            tenantId: resourceDetails.tenantId,
            ids: [resourceDetails?.campaignId],
        });
        const campaign = response?.CampaignDetails?.[0];
        if (!campaign) throw new Error("Campaign not found");
        return campaign;
    }

    private static async extractNewUsers(
        userSheetData: any[],
        mobileKey: string,
        existingUsers: any,
        existingUserWithAnotherCampaigns: any,
        campaignNumber: string,
        resourceDetails: any
    ): Promise<any[]> {
        const userMap: any = Object.fromEntries(userSheetData.map((row: any) => [row?.[mobileKey], row]).filter(([m]) => m));
        const existingMap : any = {};
        for(const user of existingUsers){
            existingMap[user?.data?.["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"]] = user;
        }
        const existingMapWithAnotherCampaigns : any = {};
        for(const user of existingUserWithAnotherCampaigns){
            existingMapWithAnotherCampaigns[user?.data?.["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"]] = user;
        }

        const newEntries = [];
        for (const [mobile, row ] of Object.entries(userMap) as [string, any]) {
            if (existingMap?.[String(mobile)]) continue;
            if(existingMapWithAnotherCampaigns?.[String(mobile)]){
                const data = existingMapWithAnotherCampaigns?.[String(mobile)]?.data;
                data["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"] = row?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"];
                data["HCM_ADMIN_CONSOLE_USER_USAGE"] = row?.["HCM_ADMIN_CONSOLE_USER_USAGE"];
                newEntries.push({
                    campaignNumber,
                    data: existingMapWithAnotherCampaigns?.[String(mobile)]?.data,
                    type: resourceDetails?.type,
                    uniqueIdentifier: String(mobile),
                    uniqueIdAfterProcess: existingMapWithAnotherCampaigns?.[String(mobile)]?.uniqueIdAfterProcess,
                    status: dataRowStatuses.completed
                });
            }
            else{
                newEntries.push({
                    campaignNumber,
                    data: row,
                    type: resourceDetails?.type,
                    uniqueIdentifier: String(mobile),
                    uniqueIdAfterProcess: null,
                status: dataRowStatuses.pending
                });
            }
        }
        return newEntries;
    }

    private static async persistInBatches(users: any[], topic: string, tenantId: string): Promise<void> {
        const BATCH_SIZE = config.user.persistBatchSize;
        for (let i = 0; i < users.length; i += BATCH_SIZE) {
            const batch = users.slice(i, i + BATCH_SIZE);
            await produceModifiedMessages({ datas: batch }, topic, tenantId);
        }
    }
    

    static async createUserFromTableData(resourceDetails: any): Promise<any> {
        logger.info("Fetching campaign details...");
        const tenantId = resourceDetails.tenantId;
        const response = await searchProjectTypeCampaignService({
            tenantId: tenantId,
            ids: [resourceDetails?.campaignId],
        });

        const campaign = response?.CampaignDetails?.[0];
        if (!campaign) throw new Error("Campaign not found");

        const campaignNumber = campaign?.campaignNumber;
        const userUuid = campaign?.auditDetails?.createdBy;

        const allCurrentUsers = await getRelatedDataWithCampaign("user", campaignNumber, resourceDetails?.tenantId);
        const usersToCreate: any[] = (allCurrentUsers || []).filter(
            (user: any) =>
                (user?.status === dataRowStatuses.pending || user?.status === dataRowStatuses.failed) &&
                user?.data?.[campaignDataRowFields.status] !== sheetDataRowStatuses.INVALID
        );

        logger.info(`${usersToCreate.length} users to create (pre HRMS-idempotency check)`);
        const requestInfo = resourceDetails?.requestInfo;
        if (!requestInfo?.userInfo) {
            throw new Error('RequestInfo with userInfo is required in resourceDetails for user transformation');
        }

        // HRMS idempotency pre-check (mirror of userBatchHandler retry semantics):
        // for each row we're about to attempt, search Individual service by phone.
        // If HRMS already has the user, mark the row completed and skip HRMS create.
        // Detect phone-reused-for-different-user and treat HRMS as source of truth.
        const phonesToCheck = usersToCreate
            .map((u: any) => {
                const raw = u?.data?.["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"];
                return raw != null ? String(raw) : '';
            })
            .filter((p: string) => !!p && p !== 'undefined');

        const alreadyExistingMap = await fetchExistingUsersByPhone(phonesToCheck, tenantId, requestInfo);

        const absorbedRecords: any[] = [];
        const stillNeedCreate: any[] = [];
        const retryCandidateCount = usersToCreate.filter((u: any) => u?.status === dataRowStatuses.failed).length;
        for (const u of usersToCreate) {
            const phone = String(u?.data?.["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"] ?? '');
            const existing = phone ? alreadyExistingMap[phone] : undefined;
            if (!existing) {
                stillNeedCreate.push(u);
                continue;
            }
            const wasRetry = u?.status === dataRowStatuses.failed;
            const sheetName = normalizeNameForCompare(u?.data?.[userDataFields.name]);
            const hrmsName = normalizeNameForCompare(existing.existingName);

            u.status = dataRowStatuses.completed;
            u.data = {
                ...u.data,
                [userCredentialFields.userServiceUuids]: existing.serviceUuid,
            };
            u.uniqueIdAfterProcess = existing.serviceUuid;

            if (sheetName && hrmsName && sheetName !== hrmsName) {
                const reason = `${errorCodes.hrmsPhoneReusedDifferentUser}: phone exists in HRMS as '${existing.existingName}' but sheet provided '${u?.data?.[userDataFields.name] ?? ''}'. HRMS user kept as source of truth.`;
                logger.warn(`Phone ${phone} → ${reason}`);
                u.data[campaignDataRowFields.errorDetails] = reason;
            } else {
                logger.info(`Row for phone ${phone} already in HRMS (serviceUuid ${existing.serviceUuid}) — marking completed without create${wasRetry ? ' (retry=true)' : ''}`);
            }
            absorbedRecords.push(u);
        }

        logger.info(`HRMS pre-check (sync template path): absorbed ${absorbedRecords.length}, ${stillNeedCreate.length} need HRMS create, ${retryCandidateCount} of original input were retries of previously-failed rows`);

        if (absorbedRecords.length > 0) {
            await this.persistInBatches(absorbedRecords, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, resourceDetails.tenantId);
        }

        if (stillNeedCreate.length === 0) {
            logger.info("All retry candidates resolved by HRMS pre-check; nothing to send to HRMS");
            const earlyWait = 5000;
            await new Promise((res) => setTimeout(res, earlyWait));
            return;
        }

        logger.info(`${stillNeedCreate.length} users to create via HRMS`);
        const userRowDatas = stillNeedCreate.map((u: any) => u?.data);

        const transformConfig = { ...transformConfigs?.["employeeHrms"] };
        transformConfig.metadata.tenantId = tenantId;
        transformConfig.metadata.hierarchy = resourceDetails.hierarchyType;

        const transformer = new DataTransformer(transformConfig);

        logger.info("Transforming user data...");
        const transformedUsers = await transformer.transform(userRowDatas, requestInfo);
        logger.info(`${transformedUsers.length} users transformed`);

        const mobileToCampaignMap = this.buildMobileNumberToCampaignUserMap(allCurrentUsers);
        const BATCH_SIZE = config.user.creationBatchSize;
        for (let i = 0; i < transformedUsers.length; i += BATCH_SIZE) {
            const batch = transformedUsers.slice(i, i + BATCH_SIZE);
            try {
                const { mobileToServiceMap, mobileToIndividualIdMap } = await this.createEmployeesAndGetServiceUuid(batch, userUuid, resourceDetails);

                const successfulUsers = [];
                const workerDataList: WorkerData[] = [];

                for (const user of batch) {
                    const mobile = String(user?.user?.mobileNumber);
                    const serviceUuid = mobileToServiceMap[mobile];
                    const individualId = mobileToIndividualIdMap[mobile];
                    const existing = mobileToCampaignMap[mobile];
                    if (existing) {
                        existing.status = dataRowStatuses.completed;
                        existing.data["UserService Uuids"] = serviceUuid;
                        existing.data["UserName"] = encrypt(user?.user?.userName);
                        existing.data["Password"] = encrypt(user?.user?.password);
                        existing.uniqueIdAfterProcess = serviceUuid;
                        successfulUsers.push(existing);

                        // Collect worker data
                        if (individualId) {
                            workerDataList.push({
                                name: existing.data["HCM_ADMIN_CONSOLE_USER_NAME"] || "",
                                payeePhoneNumber: existing.data["HCM_ADMIN_CONSOLE_USER_PAYEE_PHONE_NUMBER"] || "",
                                paymentProvider: existing.data["HCM_ADMIN_CONSOLE_USER_PAYMENT_PROVIDER"] || "",
                                payeeName: existing.data["HCM_ADMIN_CONSOLE_USER_PAYEE_NAME"] || "",
                                bankAccount: existing.data["HCM_ADMIN_CONSOLE_USER_BANK_ACCOUNT"] || "",
                                bankCode: existing.data["HCM_ADMIN_CONSOLE_USER_BANK_CODE"] || "",
                                beneficiaryCode: existing.data["HCM_ADMIN_CONSOLE_USER_BENEFICIARY_CODE"] || "",
                                id: existing.data["HCM_ADMIN_CONSOLE_USER_WORKER_ID"] || "",
                                individualId,
                                tenantId: resourceDetails.tenantId,
                            });
                        }
                    }
                }

                logger.info(`Successfully created ${successfulUsers.length} users`);

                // Log warnings for workers with invalid payment fields (don't block — let worker-registry be the hard gate)
                workerDataList.forEach(wd => {
                    const result = validatePaymentFields(wd);
                    if (!result.valid) {
                        logger.warn(`Worker ${wd.individualId} has payment field issues: ${result.errors.join('; ')}`);
                    }
                });

                // Create/update workers in worker registry BEFORE persist to capture worker IDs
                if (workerDataList.length > 0) {
                    // Build individualId → campaignRecords map (multiple phones can map to same individualId)
                    const individualIdToRecords = new Map<string, CampaignRecord[]>();
                    for (const [phone, indId] of Object.entries(mobileToIndividualIdMap)) {
                        const record = mobileToCampaignMap[phone] as CampaignRecord | undefined;
                        if (record) {
                            const list = individualIdToRecords.get(indId) || [];
                            list.push(record);
                            individualIdToRecords.set(indId, list);
                        }
                    }

                    try {
                        const workerRequestInfo = withUserInfo(resourceDetails?.requestInfo, { tenantId });
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
                                    for (const record of records) {
                                        record.status = dataRowStatuses.failed;
                                        record.data[campaignDataRowFields.status] = sheetDataRowStatuses.FAILED;
                                        record.data[campaignDataRowFields.errorDetails] = errMsg;
                                    }
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
                            for (const record of records) {
                                record.status = dataRowStatuses.failed;
                                record.data[campaignDataRowFields.status] = sheetDataRowStatuses.FAILED;
                                record.data[campaignDataRowFields.errorDetails] = errMsg;
                            }
                        }
                    }
                }

                await this.persistInBatches(successfulUsers, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, resourceDetails.tenantId);
            } catch (err) {
                // Non-blocking: a partial HRMS batch failure must not abort the rest of the create loop
                // and must not propagate up to fail the campaign. Mark this batch's rows failed (with
                // error history chained) and continue to the next batch — retries can reconcile later.
                const errMsg = err instanceof Error ? err.message : String(err);
                logger.error(`User batch creation failed (non-blocking): ${errMsg}`);
                await this.handleBatchFailure(batch, stillNeedCreate, resourceDetails.tenantId, errMsg);
            }
        }
        const waitTime = Math.max(5000, transformedUsers.length * 8);
        logger.info(`Waiting for ${waitTime} ms for persistence of created users...`);
        await new Promise((res) => setTimeout(res, waitTime));
    }

    private static buildMobileNumberToCampaignUserMap(users: any[]) {
        const map: Record<string, any> = {};
        for (const user of users) {
            const mobile = String(user?.data?.["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"]);
            map[mobile] = user;
        }
        return map;
    }


    private static async handleBatchFailure(batch: any[], usersToCreate: any[], tenantId: string, errMsg?: string) {
        const phoneKey = "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER";
        const batchMobileSet = new Set(batch.map((u: any) => String(u?.user?.mobileNumber)));
        const failedUsers = usersToCreate.filter((u: any) => batchMobileSet.has(String(u?.data?.[phoneKey])));
        failedUsers.forEach(u => {
            u.status = dataRowStatuses.failed;
            u.data["#status#"] = sheetDataRowStatuses.FAILED;
            if (errMsg) u.data["#errorDetails#"] = errMsg;
        });
        logger.warn(`${failedUsers.length} users failed in batch`);
        await this.persistInBatches(failedUsers, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
    }



    static async createEmployeesAndGetServiceUuid(users: any[], userUuid: string, resourceDetails?: any): Promise<{ mobileToServiceMap: Record<string, string>; mobileToIndividualIdMap: Record<string, string> }> {
        const url = config.host.hrmsHost + config.paths.hrmsEmployeeCreate;
        const RequestInfo = resourceDetails?.requestInfo;
        const requestBody = {
            RequestInfo,
            Employees: users,
        };

        try {
            const response = await httpRequest(url, requestBody);
            const mobileToServiceMap: Record<string, string> = {};
            const mobileToIndividualIdMap: Record<string, string> = {};
            for (const employee of response?.Employees) {
                const mobile = String(employee?.user?.mobileNumber);
                mobileToServiceMap[mobile] = employee?.user?.userServiceUuid;
                if (employee?.user?.uuid) {
                    mobileToIndividualIdMap[mobile] = employee?.user?.uuid;
                }
            }
            return { mobileToServiceMap, mobileToIndividualIdMap };
        } catch (error: any) {
            console.error("Employee creation API failed:", error);
            throw new Error(error);
        }
    }

}
