import { RequestInfo } from "../config/models/requestInfoSchema";
import express from "express";
import { dropCloneSourceFileIfOutsideHouseholdChoiceDiffers, reattachCloneSourceFileIfChoiceAgrees, prepareAndProduceCancelMessage, processBasedOnAction, processFetchMicroPlan, searchProjectCampaignResourcData, updateCampaignAfterSearch, validateAndFetchCampaign } from "../utils/campaignUtils";
import { logger } from "../utils/logger";
import { validateMicroplanRequest, validateProjectCampaignRequest, validateAddResourcesRequest, validateEditFile } from "../validators/campaignValidators";
import { campaignStatuses, processStatuses, resourceTypes } from "../config/constants";
import { createResourceDetail, updateResourceDetail, getCampaignStatusFromDB, deactivateAllResourcesForCampaign, undoSavedResources, SavedResourceChange } from "./resourceDetailsService";
import { findActiveResourceByUpsertKey, searchResourceDetailsFromDB } from "../utils/resourceDetailsUtils";
import { prepareProcessesForResourceTypes, getCurrentProcesses } from "../utils/genericUtils";
import config from "../config";
import { produceModifiedMessages } from "../kafka/Producer";
import { getRegistryEntry } from "../config/resourceTypeRegistry";
import { dropCloneSourceDeliveryDates, dropCloneSourceDeliveryRules, fileBeforeOutsideHouseholdChange, isGenerationTriggerNeeded, isOutsideHouseholdDeliveryChanged, keepCloneLineage, keepFileForOutsideHouseholdChange } from "../utils/generateUtils";
import { CampaignResource, ResourceDetailsResponse, toCampaignResource } from "../config/models/resourceTypes";

/** Validates and creates a project-type campaign, persisting its resources to eg_cm_resource_details so they survive the Flow 2 JSONB overwrite. */
async function createProjectTypeCampaignService(request: express.Request) {
    logger.info("VALIDATING:: for project type");
    await validateProjectCampaignRequest(request, "create");
    await validateEditFile(request, "create");
    logger.info("VALIDATED:: THE PROJECT TYPE CREATE REQUEST");

    // A clone whose Outside Household choice differs from its source's does not keep the source's file
    await dropCloneSourceFileIfOutsideHouseholdChoiceDiffers(request?.body?.CampaignDetails);
    // A new clone starts without its source's delivery dates; the planner picks its own
    const datesRemoved = dropCloneSourceDeliveryDates(request?.body?.CampaignDetails);
    if (datesRemoved > 0) {
        logger.info(`New clone of ${request?.body?.CampaignDetails?.additionalDetails?.cloneFrom}: removed ${datesRemoved} delivery dates copied from the source`);
    }
    // ...and a clone of another campaign type starts without its source's delivery rules (dates and conditions)
    if (dropCloneSourceDeliveryRules(request?.body?.CampaignDetails)) {
        logger.info(`New clone of ${request?.body?.CampaignDetails?.additionalDetails?.cloneFrom}: removed the delivery rules copied from the source`);
    }

    await processBasedOnAction(request, "create");

    // Persist resources to eg_cm_resource_details table so they survive Flow 2 JSONB overwrite
    const resources: CampaignResource[] = request?.body?.CampaignDetails?.resources || [];
    const tenantId = request?.body?.CampaignDetails?.tenantId;
    const campaignId = request?.body?.CampaignDetails?.id;
    const useruuid = request?.body?.RequestInfo?.userInfo?.uuid || "system";
    if (resources.length > 0 && tenantId && campaignId) {
        for (const res of resources) {
            if (!res.type || !res.filestoreId) continue;
            try {
                await createResourceDetail({
                    tenantId,
                    campaignId,
                    type: res.type,
                    parentResourceId: res.parentResourceId || null,
                    fileStoreId: res.filestoreId,
                    filename: res.filename || null,
                    additionalDetails: res.additionalDetails || {}
                }, useruuid);
                logger.info(`Persisted resource type=${res.type} to eg_cm_resource_details for campaign ${campaignId}`);
            } catch (err) {
                logger.warn(`Failed to persist resource type=${res.type} to table: ${err}`);
            }
        }
    }

    return request?.body?.CampaignDetails;
}

/**
 * The campaign's earlier unified files, newest first (at most 5), not counting the given one; none if they cannot be read.
 * Both active and inactive rows are read: a file replaced by a later one is not always left inactive.
 */
