import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { logger } from "./logger";
import { getLocalizedMessagesHandlerViaLocale, throwError } from "./genericUtils";
import { enrichAndPersistCampaignWithErrorProcessingTask } from "./campaignUtils";
import { allProcesses, processStatuses } from "../config/constants";
import { processTemplateConfigs } from "../config/processTemplateConfigs";
import { enrichProcessTemplateConfig, handleErrorDuringProcess, processRequest } from "./sheetManageUtils";
import { fetchFileFromFilestore } from "../api/coreApis";
import { getExcelWorkbookFromFileURL, getLocaleFromWorkbook } from "./excelUtils";
import { getLocalisationModuleName } from "./localisationUtils";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";

export async function handleTaskForCampaign(messageObject: any) {
    try {
        const { CampaignDetails, task } = messageObject;
        const processName = task?.processName
        logger.info(`Task for campaign ${CampaignDetails?.id} : ${processName} started..`);
        const resourceType : string = getResourceType(processName);
        if(!resourceType) {
            logger.error(`Resource type not found for process ${processName}`);
            throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", `Resource type not found for process ${processName}`);
        }
        const resource = getResorceViaResourceType(CampaignDetails, resourceType);
        if (!resource?.filestoreId) {
            logger.error(`FileStoreId not found for resource type ${resourceType}`);
            throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", `FileStoreId not found for resource type ${resourceType}`);
        }
        const resourceDetails : ResourceDetails = {
            campaignId : CampaignDetails?.id,
            type : resourceType,
            tenantId : CampaignDetails?.tenantId,
            fileStoreId: resource?.filestoreId,
            hierarchyType : CampaignDetails?.hierarchyType
        }
        logger.info(`Process resource for campaign ${CampaignDetails?.id} : ${processName} started..`);
        const processTemplateConfig = JSON.parse(JSON.stringify(processTemplateConfigs?.[String(resourceType)]));
        await enrichProcessTemplateConfig(resourceDetails, processTemplateConfig);
        const fileUrl = await fetchFileFromFilestore(resourceDetails?.fileStoreId, resourceDetails?.tenantId);
        const workBook = await getExcelWorkbookFromFileURL(fileUrl);
        let locale = getLocaleFromWorkbook(workBook) || "";
        if (!locale) {
            throw new Error(`Locale not found in the file metadata for resource type ${resourceType}`);
        }
        const localizationMapHierarchy = resourceDetails?.hierarchyType && await getLocalizedMessagesHandlerViaLocale(locale, resourceDetails?.tenantId, getLocalisationModuleName(resourceDetails?.hierarchyType), true);
        const localizationMapModule = await getLocalizedMessagesHandlerViaLocale(locale, resourceDetails?.tenantId);
        const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };
        try {
            await processRequest(resourceDetails, workBook, processTemplateConfig, localizationMap);
        } catch (error) {
            console.log(error)
            await handleErrorDuringProcess(resourceDetails, error);
            throw error;
        }
        logger.info(`Process resource for campaign ${CampaignDetails?.id} : ${processName} completed..`);
        task.status = processStatuses.completed;
        // Add audit details for update
        const currentTime = Date.now();
        task.auditDetails = {
            createdBy: task.auditDetails?.createdBy || messageObject?.useruuid,
            createdTime: task.auditDetails?.createdTime || currentTime,
            lastModifiedBy: messageObject?.useruuid,
            lastModifiedTime: currentTime
        };
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC, CampaignDetails?.tenantId);
    } catch (error) {
        let task = messageObject?.task;
        task.status = processStatuses.failed;
        // Add audit details for failed status update
        const currentTime = Date.now();
        task.auditDetails = {
            createdBy: task.auditDetails?.createdBy || messageObject?.useruuid,
            createdTime: task.auditDetails?.createdTime || currentTime,
            lastModifiedBy: messageObject?.useruuid,
            lastModifiedTime: currentTime
        };
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC, messageObject?.CampaignDetails?.tenantId);
        logger.error(`Error in campaign creation process : ${error}`);
        await enrichAndPersistCampaignWithErrorProcessingTask(messageObject?.CampaignDetails, messageObject?.parentCampaign, messageObject?.useruuid, error);
    }
}

function getResourceType(processName: string) {
    if(processName == allProcesses.facilityCreation) return "facility";
    if(processName == allProcesses.userCreation) return "user";
    if(processName == allProcesses.projectCreation) return "boundary"; 
    return "";
}

function getResorceViaResourceType(campaignDetails: any, resourceType: string) {
    for(let i = 0; i < campaignDetails?.resources?.length; i++) {
        if(campaignDetails?.resources[i]?.type == resourceType) {
            return campaignDetails?.resources[i];
        }
    }
    return null;
}