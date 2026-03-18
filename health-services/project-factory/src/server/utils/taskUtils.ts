import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { CampaignResource } from "../config/models/resourceTypes";
import { logger } from "./logger";
import { getLocalizedMessagesHandlerViaLocale, throwError } from "./genericUtils";
import { enrichAndPersistCampaignWithErrorProcessingTask, updateResourceDetails } from "./campaignUtils";
import { searchResourceDetailsFromDB } from "./resourceDetailsUtils";
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
        // Support both camelCase (table-enriched) and lowercase (request-body legacy)
        const resolvedFileStoreId = resource?.filestoreId;
        if (!resolvedFileStoreId) {
            logger.error(`FileStoreId not found for resource type ${resourceType}`);
            throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", `FileStoreId not found for resource type ${resourceType}`);
        }
        const resourceDetails : ResourceDetails = {
            campaignId : CampaignDetails?.id,
            type : resourceType,
            tenantId : CampaignDetails?.tenantId,
            fileStoreId: resolvedFileStoreId!,
            hierarchyType : CampaignDetails?.hierarchyType,
            requestInfo: messageObject?.requestInfo
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

        // Upload annotated workbook (with #status#/#errorDetails# columns) and persist resource result
        try {
            enrichTemplateMetaData(workBook, locale, CampaignDetails?.id);
            const fileResponse = await createAndUploadFileWithOutRequest(workBook, resourceDetails?.tenantId);
            const processedFileStoreId = fileResponse?.[0]?.fileStoreId;
            if (processedFileStoreId) {
                // Keep in-memory update for any downstream code reading CampaignDetails.resources
                if (resource) {
                    updateResourceDetails(CampaignDetails, resource, { status: "completed", processedFileStoreId });
                }
                // Persist to eg_cm_resource_details via update-resource-details Kafka topic
                await persistResourceDetailUpdate(
                    CampaignDetails?.id,
                    CampaignDetails?.tenantId,
                    resourceType,
                    { status: "completed", processedFileStoreId },
                    messageObject?.requestInfo?.userInfo?.uuid
                );
                logger.info(`Uploaded processed file for resource type ${resourceType}: ${processedFileStoreId}`);
            }
        } catch (uploadError) {
            logger.warn(`Failed to upload processed file for resource type ${resourceType}: ${uploadError}. Continuing.`);
        }

        task.status = processStatuses.completed;
        // Add audit details for update
        const currentTime = Date.now();
        task.auditDetails = {
            createdBy: task.auditDetails?.createdBy || messageObject?.requestInfo?.userInfo?.uuid,
            createdTime: task.auditDetails?.createdTime || currentTime,
            lastModifiedBy: messageObject?.requestInfo?.userInfo?.uuid,
            lastModifiedTime: currentTime
        };
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC, CampaignDetails?.tenantId);
    } catch (error) {
        let task = messageObject?.task;
        task.status = processStatuses.failed;
        // Add audit details for failed status update
        const currentTime = Date.now();
        task.auditDetails = {
            createdBy: task.auditDetails?.createdBy || messageObject?.requestInfo?.userInfo?.uuid,
            createdTime: task.auditDetails?.createdTime || currentTime,
            lastModifiedBy: messageObject?.requestInfo?.userInfo?.uuid,
            lastModifiedTime: currentTime
        };
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC, messageObject?.CampaignDetails?.tenantId);
        logger.error(`Error in campaign creation process : ${error}`);
        // Record error on the resource entry
        try {
            const failedResourceType = getResourceType(messageObject?.task?.processName);
            const failedResource = getResorceViaResourceType(messageObject?.CampaignDetails, failedResourceType);
            if (failedResource) {
                updateResourceDetails(messageObject?.CampaignDetails, failedResource, {
                    status: "failed",
                    error: (error as any)?.code || "INTERNAL_SERVER_ERROR",
                    errorMessage: (error as any)?.message || String(error),
                });
            }
            // Persist status=failed to eg_cm_resource_details via update-resource-details topic
            if (failedResourceType) {
                await persistResourceDetailUpdate(
                    messageObject?.CampaignDetails?.id,
                    messageObject?.CampaignDetails?.tenantId,
                    failedResourceType,
                    { status: "failed" },
                    messageObject?.requestInfo?.userInfo?.uuid
                );
            }
        } catch (resourceUpdateError) {
            logger.warn(`Failed to update resource error details: ${resourceUpdateError}`);
        }
        await enrichAndPersistCampaignWithErrorProcessingTask(messageObject?.CampaignDetails, messageObject?.parentCampaign, messageObject?.requestInfo, error);
    }
}

function getResourceType(processName: string) {
    return getResourceTypeByProcessName(processName);
}

function getResorceViaResourceType(campaignDetails: any, resourceType: string): CampaignResource | null {
    const matching = (campaignDetails?.resources || []).filter((r: CampaignResource) => r?.type === resourceType);
    // Prefer the entry currently being created; fall back to first match
    return matching.find((r: CampaignResource) => r?.status === "creating") || matching[0] || null;
}

/**
 * Look up the active resource in eg_cm_resource_details by (campaignId, type)
 * and produce an update message to the update-resource-details Kafka topic.
 * This persists status, processedFileStoreId, and other field changes correctly
 * after (resources are no longer stored in campaign JSONB).
 */
async function persistResourceDetailUpdate(
    campaignId: string,
    tenantId: string,
    resourceType: string,
    updates: { status: string; processedFileStoreId?: string },
    userUuid: string
): Promise<void> {
    if (!campaignId || !tenantId || !resourceType) return;
    try {
        const rows = await searchResourceDetailsFromDB({
            tenantId,
            campaignId,
            type: [resourceType],
            isActive: true
        });
        const dbRow = rows?.[0];
        if (!dbRow) {
            logger.warn(`persistResourceDetailUpdate: no active row found for campaignId=${campaignId} type=${resourceType}`);
            return;
        }
        const now = Date.now();
        const updatedRecord = {
            id: dbRow.id,
            tenantId: dbRow.tenantid,
            campaignId: dbRow.campaignid,
            type: dbRow.type,
            parentResourceId: dbRow.parentresourceid || null,
            fileStoreId: dbRow.filestoreid,
            processedFileStoreId: updates.processedFileStoreId ?? dbRow.processedfilestoreid ?? null,
            filename: dbRow.filename || null,
            status: updates.status,
            action: dbRow.action,
            isActive: true,
            hierarchyType: dbRow.hierarchytype || null,
            additionalDetails: dbRow.additionaldetails || {},
            auditDetails: {
                createdBy: dbRow.createdby,
                createdTime: Number(dbRow.createdtime),
                lastModifiedBy: userUuid || dbRow.lastmodifiedby,
                lastModifiedTime: now
            }
        };
        await produceModifiedMessages(
            { ResourceDetails: updatedRecord },
            config.kafka.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC,
            tenantId
        );
        logger.info(`persistResourceDetailUpdate: campaignId=${campaignId} type=${resourceType} status=${updates.status}`);
    } catch (err) {
        logger.error(`persistResourceDetailUpdate failed for campaignId=${campaignId} type=${resourceType}: ${err}`);
    }
}