async function earlierUnifiedFiles(tenantId: string, campaignId: string, exclude: string): Promise<string[]> {
    try {
        const criteria = { tenantId, campaignId, type: [resourceTypes.unifiedConsoleResources], parentResourceId: null };
        const page = { sortBy: "createdtime", sortOrder: "DESC" as const, limit: 10, offset: 0 };
        const rows = [
            ...((await searchResourceDetailsFromDB({ ...criteria, isActive: false }, page)) || []),
            ...((await searchResourceDetailsFromDB({ ...criteria, isActive: true }, page)) || []),
        ].sort((a: any, b: any) => Number(b?.createdtime || 0) - Number(a?.createdtime || 0));
        return [...new Set(rows.map((row: any) => row?.filestoreid).filter((id: any) => id && id !== exclude))].slice(0, 5) as string[];
    } catch (error) {
        logger.warn(`Could not read the earlier files of campaign ${campaignId}: ${error instanceof Error ? error.message : error}`);
        return [];
    }
}

/** Backward compat: stores the files of a save in eg_cm_resource_details (an existing row of the type is replaced). */
/** Stores the files sent with a save (upsert by type and parent); returns the rows it created and the rows they replaced. */
async function saveRequestResources(requestResources: CampaignResource[], tenantId: string, campaignId: string, useruuid: string): Promise<SavedResourceChange[]> {
    const changes: SavedResourceChange[] = [];
    for (const res of requestResources) {
        const fileStoreId = res.filestoreId;
        if (!res.type || !fileStoreId) continue;
        try {
            const existing = await findActiveResourceByUpsertKey(tenantId, campaignId, res.type, res.parentResourceId || null);
            if (existing) {
                const created = await updateResourceDetail({
                    id: existing.id,
                    tenantId,
                    campaignId,
                    fileStoreId,
                    filename: res.filename !== undefined ? res.filename : undefined
                }, useruuid);
                if (created?.id) changes.push({ createdId: created.id, replacedId: existing.id });
                logger.info(`Updated existing resource id=${existing.id} type=${res.type} for campaign ${campaignId} via update`);
            } else {
                const created = await createResourceDetail({
                    tenantId,
                    campaignId,
                    type: res.type,
                    parentResourceId: res.parentResourceId || null,
                    fileStoreId,
                    filename: res.filename || null,
                    additionalDetails: res.additionalDetails || {}
                }, useruuid);
                if (created?.id) changes.push({ createdId: created.id });
                logger.info(`Created new resource type=${res.type} for campaign ${campaignId} via update`);
            }
        } catch (err: any) {
            // Surface the real cause instead of collapsing to `${err}` (which hides
            // the downstream code/status/stack behind a generic "Unknown Error. Check Logs").
            logger.error(
                `Failed to upsert resource type=${res.type} for campaign ${campaignId}: ` +
                `code=${err?.code ?? "n/a"} status=${err?.status ?? "n/a"} message=${err?.message ?? String(err)}`
            );
            logger.error(`:: UPSERT ERROR STACK :: ${err?.stack ?? err}`);
        }
    }
    return changes;
}

