import { getLocalizedMessagesHandler, processGenerate } from "./genericUtils";
import _ from 'lodash';
import { logger } from "./logger";
import { generateDataService } from "../service/sheetManageService";
import config from "../config";
import { resourceTypes } from "../config/constants";
import { getLocaleFromRequestInfo, getLocalisationModuleName } from "./localisationUtils";
import { getBoundarySheetData } from "../api/genericApis";
import { checkIfSourceIsMicroplan } from "./campaignUtils";
import { httpRequest } from "./request";
import { RequestInfo } from "../config/models/requestInfoSchema";
import { CampaignId, EpochMs, GenerationId, TenantId } from "../config/models/brandedTypes";
import { OUTSIDE_HOUSEHOLD_DELIVERY_METHOD, campaignTypeOffersOutsideHousehold, normalizeDeliveryMethodCode } from "./deliveryMethodUtils";

function extractProperties(obj: any) {
    return {
        code: obj.code || null,
        includeAllChildren: obj.includeAllChildren || null,
        isRoot: obj.isRoot || null
    };
}

function areBoundariesSame(existingBoundaries: any, currentBoundaries: any) {
    const existing = existingBoundaries ?? [];
    const current = currentBoundaries ?? [];

    if (existing.length === 0 && current.length === 0) return true;
    if (existing.length !== current.length) return false;
    const existingSetOfBoundaries = new Set(existing.map((exboundary: any) => JSON.stringify(extractProperties(exboundary))));
    const currentSetOfBoundaries = new Set(current.map((currboundary: any) => JSON.stringify(extractProperties(currboundary))));
    return _.isEqual(existingSetOfBoundaries, currentSetOfBoundaries);
}

function isCampaignTypeSame(request: any) {
    const existingCampaignType = request?.body?.ExistingCampaignDetails?.projectType;
    const currentCampaignType = request?.body?.CampaignDetails?.projectType;
    return _.isEqual(existingCampaignType, currentCampaignType);
}

/** Whether additionalDetails.cycleData.deliveryMethods (codes or {code} objects) holds OUTSIDEHOUSEHOLD, compared as excel-ingestion does; null when there is no list. */
export function hasOutsideHouseholdDelivery(campaignDetails: any): boolean | null {
    const methods = campaignDetails?.additionalDetails?.cycleData?.deliveryMethods;
    if (!Array.isArray(methods)) return null;
    return methods.some((method: any) =>
        normalizeDeliveryMethodCode(method && typeof method === "object" ? method.code : method) === OUTSIDE_HOUSEHOLD_DELIVERY_METHOD);
}

/** True when the update adds or removes OUTSIDEHOUSEHOLD (the template gains or loses its Outside Household sheet); an update without a deliveryMethods list changes nothing. */
export function isOutsideHouseholdDeliveryChanged(request: any): boolean {
    const incoming = hasOutsideHouseholdDelivery(request?.body?.CampaignDetails);
    if (incoming === null) return false;
    return incoming !== (hasOutsideHouseholdDelivery(request?.body?.ExistingCampaignDetails) ?? false);
}

/**
 * True when the clone's Outside Household choice (its deliveryMethods) differs from its source's, so the source's file
 * (with or without the Outside Household sheet) does not match the clone's template. False when the switch is off, the
 * clone carries no deliveryMethods list, or the source is unknown - the clone then keeps the source's file as before.
 */
export function isOutsideHouseholdChoiceDifferentFromSource(cloneDetails: any, sourceCampaign: any): boolean {
  if (!config.mapping?.detachStaleResourcesOnConfigChange || !sourceCampaign) return false;
  const cloneChoice = hasOutsideHouseholdDelivery(cloneDetails);
  if (cloneChoice === null) return false;
  return cloneChoice !== (hasOutsideHouseholdDelivery(sourceCampaign) ?? false);
}

const isUnifiedFile = (r: any) => r?.type === resourceTypes.unifiedConsoleResources && r?.filestoreId;

/**
 * The source's unified file to attach again to a clone whose Outside Household choice changed back to its source's, or
 * null. Only for a clone (cloneFrom, no parent) whose save changed the choice, carries a readable deliveryMethods list
 * that now agrees with the source, and brings no unified file of its own, when the source has one.
 */
