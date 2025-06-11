import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getRelatedDataWithCampaign } from "../utils/genericUtils";
import { dataRowStatuses, sheetDataRowStatuses } from "../config/constants";
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

        const userSheetData = wholeSheetData[getLocalizedName("HCM_ADMIN_CONSOLE_USER_LIST", localizationMap)];
        const mobileKey = "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER";

        const newUsers = await this.extractNewUsers(userSheetData, mobileKey, campaign.campaignNumber, resourceDetails);
        await this.persistInBatches(newUsers, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC);

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
        sheetData: any[],
        mobileKey: string,
        campaignNumber: string,
        resourceDetails: any
    ): Promise<any[]> {
        const userMap : any = Object.fromEntries(sheetData.map((row: any) => [row?.[mobileKey], row]).filter(([m]) => m));

        const existing = await getRelatedDataWithCampaign(resourceDetails?.type, campaignNumber);
        const existingMap : any = {};
        for(const user of existing){
            existingMap[user?.data?.["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"]] = user;
        }

        const newEntries = [];
        for (const [mobile, row] of Object.entries(userMap)) {
            if (existingMap?.[String(mobile)]) continue;
            newEntries.push({
                campaignNumber,
                data : row,
                type: resourceDetails?.type,
                uniqueIdentifier: mobile,
                uniqueIdAfterProcess: null,
                status: dataRowStatuses.pending
            });
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