/** Validates and updates a campaign, deactivating stale resources when hierarchy/boundaries change and re-fetching so the response carries table-backed resources. */
async function updateProjectTypeCampaignService(request: express.Request) {

    await validateProjectCampaignRequest(request, "update");
    await validateEditFile(request, "update");
    logger.info("VALIDATED THE PROJECT TYPE UPDATE REQUEST");

    // The console's final create sends a fresh additionalDetails without cloneFrom / clonedCampaignId; keep them
    const lineageKept = keepCloneLineage(request?.body?.CampaignDetails, request?.body?.ExistingCampaignDetails);
    if (lineageKept.length > 0) {
        logger.info(`Campaign ${request?.body?.CampaignDetails?.id}: kept ${lineageKept.join(", ")} the save left out`);
    }

    const campaignId = request?.body?.CampaignDetails?.id;
    const tenantId = request?.body?.CampaignDetails?.tenantId;
    const useruuid = request?.body?.RequestInfo?.userInfo?.uuid || "system";

    // A save that adds or drops Outside Household detaches the file below; the new template keeps that file's data,
    // and the campaign keeps the file's id for the next change (when no file is attached any more)
    const fileBefore = fileBeforeOutsideHouseholdChange(request);
    keepFileForOutsideHouseholdChange(request, fileBefore);
    if (fileBefore) {
        request.body.fileBeforeOutsideHouseholdChange = fileBefore;
        // Files detached by earlier Outside Household changes: when the sheet comes back, its rows come from the latest one
        request.body.earlierFilesBeforeOutsideHouseholdChange = await earlierUnifiedFiles(tenantId, campaignId, fileBefore);
        logger.info(`Campaign ${campaignId}: Outside Household changed; the new template keeps the data of file ${fileBefore}`);
    }

    // Deactivate stale resources when hierarchyType or boundaries change — user must re-upload for the new configuration
    const existingCampaign = request?.body?.ExistingCampaignDetails;
    const incomingHierarchyType = request?.body?.CampaignDetails?.hierarchyType;
    const hierarchyChanged = existingCampaign && incomingHierarchyType &&
        existingCampaign.hierarchyType !== incomingHierarchyType;
    const boundariesOrTypeChanged = isGenerationTriggerNeeded(request)?.trigger === true;
    // Only an Outside Household choice change keeps the old files detached (the template gains or loses its sheet);
    // other configuration changes keep attaching the files the save sends, as before.
    const detachStaleFiles = Boolean(config.mapping?.detachStaleResourcesOnConfigChange) && isOutsideHouseholdDeliveryChanged(request);
    const staleFileStoreIds = new Set<string>();
    // The files were deactivated for a configuration change: a failed save then keeps what it stored, as before
    let filesDeactivatedForConfigChange = false;
    if (campaignId && tenantId && (hierarchyChanged || boundariesOrTypeChanged)) {
        logger.info(`Campaign config changed (hierarchyChanged=${hierarchyChanged}, boundariesOrTypeChanged=${boundariesOrTypeChanged}) for campaign ${campaignId}. Deactivating stale resources.`);
        try {
            const activeBefore = detachStaleFiles ? await searchResourceDetailsFromDB({ tenantId, campaignId, isActive: true }) : [];
            await deactivateAllResourcesForCampaign(campaignId, tenantId, useruuid);
            filesDeactivatedForConfigChange = true;
            if (detachStaleFiles) {
                activeBefore.forEach((row: any) => row?.filestoreid && staleFileStoreIds.add(row.filestoreid));
                (existingCampaign?.resources || []).forEach((r: any) => r?.filestoreId && staleFileStoreIds.add(r.filestoreId));
            }
        } catch (err) {
            logger.error(`Failed to deactivate resources on campaign config change for campaign ${campaignId}: ${err}`);
        }
    }
    // The console sends the campaign's files back on every save. After an Outside Household choice change the old files
    // no longer match the template, so they are neither attached again below nor saved on the campaign row (which
    // search falls back to); the user downloads the new template and uploads it. A newly uploaded file is kept.
    if (staleFileStoreIds.size > 0 && Array.isArray(request?.body?.CampaignDetails?.resources)) {
        const sent = request.body.CampaignDetails.resources;
        request.body.CampaignDetails.resources = sent.filter((r: any) => !staleFileStoreIds.has(r?.filestoreId));
        const detached = sent.length - request.body.CampaignDetails.resources.length;
        if (detached > 0) {
            logger.info(`Campaign ${campaignId}: ${detached} file(s) from before the configuration change are not attached again`);
        }
    }
    // A clone whose Outside Household choice is back to its source's gets the source's file again (after the old files
    // are dropped above, before the campaign and its files are saved below)
    await reattachCloneSourceFileIfChoiceAgrees(request);

    // The files of the save are stored first: a Create starts the campaign (status started), after which a file can no
    // longer be stored, so a clone whose file came only with its Create kept its source's file
    const requestResources = request?.body?.CampaignDetails?.resources || [];
    const savedResources = (requestResources.length > 0 && tenantId && campaignId)
        ? await saveRequestResources(requestResources as CampaignResource[], tenantId, campaignId, useruuid)
        : [];

    try {
        await processBasedOnAction(request, "update");
    } catch (error) {
        // The save failed before the campaign itself was saved: take back the files it stored, so the campaign keeps the
        // files it had. A failure after the campaign was saved (e.g. starting the template) keeps them with the campaign.
        if (!request?.body?.campaignPersisted && !filesDeactivatedForConfigChange && savedResources.length > 0) {
            await undoSavedResources(savedResources, tenantId, useruuid).catch((undoError: any) =>
                logger.error(`Campaign ${campaignId}: could not take back the files of the failed save: ${undoError?.message ?? undoError}`));
        }
        throw error;
    }

    if (requestResources.length > 0 && tenantId && campaignId) {
        // Re-fetch campaign so response includes full resources from table
        const updatedResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        return updatedResp?.CampaignDetails?.[0] || request?.body?.CampaignDetails;
    }

    // Always re-fetch so response includes resources from eg_cm_resource_details table
    const tenantIdForSearch = request?.body?.CampaignDetails?.tenantId;
    const campaignIdForSearch = request?.body?.CampaignDetails?.id;
    if (tenantIdForSearch && campaignIdForSearch) {
        const freshResp = await searchProjectTypeCampaignService({ tenantId: tenantIdForSearch, ids: [campaignIdForSearch] });
        return freshResp?.CampaignDetails?.[0] || request?.body?.CampaignDetails;
    }
    return request?.body?.CampaignDetails;
}