export function cloneSourceFileToReattach(cloneDetails: any, sourceCampaign: any, outsideHouseholdChanged: boolean): any | null {
    if (!config.mapping?.detachStaleResourcesOnConfigChange || !outsideHouseholdChanged || !sourceCampaign) return null;
    if (!cloneDetails?.additionalDetails?.cloneFrom || cloneDetails?.parentId) return null;
    if (hasOutsideHouseholdDelivery(cloneDetails) === null || isOutsideHouseholdChoiceDifferentFromSource(cloneDetails, sourceCampaign)) return null;
    if ((Array.isArray(cloneDetails.resources) ? cloneDetails.resources : []).some(isUnifiedFile)) return null;
    const file = (Array.isArray(sourceCampaign.resources) ? sourceCampaign.resources : []).find(isUnifiedFile);
    return file ? { type: resourceTypes.unifiedConsoleResources, filestoreId: file.filestoreId, filename: file.filename || null, additionalDetails: {} } : null;
}

// additionalDetails keys that tie a clone to the campaign it was copied from
const CLONE_LINEAGE_KEYS = ["cloneFrom", "clonedCampaignId"] as const;

/**
 * Fills the clone lineage keys a save left out from the saved campaign, so a clone does not lose the link to its source
 * (excel-ingestion accepts the source's file only through clonedCampaignId). A key the save sends is never changed.
 * Returns the keys filled; none when the switch is off or the saved campaign has none.
 */
export function keepCloneLineage(incomingCampaign: any, savedCampaign: any): string[] {
    const saved = savedCampaign?.additionalDetails;
    if (!config.mapping?.keepCloneLineageOnUpdate || !incomingCampaign || !saved) return [];
    const filled: string[] = [];
    for (const key of CLONE_LINEAGE_KEYS) {
        const incoming = incomingCampaign.additionalDetails?.[key];
        if ((incoming === undefined || incoming === null || incoming === "") && saved[key]) {
            incomingCampaign.additionalDetails = { ...(incomingCampaign.additionalDetails || {}), [key]: saved[key] };
            filled.push(key);
        }
    }
    return filled;
}

/**
 * A clone being created starts without its source's delivery dates (the cycles' fromDate / toDate in cycleData and their
 * startDate / endDate in deliveryRules): they belong to the source's period, and the planner picks the clone's own in the
 * delivery step. Cycles, deliveries, doses, conditions and delivery methods stay. Returns the number of dates removed;
 * none for a campaign that is not a clone, an edit (parentId), a campaign type that does not offer Outside Household
 * ([offersOutsideHousehold], from campaignTypeOffersOutsideHousehold), or when the switch is off.
 */
export function dropCloneSourceDeliveryDates(campaignDetails: any, offersOutsideHousehold: boolean): number {
    const details = campaignDetails?.additionalDetails;
    if (!config.mapping?.clearCloneDeliveryDates || !campaignDetails || campaignDetails.parentId
        || !(details?.cloneFrom || details?.clonedCampaignId)) return 0;
    if (!offersOutsideHousehold) return 0;
    let removed = 0;
    const drop = (target: any, keys: string[]) => {
        for (const key of keys) {
            if (target && typeof target === "object" && target[key] !== undefined) {
                delete target[key];
                removed++;
            }
        }
    };
    const cycleDates = details?.cycleData?.cycleData;
    (Array.isArray(cycleDates) ? cycleDates : []).forEach((cycle: any) => drop(cycle, ["fromDate", "toDate"]));
    (Array.isArray(campaignDetails.deliveryRules) ? campaignDetails.deliveryRules : [])
        .forEach((rule: any) => (Array.isArray(rule?.cycles) ? rule.cycles : []).forEach((cycle: any) => drop(cycle, ["startDate", "endDate"])));
    return removed;
}

/**
 * A clone being created of a campaign type that does not offer Outside Household ([offersOutsideHousehold], from
 * campaignTypeOffersOutsideHousehold) starts without its source's delivery rules: cycles, dates, deliveries, conditions and
 * products (deliveryRules and additionalDetails.cycleData). The planner sets them in the delivery step, as for a new campaign.
 * Only for the clone's first save as a draft; nothing for a campaign that is not a clone, an edit (parentId), a campaign
 * without a type, or when the switch is off. Returns true when the source's rules were removed.
 */
