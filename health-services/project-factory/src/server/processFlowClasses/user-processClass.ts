import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getRelatedDataWithCampaign } from "../utils/genericUtils";
import { dataRowStatuses } from "../config/constants";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";
import { DataTransformer, transformConfigs } from "../config/transFormConfig";
import { defaultRequestInfo } from "../api/coreApis";
import { httpRequest } from "../utils/request";

// This will be a dynamic template class for different types
export class TemplateClass {
    // Static generate function
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>
    ): Promise<SheetMap> {
        logger.info("Processing file...");
        logger.info(`ResourceDetails: ${JSON.stringify(resourceDetails)}`);

        const reverseMap = this.getReverseLocalizationMap(localizationMap);
        const campaign = await this.getCampaignDetails(resourceDetails);

        const sheetData = wholeSheetData[getLocalizedName("HCM_ADMIN_CONSOLE_USER_LIST", localizationMap)];
        const mobileKey = getLocalizedName("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", localizationMap);

        const newUsers = await this.extractNewUsers(sheetData, mobileKey, campaign.campaignNumber, resourceDetails, reverseMap);
        await this.persistInBatches(newUsers, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC);

        const waitTime = Math.max(5000, newUsers.length * 8);
        logger.info(`Waiting for ${waitTime} ms for persistence...`);
        await new Promise((res) => setTimeout(res, waitTime));

        await this.createUserFromTableData(resourceDetails, localizationMap);
        return {};
    }

    private static getReverseLocalizationMap(localizationMap: Record<string, string>): Map<string, string> {
        const reverse = new Map<string, string>();
        Object.entries(localizationMap).forEach(([key, val]) => reverse.set(val, key));
        return reverse;
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
        resourceDetails: any,
        reverseMap: Map<string, string>
    ): Promise<any[]> {
        const userMap : any = Object.fromEntries(sheetData.map((row: any) => [row?.[mobileKey], row]).filter(([m]) => m));

        const existing = await getRelatedDataWithCampaign(resourceDetails?.type, campaignNumber);
        const existingMap : any = {};
        for(const user of existing){
            existingMap[user?.data?.[reverseMap.get(mobileKey) || mobileKey]] = user;
        }

        const newEntries = [];
        for (const [mobile, row] of Object.entries(userMap)) {
            if (existingMap?.[String(mobile)]) continue;
            const data = Object.fromEntries(Object.entries(row as any).map(([k, v]) => [reverseMap.get(k) || k, v]));
            newEntries.push({
                campaignNumber,
                data,
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
    

    static async createUserFromTableData(resourceDetails: any, localizationMap: Record<string, string>): Promise<any> {
        const response = await searchProjectTypeCampaignService({
            tenantId: resourceDetails.tenantId,
            ids: [resourceDetails?.campaignId],
        });
        const campaign = response?.CampaignDetails?.[0];
        if (!campaign) throw new Error("Campaign not found");
        const campaignNumber = campaign?.campaignNumber;
        const userUuid = campaign?.auditDetails?.createdBy;
        const allCurrentUsers = await getRelatedDataWithCampaign("user",campaignNumber);
        const mobileNumberAndCampaignUserMapping : any = {};
        for(const user of allCurrentUsers){
            mobileNumberAndCampaignUserMapping[String(user?.data?.[getLocalizedName("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER")])] = user;
        }
        const usersToCreate = allCurrentUsers?.filter((user: any) => (user?.status === dataRowStatuses.pending || user?.status === dataRowStatuses.failed));
        logger.info(`${usersToCreate?.length} users to create`);
        const userRowDatas = usersToCreate?.map((user: any) => user?.data);
        const transFormConfig = transformConfigs?.["employeeHrms"];
        transFormConfig.metadata.tenantId = resourceDetails.tenantId;
        transFormConfig.metadata.hierarchy = resourceDetails.hierarchyType;
        const transformer = new DataTransformer(transFormConfig);
        logger.info("Transforming users...");
        const transformerUserDatas = await transformer.transform(userRowDatas);
        logger.info(`${transformerUserDatas?.length} transformed users`);
        let BATCH_SIZE = 100;
        for (let i = 0; i < transformerUserDatas?.length; i += BATCH_SIZE) {
            const batch = transformerUserDatas?.slice(i, i + BATCH_SIZE);
            try{
                const mobileNumberAndUserServiceUuid : any = await this.createEmployeesAndGetServiceUuid(batch, userUuid);
                const userSuccessFullyCreated : any = [];
                for(const user of batch){
                    const mobileNumber = String(user?.user?.mobileNumber);
                    const userServiceUuid = mobileNumberAndUserServiceUuid[mobileNumber];
                    if(mobileNumberAndCampaignUserMapping[mobileNumber]){
                        const exsistingData = mobileNumberAndCampaignUserMapping[mobileNumber];
                        exsistingData.status = dataRowStatuses.completed;
                        exsistingData.data["UserService Uuids"] = userServiceUuid;
                        exsistingData.uniqueIdAfterProcess = userServiceUuid;
                        userSuccessFullyCreated.push(exsistingData);
                    }
                }
                await this.persistInBatches(userSuccessFullyCreated, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
            }
            catch(e){
                console.log(e);
                const mobileNumbers = new Set(batch?.map((user: any) => String(user?.user?.mobileNumber)));
                const failedUsers = usersToCreate?.filter((user: any) => mobileNumbers.has(String(user?.data?.[getLocalizedName("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", localizationMap)])));
                failedUsers?.forEach((data: any) => data.status = dataRowStatuses.failed);
                await this.persistInBatches(failedUsers, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
            }
        }
    }

    static async  createEmployeesAndGetServiceUuid(users: any[], userUuid: string) {
        const url = config.host.hrmsHost + config.paths.hrmsEmployeeCreate
        const requestBody = {
            RequestInfo : defaultRequestInfo?.RequestInfo,
            Employees : users
        }
        let response
        try {
            response = await httpRequest(url, requestBody);
        } catch (error : any) {
            console.log(error);
            throw new Error(error);
        }        
        const mobileNumberAndUserServiceUuid : any = {};
        for(const user of response?.Employees){
            mobileNumberAndUserServiceUuid[user?.user?.mobileNumber] = user?.user?.userServiceUuid;
        }
        return mobileNumberAndUserServiceUuid;
    }
}
