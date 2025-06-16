import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getMappingDataRelatedToCampaign, getRelatedDataWithCampaign, getRelatedDataWithUniqueIdentifiers } from "../utils/genericUtils";
import { dataRowStatuses, mappingStatuses, sheetDataRowStatuses, usageColumnStatus } from "../config/constants";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";
import { DataTransformer } from "../utils/transFormUtil";
import { transformConfigs } from "../config/transformConfigs";
import { defaultRequestInfo } from "../api/coreApis";
import { httpRequest } from "../utils/request";
import { decrypt, encrypt } from "../utils/cryptUtils";
import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";

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

        const existingUsersForCampaign = await getRelatedDataWithCampaign(resourceDetails?.type, campaign.campaignNumber);

        const userDataWithMobileNumberButNotOfThisCampaign = await this.getUserDataWithMobileNumberButNotOfThisCampaign(campaign.campaignNumber, mobileNumbersInSheet, resourceDetails);
        const newUsers = await this.extractNewUsers(userSheetData, mobileKey, existingUsersForCampaign, userDataWithMobileNumberButNotOfThisCampaign, campaign.campaignNumber, resourceDetails);
        await this.persistInBatches(newUsers, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC);

        await this.processBoundaryChanges(userSheetData, existingUsersForCampaign, newUsers, campaign.campaignNumber, resourceDetails);

        const waitTime = Math.max(5000, newUsers.length * 8);
        logger.info(`Waiting for ${waitTime} ms for persistence...`);
        await new Promise((res) => setTimeout(res, waitTime));

        await this.createUserFromTableData(resourceDetails);

        const allCurrentUsers = await getRelatedDataWithCampaign(resourceDetails?.type, campaign.campaignNumber, dataRowStatuses.completed);
        const allData = allCurrentUsers?.map((u: any) => {
            const data: any = u?.data;
            data["#status#"] = sheetDataRowStatuses.CREATED;
            data["UserName"] = decrypt(u?.data?.["UserName"]);
            data["Password"] = decrypt(u?.data?.["Password"]);
            return data;
        });
        const sheetMap : SheetMap = {};
        sheetMap["HCM_ADMIN_CONSOLE_USER_LIST"] = {
            data : allData,
            dynamicColumns: null
        };
        logger.info(`SheetMap generated for template of type ${resourceDetails.type}.`);
        return sheetMap;
    }

    private static async getUserDataWithMobileNumberButNotOfThisCampaign(campaignNumber: string, mobileNumbersInSheet: string[], resourceDetails: any){
        const existingUsersWithMobileNumber = await getRelatedDataWithUniqueIdentifiers(resourceDetails?.type, mobileNumbersInSheet, dataRowStatuses.completed);
        const existingUserWithDifferentCampaign = existingUsersWithMobileNumber?.filter((u: any) => u?.campaignNumber !== campaignNumber);
        return existingUserWithDifferentCampaign;
    }

    private static async processBoundaryChanges(userSheetData: any, existingUsers: any, newUsers: any, campaignNumber: string, resourceDetails: any){ 
        const boundaryKey = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY";
        const usageKey = "HCM_ADMIN_CONSOLE_USER_USAGE";
        const phoneKey = "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER";
        const currentMappings = await getMappingDataRelatedToCampaign("user", campaignNumber);

        const existingUserMobileToDataMapping: any = {};
        for (const user of existingUsers) {
            existingUserMobileToDataMapping[user?.data?.[phoneKey]] = user;
        }

        const boundaryUniqueIdentifierToCurrentMapping: any = {};
        for (const mapping of currentMappings) {
            boundaryUniqueIdentifierToCurrentMapping[`${mapping?.uniqueIdentifierForData}#${mapping?.boundaryCode}`] = mapping;
        }

        await this.processActiveRows(boundaryKey, usageKey, phoneKey, userSheetData, existingUserMobileToDataMapping, boundaryUniqueIdentifierToCurrentMapping, campaignNumber);
        await this.processInactiveRows(boundaryKey, usageKey, phoneKey, userSheetData, existingUserMobileToDataMapping, boundaryUniqueIdentifierToCurrentMapping, campaignNumber);
    }

    private static async processActiveRows(
        boundaryKey: string,
        usageKey: string,
        phoneKey: string,
        userSheetData: any[],
        existingUserMobileToDataMapping: any,
        boundaryUniqueIdentifierToCurrentMapping: any,
        campaignNumber: string
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
        let batchSize = 100;
        for(let i = 0; i < newMappingRow.length; i += batchSize){
            const batch = newMappingRow.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_SAVE_MAPPING_DATA_TOPIC);
        }
        for(let i = 0; i < demappingRows.length; i += batchSize){
            const batch = demappingRows.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC);
        }
        for(let i = 0; i < usersToBeUpdated.length; i += batchSize){
            const batch = usersToBeUpdated.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
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
        campaignNumber: string
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
        const batchSize = 100;

        for (let i = 0; i < boundariesToBeDemappedRow.length; i += batchSize) {
            const batch = boundariesToBeDemappedRow.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC);
        }

        for (let i = 0; i < usersToBeUpdated.length; i += batchSize) {
            const batch = usersToBeUpdated.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
        }
        logger.info(`Done processing inactive rows for user sheet...`);
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

    private static async persistInBatches(users: any[], topic: string): Promise<void> {
        const BATCH_SIZE = 100;
        for (let i = 0; i < users.length; i += BATCH_SIZE) {
            const batch = users.slice(i, i + BATCH_SIZE);
            await produceModifiedMessages({ datas: batch }, topic);
        }
    }
    

    static async createUserFromTableData(resourceDetails: any): Promise<any> {
        logger.info("Fetching campaign details...");
        const response = await searchProjectTypeCampaignService({
            tenantId: resourceDetails.tenantId,
            ids: [resourceDetails?.campaignId],
        });

        const campaign = response?.CampaignDetails?.[0];
        if (!campaign) throw new Error("Campaign not found");

        const campaignNumber = campaign?.campaignNumber;
        const userUuid = campaign?.auditDetails?.createdBy;

        const allCurrentUsers = await getRelatedDataWithCampaign("user", campaignNumber);
        const usersToCreate = allCurrentUsers?.filter(
            (user: any) => user?.status === dataRowStatuses.pending || user?.status === dataRowStatuses.failed
        );

        logger.info(`${usersToCreate?.length} users to create`);
        const userRowDatas = usersToCreate?.map((u: any) => u?.data);

        const transformConfig = { ...transformConfigs?.["employeeHrms"] };
        transformConfig.metadata.tenantId = resourceDetails.tenantId;
        transformConfig.metadata.hierarchy = resourceDetails.hierarchyType;

        const transformer = new DataTransformer(transformConfig);

        logger.info("Transforming user data...");
        const transformedUsers = await transformer.transform(userRowDatas);
        logger.info(`${transformedUsers.length} users transformed`);

        const mobileToCampaignMap = this.buildMobileNumberToCampaignUserMap(allCurrentUsers);
        const BATCH_SIZE = 100;
        for (let i = 0; i < transformedUsers.length; i += BATCH_SIZE) {
            const batch = transformedUsers.slice(i, i + BATCH_SIZE);
            try {
                const mobileToUserServiceMap = await this.createEmployeesAndGetServiceUuid(batch, userUuid);

                const successfulUsers = [];
                for (const user of batch) {
                    const mobile = String(user?.user?.mobileNumber);
                    const serviceUuid = mobileToUserServiceMap[mobile];
                    const existing = mobileToCampaignMap[mobile];
                    if (existing) {
                        existing.status = dataRowStatuses.completed;
                        existing.data["UserService Uuids"] = serviceUuid;
                        existing.data["UserName"] = encrypt(user?.user?.userName);
                        existing.data["Password"] = encrypt(user?.user?.password);
                        existing.uniqueIdAfterProcess = serviceUuid;
                        successfulUsers.push(existing);
                    }
                }

                logger.info(`Successfully created ${successfulUsers.length} users`);
                await this.persistInBatches(successfulUsers, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
            } catch (err) {
                console.error("Error in batch creation:", err);
                await this.handleBatchFailure(batch, usersToCreate);
                throw new Error(`Error in user batch creation: ${err}`);
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


    private static async handleBatchFailure(batch: any[], usersToCreate: any[]) {
        const phoneKey = "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER";
        const batchMobileSet = new Set(batch.map((u: any) => String(u?.user?.mobileNumber)));
        const failedUsers = usersToCreate.filter((u: any) => batchMobileSet.has(String(u?.data?.[phoneKey])));
        failedUsers.forEach(u => u.status = dataRowStatuses.failed);
        logger.warn(`${failedUsers.length} users failed in batch`);
        await this.persistInBatches(failedUsers, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
    }



    static async createEmployeesAndGetServiceUuid(users: any[], userUuid: string) {
        const url = config.host.hrmsHost + config.paths.hrmsEmployeeCreate;
        const RequestInfo : any = defaultRequestInfo?.RequestInfo;
        RequestInfo.userInfo.uuid = userUuid;
        const requestBody = {
            RequestInfo,
            Employees: users,
        };

        try {
            const response = await httpRequest(url, requestBody);
            const map: any = {};
            for (const user of response?.Employees) {
                map[user?.user?.mobileNumber] = user?.user?.userServiceUuid;
            }
            return map;
        } catch (error: any) {
            console.error("Employee creation API failed:", error);
            throw new Error(error);
        }
    }

}