export function dropCloneSourceDeliveryRules(campaignDetails: any, offersOutsideHousehold: boolean): boolean {
    const details = campaignDetails?.additionalDetails;
    if (!config.mapping?.clearCloneDeliveryDates || !campaignDetails || campaignDetails.parentId || campaignDetails.action !== "draft"
        || !(details?.cloneFrom || details?.clonedCampaignId)) return false;
    if (typeof campaignDetails.projectType !== "string" || !campaignDetails.projectType.trim()
        || offersOutsideHousehold) return false;
    const cycleData = details?.cycleData;
    const hadRules = (Array.isArray(campaignDetails.deliveryRules) && campaignDetails.deliveryRules.length > 0)
        || (cycleData && typeof cycleData === "object" && Object.keys(cycleData).length > 0);
    campaignDetails.deliveryRules = [];
    campaignDetails.additionalDetails = { ...(details || {}), cycleData: {} };
    return !!hadRules;
}

/**
 * The update's deliveryMethods and projectType for the generate request, so excel-ingestion decides the Outside Household
 * sheet from this update rather than from a stored copy that may not have it yet; empty when the update carries neither.
 */
export function generationDetailsFromUpdate(campaignDetails: any, previousFileStoreId?: string | null, earlierFileStoreIds?: string[] | null): Record<string, any> {
    const details: Record<string, any> = {};
    const methods = campaignDetails?.additionalDetails?.cycleData?.deliveryMethods;
    if (Array.isArray(methods)) details.deliveryMethods = methods;
    if (typeof campaignDetails?.projectType === "string" && campaignDetails.projectType.trim()) details.projectType = campaignDetails.projectType;
    // excel-ingestion copies this file's users, targets and facilities into the new template
    if (previousFileStoreId) details.previousFileStoreId = previousFileStoreId;
    // ... and its Outside Household rows from the latest of these that has any, when the sheet comes back
    if (previousFileStoreId && Array.isArray(earlierFileStoreIds) && earlierFileStoreIds.length > 0) details.earlierFileStoreIds = earlierFileStoreIds;
    return details;
}

/** additionalDetails key: the file whose data the templates keep when Outside Household is added or dropped, with its campaign's id. */
export const KEPT_FILE_KEY = "fileKeptOnOutsideHouseholdChange";

/** The kept file of this campaign itself (not one copied over from a cloned or edited campaign), or null. */
export function keptFile(campaign: any): string | null {
    const kept = campaign?.additionalDetails?.[KEPT_FILE_KEY];
    return kept?.campaignId && kept.campaignId === campaign?.id && typeof kept.fileStoreId === "string" && kept.fileStoreId ? kept.fileStoreId : null;
}

/** True when the update keeps the saved hierarchy and boundaries (an update that sends no boundaries keeps them). */
function sameAreaAsSaved(existing: any, incoming: any): boolean {
    if (incoming.hierarchyType && existing.hierarchyType && incoming.hierarchyType !== existing.hierarchyType) return false;
    const codes = (c: any) => new Set<string>((Array.isArray(c?.boundaries) ? c.boundaries : []).map((b: any) => b?.code).filter(Boolean));
    const before = codes(existing), after = codes(incoming);
    return after.size === 0 || (after.size === before.size && [...after].every(code => before.has(code)));
}

/** True when the update sends a unified file the campaign does not have yet (attached or kept). */
export function bringsNewFile(existing: any, incoming: any): boolean {
    const known = new Set((Array.isArray(existing.resources) ? existing.resources : []).filter(isUnifiedFile).map((r: any) => r.filestoreId));
    const kept = keptFile(existing);
    if (kept) known.add(kept);
    return (Array.isArray(incoming.resources) ? incoming.resources : []).some((r: any) => isUnifiedFile(r) && !known.has(r.filestoreId));
}

/**
 * The campaign's file from before a save that adds or drops Outside Household, so the new template keeps its users,
 * targets and facilities (only the Outside Household sheet follows the new choice): the attached file, or else the file
 * kept by an earlier change (that save detached it). Null when the switch is off, the choice did not change, the hierarchy
 * or boundaries changed, the campaign has no such file, the save brings a new file, or the campaign type does not offer
 * Outside Household in MDMS.
 */
export async function fileBeforeOutsideHouseholdChange(request: any): Promise<string | null> {
    const existing = request?.body?.ExistingCampaignDetails;
    const incoming = request?.body?.CampaignDetails;
    if (!config.mapping?.keepDataOnOutsideHouseholdChange || !existing || !incoming || !isOutsideHouseholdDeliveryChanged(request)) return null;
    if (!sameAreaAsSaved(existing, incoming) || bringsNewFile(existing, incoming)) return null;
    if (!(await campaignTypeOffersOutsideHousehold(incoming.tenantId || existing.tenantId, incoming.projectType || existing.projectType))) return null;
    const existingFiles = (Array.isArray(existing.resources) ? existing.resources : []).filter(isUnifiedFile);
    return existingFiles.length > 0 ? existingFiles[0].filestoreId : keptFile(existing);
}

