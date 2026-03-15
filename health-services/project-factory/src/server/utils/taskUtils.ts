import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { logger } from "./logger";
import { getLocalizedMessagesHandlerViaLocale, throwError } from "./genericUtils";
import { enrichAndPersistCampaignWithErrorProcessingTask } from "./campaignUtils";
import { processStatuses } from "../config/constants";
import { getResourceTypeByProcessName } from "../config/resourceTypeRegistry";
import { processTemplateConfigs } from "../config/processTemplateConfigs";
import { enrichProcessTemplateConfig, handleErrorDuringProcess, processRequest } from "./sheetManageUtils";
import { fetchFileFromFilestore } from "../api/coreApis";
import { getExcelWorkbookFromFileURL, getLocaleFromWorkbook, enrichTemplateMetaData } from "./excelUtils";
import { getLocalisationModuleName } from "./localisationUtils";
import { produceModifiedMessages } from "../kafka/Producer";
import { createAndUploadFileWithOutRequest } from "../api/genericApis";
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

        // Try to extract locale from workbook metadata
        let locale: string = getLocaleFromWorkbook(workBook) || "";

        // Graceful fallback: use campaign locale or default locale if metadata missing
        if (!locale) {
            logger.warn(`Locale metadata not found in workbook for resource type ${resourceType}. Using fallback locale.`);
            locale = CampaignDetails?.additionalDetails?.locale || config.localisation.defaultLocale || "en_IN";
            logger.info(`Using fallback locale: ${locale}`);

            // Enrich the workbook metadata with locale and campaign ID for future use
            try {
                enrichTemplateMetaData(workBook, locale, CampaignDetails?.id);
                const updatedFileResponse = await createAndUploadFileWithOutRequest(workBook, resourceDetails?.tenantId);
                if (updatedFileResponse?.[0]?.fileStoreId) {
                    resourceDetails.fileStoreId = updatedFileResponse[0].fileStoreId;
                    logger.info(`Enriched file metadata and updated fileStoreId: ${resourceDetails.fileStoreId}`);
                }
            } catch (enrichError) {
                logger.warn(`Failed to enrich file metadata: ${enrichError}. Continuing with fallback locale.`);
            }
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
    return getResourceTypeByProcessName(processName);
}

function getResorceViaResourceType(campaignDetails: any, resourceType: string) {
    for(let i = 0; i < campaignDetails?.resources?.length; i++) {
        if(campaignDetails?.resources[i]?.type == resourceType) {
            return campaignDetails?.resources[i];
        }
    }
    return null;
}