async function searchProjectTypeCampaignService(campaignDetails: any , request? :any
) {
    logger.info("VALIDATED THE PROJECT TYPE SEARCH REQUEST");

    const { responseData, totalCount } = await searchProjectCampaignResourcData(campaignDetails , request);
    const responseBody: any = { CampaignDetails: responseData, totalCount: totalCount }
    return responseBody;
};

async function fetchFromMicroplanService(request: express.Request) {
    logger.info("FETCHING DATA FROM MICROPLAN");
    await validateMicroplanRequest(request);
    logger.info("UPDATE CAMPAIGN OBJECT")
    await updateCampaignAfterSearch(request, "MICROPLAN_FETCHING")
    logger.info("Validated request successfully");
    processFetchMicroPlan(request);
    return request.body.CampaignDetails;
}

async function cancelCampaignService(request: any) {
    const campaignToCancel = await validateAndFetchCampaign(request);
    const cancelledCampaign = await prepareAndProduceCancelMessage(campaignToCancel, request?.body?.RequestInfo, request);
    return cancelledCampaign;
}

/**
 * Add resources to an existing campaign.
 * Delegates to the new resource details service.
 * Resources are stored in eg_cm_resource_details, not in the campaign JSONB.
 * Preserves backward-compatible request/response shape.
 */
/** Adds resources to an existing (non-processing) campaign via the resource details service and triggers processing if the campaign is already created. */
async function addResourcesToCampaignService(request: express.Request) {
    validateAddResourcesRequest(request);

    const requestCampaignDetails = request?.body?.CampaignDetails;
    const tenantId = requestCampaignDetails?.tenantId;
    const campaignId = requestCampaignDetails?.id;
    const useruuid = request?.body?.RequestInfo?.userInfo?.uuid || "system";
    const newResources = requestCampaignDetails?.resources || [];

    const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
    const existingCampaign = campaignResp?.CampaignDetails?.[0];

    if (!existingCampaign) {
        throw Object.assign(new Error("Campaign not found"), { status: 404, code: "CAMPAIGN_NOT_FOUND" });
    }

    const campaignStatus = existingCampaign?.status;
    if (campaignStatus === campaignStatuses.started) {
        throw Object.assign(
            new Error(`Cannot add resources while campaign is actively processing (status: "${campaignStatus}")`),
            { status: 400, code: "RESOURCE_ADD_NOT_ALLOWED" }
        );
    }
    if (campaignStatus === campaignStatuses.cancelled) {
        throw Object.assign(
            new Error(`Cannot add resources to a cancelled campaign`),
            { status: 400, code: "RESOURCE_ADD_NOT_ALLOWED" }
        );
    }
    // drafted, inprogress (created), failed → allowed

    const createdResources: ResourceDetailsResponse[] = [];
    const createdResourceTypes: string[] = [];
    for (const res of newResources as CampaignResource[]) {
        if (!res.type || !res.filestoreId) {
            logger.warn(`Skipping resource with missing type or filestoreId: ${JSON.stringify(res)}`);
            continue;
        }
        const created = await createResourceDetail({
            tenantId,
            campaignId,
            type: res.type,
            parentResourceId: res.parentResourceId || null,
            fileStoreId: res.filestoreId,
            filename: res.filename || null,
            additionalDetails: res.additionalDetails || {}
        }, useruuid);
        createdResources.push(created);
        createdResourceTypes.push(res.type);
        logger.info(`Stored resource type=${res.type} id=${created.id} for campaign ${campaignId}`);
    }

    // Inject new resources into campaign object for task message (bypasses Kafka async lag).
    // Re-fetching from DB would return stale data since resource is persisted via Kafka async.
    const newCampaignResources: CampaignResource[] = createdResources.map(
        (r: any) => toCampaignResource(r as ResourceDetailsResponse)
    );
    const campaignForTask = {
        ...existingCampaign,
        resources: [
            ...(existingCampaign.resources || []).filter((r: any) =>
                !createdResources.some((nr: any) => nr.type === r.type && (nr.parentResourceId || null) === (r.parentResourceId || null))
            ),
            ...newCampaignResources
        ]
    };

    // Fresh status check before triggering to avoid acting on a cancelled/completed campaign
    // that changed state between the initial fetch and this point.
    if (createdResourceTypes.length > 0) {
        const { status: freshStatus, campaignNumber: freshCampaignNumber } = await getCampaignStatusFromDB(campaignId, tenantId);
        if (freshStatus === campaignStatuses.inprogress && freshCampaignNumber) {
            await triggerResourceProcessingIfCreated(
                campaignId, tenantId, freshCampaignNumber, campaignForTask, createdResourceTypes, useruuid, request?.body?.RequestInfo
            );
        }
    }

    logger.info(`Added ${createdResources.length} resource(s) to campaign ${campaignId}: ${createdResourceTypes.join(", ")}`);
    return campaignForTask;
}