/**
 * Saves on the campaign the file whose data its templates keep when Outside Household is added or dropped, since the save
 * that changes the choice detaches the file: this save's file before the change, else the saved one (the console's saves
 * leave it out). Dropped when the save changes the hierarchy or boundaries (the data is uploaded again for the new area)
 * or brings a new file (the next change keeps that one). Returns the kept file id, or null.
 */
export function keepFileForOutsideHouseholdChange(request: any, fileBefore: string | null): string | null {
    const existing = request?.body?.ExistingCampaignDetails;
    const incoming = request?.body?.CampaignDetails;
    if (!config.mapping?.keepDataOnOutsideHouseholdChange || !incoming) return null;
    const saved = existing ? keptFile(existing) : null;
    const file = fileBefore || (saved && sameAreaAsSaved(existing, incoming) && !bringsNewFile(existing, incoming) ? saved : null);
    const campaignId = incoming.id || existing?.id;
    if (file && campaignId) {
        incoming.additionalDetails = { ...(incoming.additionalDetails || {}), [KEPT_FILE_KEY]: { campaignId, fileStoreId: file } };
        return file;
    }
    if (incoming.additionalDetails && KEPT_FILE_KEY in incoming.additionalDetails) {
        const rest = { ...incoming.additionalDetails };
        delete rest[KEPT_FILE_KEY];
        incoming.additionalDetails = rest;
    }
    return null;
}

/** True when the stored campaign already holds the update's Outside Household choice (or the update carries no deliveryMethods list), so a template generated now gets the right sheets. */
export function isOutsideHouseholdDeliveryPersisted(incomingCampaign: any, persistedCampaign: any): boolean {
    const incoming = hasOutsideHouseholdDelivery(incomingCampaign);
    if (incoming === null) return true;
    return (hasOutsideHouseholdDelivery(persistedCampaign) ?? false) === incoming;
}

/** Delegates template generation to the excel-ingestion service; swallows errors so a generate failure never blocks the caller. */
export async function callExcelIngestionService(requestBody: any, referenceIdOverride?: string, referenceTypeOverride?: string, typeOverride?: string, additionalDetailsOverride?: Record<string, any>) {
    try {
        const campaignDetails = requestBody?.CampaignDetails;
        const tenantId = campaignDetails?.tenantId;
        const campaignId = campaignDetails?.id;
        const hierarchyType = campaignDetails?.hierarchyType;
        const excelIngestionUrl = config.host.excelIngestionHost + config.paths.excelIngestionGenerate;

        const generateResource = {
            tenantId: tenantId,
            type: typeOverride || 'unified-console',
            hierarchyType: hierarchyType,
            locale: getLocaleFromRequestInfo (requestBody?.RequestInfo),
            referenceId: referenceIdOverride || campaignId,
            referenceType: referenceTypeOverride || 'campaign',
            additionalDetails: additionalDetailsOverride || {}
        };

        const requestBodyToCallGenerate = {
            RequestInfo: requestBody?.RequestInfo,
            GenerateResource: generateResource
        };

        await httpRequest(
            excelIngestionUrl,
            requestBodyToCallGenerate,
            undefined,
            'post',
            undefined
        );
        logger.info(`Successfully called excel-ingestion generate API for campaign ${campaignId}`);
    } catch (error: any) {
        logger.error(`Error calling excel-ingestion generate API: ${error.message}`);
    }
}

/** A campaign's newest template record that is not expired: none, or its state, id, start time and (when finished) finish time. */
export type LatestTemplate = { state: "none" } | { state: "inProgress" | "completed" | "failed"; id: GenerationId; startedAt: EpochMs; finishedAt: EpochMs };

/** A template started for a campaign: its generation id and the time excel-ingestion started it. */
export type StartedTemplate = { id: GenerationId; startedAt: EpochMs };

const TEMPLATE_IN_PROGRESS_STATUSES = new Set(["queued", "pending", "in_progress"]);

// A new template expires the older ones, so a campaign normally has one record that is not expired; a few cover a race
const TEMPLATE_RECORDS_READ = 5;

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === "object" && value !== null;

const auditTime = (record: Record<string, unknown>, key: "createdTime" | "lastModifiedTime"): number | undefined => {
    const value = isRecord(record.auditDetails) ? record.auditDetails[key] : undefined;
    return typeof value === "number" || typeof value === "string" ? Number(value) : undefined;
};

const startedAt = (record: Record<string, unknown>): number => auditTime(record, "createdTime") ?? Number(record.createdTime ?? 0);

// excel-ingestion sets createdTime at _init only; finishing the template updates lastModifiedTime
const finishedAt = (record: Record<string, unknown>): number => auditTime(record, "lastModifiedTime") ?? startedAt(record);

/** A campaign's newest unified template, from its records that are not expired, whatever order the search returns them in. Throws when the search fails. */
export async function latestUnifiedTemplate(requestInfo: RequestInfo | undefined, tenantId: TenantId, campaignId: CampaignId): Promise<LatestTemplate> {
    const url = config.host.excelIngestionHost + config.paths.excelIngestionGenerateSearch;
    const response: unknown = await httpRequest(url, {
        RequestInfo: requestInfo,
        GenerationSearchCriteria: {
            tenantId,
            referenceIds: [campaignId],
            referenceTypes: ["campaign"],
            types: ["unified-console"],
            statuses: ["queued", "pending", "in_progress", "completed", "failed"],
            limit: TEMPLATE_RECORDS_READ,
            offset: 0
        }
    }, undefined, "post", undefined);
    const records = isRecord(response) && Array.isArray(response.GenerationDetails) ? response.GenerationDetails.filter(isRecord) : [];
    if (records.length === 0) return { state: "none" };
    const newest = records.reduce((latest, record) => (startedAt(record) > startedAt(latest) ? record : latest));
    const state = typeof newest.status === "string" && TEMPLATE_IN_PROGRESS_STATUSES.has(newest.status) ? "inProgress"
        : newest.status === "failed" ? "failed" : "completed";
    return { state, id: String(newest.id ?? "") as GenerationId, startedAt: startedAt(newest) as EpochMs, finishedAt: finishedAt(newest) as EpochMs };
}

/** Starts a campaign's unified template with the request callExcelIngestionService sends without overrides, and returns the new template; throws when it cannot be started. */
export async function startUnifiedTemplate(requestBody: any, details: Record<string, any>): Promise<StartedTemplate> {
    const campaignDetails = requestBody?.CampaignDetails;
    const response: unknown = await httpRequest(config.host.excelIngestionHost + config.paths.excelIngestionGenerate, {
        RequestInfo: requestBody?.RequestInfo,
        GenerateResource: {
            tenantId: campaignDetails?.tenantId,
            type: 'unified-console',
            hierarchyType: campaignDetails?.hierarchyType,
            locale: getLocaleFromRequestInfo(requestBody?.RequestInfo),
            referenceId: campaignDetails?.id,
            referenceType: 'campaign',
            additionalDetails: details
        }
    }, undefined, 'post', undefined);
    const started = isRecord(response) && isRecord(response.GenerateResource) ? response.GenerateResource : {};
    if (typeof started.id !== "string" || !started.id) throw new Error("excel-ingestion did not return the new template's id");
    return { id: started.id as GenerationId, startedAt: Number(started.createdTime ?? Date.now()) as EpochMs };
}

/** Queries excel-ingestion for the completed generation result of a campaign's template. */
export async function callExcelIngestionGenerateSearch(requestBody: any) {
    try {
        const excelIngestionSearchUrl = config.host.excelIngestionHost + config.paths.excelIngestionGenerateSearch;
        const requestInfo = requestBody?.RequestInfo;
        const campaignDetails = requestBody?.CampaignDetails;


        const requestBodyToCallSearch = {
            RequestInfo: requestInfo,
            GenerationSearchCriteria: {
                tenantId: campaignDetails?.tenantId,
                referenceIds: [campaignDetails?.id],
                statuses: ["completed"],
            }
        };

        const response = await httpRequest(
            excelIngestionSearchUrl,
            requestBodyToCallSearch,
            undefined,
            'post',
            undefined
        );

        logger.info(`Successfully called excel-ingestion generate search API for referenceIds: ${campaignDetails?.id}`);
        return response;
    } catch (error: any) {
        logger.error(`Error calling excel-ingestion generate search API: ${error.message}`);
        throw error;
    }
}