/**
 * Trigger Kafka processing tasks for the given resource types on a created campaign.
 * Resets process tasks to pending before triggering so failed processes can be retried.
 */
export async function triggerResourceProcessingIfCreated(
    campaignId: string,
    tenantId: string,
    campaignNumber: string,
    updatedCampaign: any,
    resourceTypes: string[],
    useruuid: string,
    requestInfo: RequestInfo
): Promise<void> {
    await prepareProcessesForResourceTypes(campaignNumber, tenantId, resourceTypes, useruuid);
    const allCurrentProcesses = await getCurrentProcesses(campaignNumber, tenantId);
    for (const resType of resourceTypes) {
        const registryEntry = getRegistryEntry(resType);
        if (!registryEntry) continue;
        const task = allCurrentProcesses.find((p: any) => p?.processName === registryEntry.processName);
        if (task && task?.status === processStatuses.pending) {
            await produceModifiedMessages({
                task,
                CampaignDetails: updatedCampaign,
                useruuid,
                requestInfo
            }, config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC, tenantId, registryEntry.kafkaKey);
            logger.info(`Triggered processing task for type=${resType} on created campaign ${campaignId}`);
        }
    }
}

/**
 * Fire-and-forget helper: fetch campaign and trigger processing if status is "created" (inprogress).
 * Used by the resource details controller after creating/updating a resource.
 */
export async function triggerIfCampaignCreated(
    campaignId: string,
    tenantId: string,
    resourceType: string,
    useruuid: string,
    requestInfo: RequestInfo,
    newResourceDetails?: any   // optional: the just-created/updated ResourceDetails object
): Promise<void> {
    const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
    const campaign = campaignResp?.CampaignDetails?.[0];
    if (!campaign || campaign.status !== campaignStatuses.inprogress) return;
    const campaignNumber = campaign.campaignNumber;
    if (!campaignNumber) return;

    // Inject new resource into campaign for task message (resource not in DB yet due to Kafka async)
    let campaignForTask = campaign;
    if (newResourceDetails) {
        const newCampaignResource = toCampaignResource(newResourceDetails as ResourceDetailsResponse);
        campaignForTask = {
            ...campaign,
            resources: [
                ...(campaign.resources || []).filter((r: any) =>
                    !(r.type === resourceType && (r.parentResourceId || null) === (newResourceDetails.parentResourceId || null))
                ),
                newCampaignResource
            ]
        };
    }

    await triggerResourceProcessingIfCreated(
        campaignId, tenantId, campaignNumber, campaignForTask, [resourceType], useruuid, requestInfo
    );
}

export {
    createProjectTypeCampaignService,
    updateProjectTypeCampaignService,
    searchProjectTypeCampaignService,
    fetchFromMicroplanService,
    cancelCampaignService,
    addResourcesToCampaignService
}