async function callGenerateIfBoundariesOrCampaignTypeDiffer(request: any) {
    try {
        // Let the just-persisted campaign changes settle before regenerating templates
        await new Promise(resolve => setTimeout(resolve, 2000));
        const campaignDetails = request?.body?.CampaignDetails;
        const tenantId = campaignDetails?.tenantId;
        const campaignId = campaignDetails?.id;
        const hierarchyType = campaignDetails?.hierarchyType;
        const useruuid = request?.body?.RequestInfo?.userInfo?.uuid || campaignDetails?.auditDetails?.createdBy;
        const locale = getLocaleFromRequestInfo(request?.body?.RequestInfo);

        const isMicroplan = checkIfSourceIsMicroplan(campaignDetails);
        const isUnifiedCampaign = campaignDetails?.additionalDetails?.isUnifiedCampaign;
        if(isUnifiedCampaign){
            await callExcelIngestionService(request?.body, undefined, undefined, undefined, generationDetailsFromUpdate(campaignDetails, request?.body?.fileBeforeOutsideHouseholdChange, request?.body?.earlierFilesBeforeOutsideHouseholdChange));
            return;
        }

        if (isMicroplan) {
            const types = ["boundary", "facilityWithBoundary"];
            for (const t of types) {
                const newRequestToGenerate = {
                    ...request,
                    query: {
                        ...request.query,
                        type: t,
                        tenantId,
                        hierarchyType,
                        campaignId,
                        forceUpdate: 'true'
                    }
                };
                await callGenerate(newRequestToGenerate, t);
            }
        } else {
            const requestInfo = request?.body?.RequestInfo;
            triggerGenerate("boundary", tenantId, hierarchyType, campaignId, useruuid, locale, requestInfo);
            triggerGenerate("user", tenantId, hierarchyType, campaignId, useruuid, locale, requestInfo);
            triggerGenerate("facility", tenantId, hierarchyType, campaignId, useruuid, locale, requestInfo);
        }
    } catch (error: any) {
        logger.error(error);
    }
}

function isSourceDifferent(request: any){
    const ExistingCampaignDetails = request?.body?.ExistingCampaignDetails;
    const CampaignDetails = request?.body?.CampaignDetails;

    if(CampaignDetails?.additionalDetails?.source !== ExistingCampaignDetails?.additionalDetails?.source){
        return true;
    }
    return false;
}

/** Runs the template generate flow for a type; the *WithBoundary types additionally attach localized boundary sheet data. */
export async function callGenerate(request: any, type: any, enableCaching = false) {
    logger.info(`calling generate api for type ${type}`);
    if (type === "facilityWithBoundary" || type == "userWithBoundary") {
        const { hierarchyType } = request.query;
        const localizationMapHierarchy = hierarchyType && await getLocalizedMessagesHandler(
            request,
            request.query.tenantId,
            getLocalisationModuleName(hierarchyType)
        );
        const localizationMapModule = await getLocalizedMessagesHandler(request, request.query.tenantId);
        const localizationMap = { ...localizationMapHierarchy, ...localizationMapModule };
        const filteredBoundary = await getBoundarySheetData(request, localizationMap);
        await processGenerate(request, enableCaching, filteredBoundary);
    } else {
        await processGenerate(request, enableCaching);
    }
}

/** Fire-and-forget generate for one resource type; logs and swallows errors so one type's failure doesn't abort the others. */
export async function triggerGenerate(type: string, tenantId: string, hierarchyType: string, campaignId: string, userUuid: string, locale: string = config.localisation.defaultLocale, requestInfo?: RequestInfo) {

    logger.info(`Calling generate API for type ${type}`);

    const generateRequestQuery = {
        type,
        tenantId,
        hierarchyType,
        campaignId
    };

    try {
        await generateDataService(generateRequestQuery, userUuid, locale, requestInfo);
    } catch (error: any) {
        logger.error(`Error in triggerGenerate for type ${type}: ${error?.message}`, error);
    }
}



/** True (with the new boundaries) when a campaign update changed boundaries, source, campaign type or the Outside Household delivery method and templates must be regenerated. */
export const isGenerationTriggerNeeded = (request: any) => {
    const ExistingCampaignDetails = request?.body?.ExistingCampaignDetails;
    const boundaries = request?.body?.CampaignDetails?.boundaries;
    const newBoundaries = boundaries?.filter((boundary: any) => !boundary.insertedAfter) || [];


    if (!areBoundariesSame(ExistingCampaignDetails?.boundaries, newBoundaries) || isSourceDifferent(request) || !isCampaignTypeSame(request)
        || isOutsideHouseholdDeliveryChanged(request)) {
        logger.info("Boundaries, Campaign Type or the Outside Household delivery method differ, generating new resources");
        return { trigger: true, newBoundaries };
    }
    return { trigger: false };
}



export { callGenerateIfBoundariesOrCampaignTypeDiffer, areBoundariesSame }
