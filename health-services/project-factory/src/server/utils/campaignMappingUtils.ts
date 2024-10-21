import createAndSearch from "../config/createAndSearch";
import config from "../config";
import { getDataFromSheet, getLocalizedMessagesHandlerViaRequestInfo, throwError } from "./genericUtils";
import { getFormattedStringForDebug, logger } from "./logger";
import { defaultheader, httpRequest } from "./request";
import { produceModifiedMessages } from "../kafka/Producer";
import { enrichAndPersistCampaignWithError, getLocalizedName } from "./campaignUtils";
import { campaignStatuses, resourceDataStatuses } from "../config/constants";
import { createCampaignService } from "../service/campaignManageService";
import { persistTrack } from "./processTrackUtils";
import { processTrackTypes, processTrackStatuses } from "../config/constants";
import { createProjectFacilityHelper, createProjectResourceHelper, createStaffHelper } from "../api/genericApis";
import { delinkAndLinkResourcesWithProjectCorrespondingToGivenBoundary } from "./onGoingCampaignUpdateUtils";


async function createBoundaryWithProjectMapping(projects: any, boundaryWithProject: any) {
    for (const project of projects) {
        if (project?.address?.boundary) {
            boundaryWithProject[project?.address?.boundary] = project?.id
        }
        if (project?.descendants && Array.isArray(project?.descendants) && project?.descendants?.length > 0) {
            await createBoundaryWithProjectMapping(project?.descendants, boundaryWithProject)
        }
    }
}

function getPvarIds(messageObject: any) {
    //update to set now
    logger.info("campaign product resource mapping started");
    const deliveryRules = messageObject?.CampaignDetails?.deliveryRules;
    const uniquePvarIds = new Set(); // Create a Set to store unique pvar IDs
    if (deliveryRules) {
        for (const deliveryRule of deliveryRules) {
            const products = deliveryRule?.resources;
            if (products) {
                for (const product of products) {
                    uniquePvarIds.add(product?.productVariantId); // Add pvar ID to the Set
                }
            }
        }
    }
    logger.info("campaign product resource found items  : " + JSON.stringify(uniquePvarIds));
    return Array.from(uniquePvarIds); // Convert Set to array before returning
}

function trimBoundaryCodes(root: any) {
    if (root) {
        root.code = root.code.trim(); // Trim the code

        // Recursively trim the codes in the children
        for (const child of root.children) {
            trimBoundaryCodes(child);
        }
    }
}

async function getAllBoundaries(messageObject: any, tenantId: any, rootBoundary: any, hierarchyType: any) {
    const BoundarySearchBody = {
        RequestInfo: messageObject?.RequestInfo,
    }
    const params = {
        tenantId,
        codes: rootBoundary,
        hierarchyType,
        includeChildren: true
    }
    const header = {
        ...defaultheader,
        cachekey: `boundaryRelationShipSearch${params?.hierarchyType}${params?.tenantId}${params.codes || ''}${params?.includeChildren || ''}`,
    }
    const boundaryResponse = await httpRequest(config.host.boundaryHost + config.paths.boundaryRelationship, BoundarySearchBody, params, undefined, undefined, header);
    trimBoundaryCodes(boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0]);
    return boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0]
}

// Function to find the path to a given boundary code
function findPath(root: any, code: string, path: any[] = []) {
    if (root.code === code) {
        return [...path, root];
    }
    for (const child of root.children) {
        const result: any = findPath(child, code, [...path, root]);
        if (result) return result;
    }
    return null;
}

// Function to find the common parent for multiple codes
function findCommonParent(codes: string[], root: any) {
    if (codes.length === 0) return null;

    // Find paths for all codes
    const paths = codes.map(code => findPath(root, code)).filter(path => path !== null);

    if (paths.length === 0) return null;

    // Compare paths to find the common ancestor
    let commonParent: any = null;

    for (let i = 0; i < Math.min(...paths.map(path => path.length)); i++) {
        const currentParent = paths[0][i];
        if (paths.every(path => path[i] && path[i].code === currentParent.code)) {
            commonParent = currentParent;
        } else {
            break;
        }
    }

    return commonParent?.code;
}

function mapBoundaryCodes(resource: any, code: string, boundaryCode: string, boundaryCodes: any, allBoundaries: any) {
    // Split boundary codes if they have comma separated values
    const boundaryCodesArray = boundaryCode
        ? boundaryCode.includes(',')
            ? boundaryCode.split(',').map((bc: string) => bc.trim()).filter(Boolean)
            : [boundaryCode.trim()]
        : [];
    if (resource?.type == "user" && boundaryCodesArray?.length > 1 && config.user.mapUserViaCommonParent) {
        const commonParent = findCommonParent(boundaryCodesArray, allBoundaries);
        if (commonParent) {
            logger.info(`Boundary Codes Array ${boundaryCodesArray.join(",")} for resource ${resource?.type} has common parent ${commonParent}`)
            if (!boundaryCodes[resource?.type]) {
                boundaryCodes[resource?.type] = {};
            }
            if (!boundaryCodes[resource?.type][commonParent]) {
                boundaryCodes[resource?.type][commonParent] = [];
            }
            boundaryCodes[resource?.type][commonParent].push(code);
            logger.info(`Common Parent Boundary code ${commonParent} mapped to resource ${resource?.type} with code ${code}`)
        }
    }
    else {
        boundaryCodesArray.forEach((trimmedBC: string) => {
            // Trim any leading or trailing spaces
            if (!boundaryCodes[resource?.type]) {
                boundaryCodes[resource?.type] = {};
            }
            if (!boundaryCodes[resource?.type][trimmedBC]) {
                boundaryCodes[resource?.type][trimmedBC] = [];
            }
            boundaryCodes[resource?.type][trimmedBC].push(code);
            logger.info(`Boundary code ${trimmedBC} mapped to resource ${resource?.type} with code ${code}`)
        });
    }
}

async function enrichBoundaryCodes(resources: any[], messageObject: any, boundaryCodes: any, sheetName: any) {
    const localizationMap: any = messageObject?.localizationMap
    const allBoundaries = await getAllBoundaries(messageObject, messageObject?.Campaign?.tenantId, messageObject?.Campaign?.boundaryCode, messageObject?.Campaign?.hierarchyType);
    const delinkOperations: any = [];
    const linkOperations: any = [];
    for (const resource of resources) {
        const processedFilestoreId = resource?.processedFilestoreId;
        if (processedFilestoreId) {
            const dataFromSheet: any = await getDataFromSheet(messageObject, processedFilestoreId, messageObject?.Campaign?.tenantId, undefined, sheetName[resource?.type], localizationMap);
            for (const data of dataFromSheet) {
                const uniqueCodeColumn = getLocalizedName(createAndSearch?.[resource?.type]?.uniqueIdentifierColumnName, localizationMap)
                const code = data[uniqueCodeColumn];
                if (code) {
                    // Extract boundary codes
                    const boundaryCode = data[getLocalizedName(createAndSearch?.[resource?.type]?.boundaryValidation?.column, localizationMap)];
                    var active: any = "Active";
                    if (createAndSearch?.[resource?.type]?.activeColumn && createAndSearch?.[resource?.type]?.activeColumnName) {
                        var activeColumn = getLocalizedName(createAndSearch?.[resource?.type]?.activeColumnName, localizationMap);
                        active = data[activeColumn];
                    }
                    if (boundaryCode && active === "Active") {
                        if (!messageObject?.parentCampaign) {
                            mapBoundaryCodes(resource, code, boundaryCode, boundaryCodes, allBoundaries);
                        }
                        else {
                            const existingBoundaryColumnRaw = data[getLocalizedName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE_OLD", localizationMap)];
                            const existingBoundaryColumn = existingBoundaryColumnRaw ?
                                (existingBoundaryColumnRaw.includes(',') ?
                                    existingBoundaryColumnRaw.split(',').map((code: string) => code.trim()).filter(Boolean) :
                                    [existingBoundaryColumnRaw.trim()].filter(Boolean)) : [];
                            const newBoundaryColumn = boundaryCode.includes(',') ?
                                boundaryCode.split(',').map((code: string) => code.trim()).filter(Boolean) :
                                [boundaryCode.trim()].filter(Boolean);

                            existingBoundaryColumn.forEach((boundary: any) => {
                                if (!newBoundaryColumn.includes(boundary)) {
                                    delinkOperations.push({
                                        resource, messageObject, boundary, code, isDelink: true
                                    });
                                }
                            });

                            // Collect link operations for new boundaries
                            newBoundaryColumn.forEach((boundary: any) => {
                                linkOperations.push({
                                    resource, messageObject, boundary, code, isDelink: false
                                });
                            });
                        }
                    }
                }
                else {
                    logger.info(`Code ${code} is somehow null or empty for resource ${resource?.type} for uniqueCodeColumn ${uniqueCodeColumn}`)
                }
            }
        }
    }

    await Promise.all(delinkOperations.map(async (delinkData: any) => {
        const isMappingAlreadyPresent = await delinkAndLinkResourcesWithProjectCorrespondingToGivenBoundary(
            delinkData.resource,
            delinkData.messageObject,
            delinkData.boundary,
            delinkData.code,
            delinkData.isDelink
        );
        logger.info("Delink operation complete, mapping present:", isMappingAlreadyPresent);
    }));

    await Promise.all(linkOperations.map(async (linkData: any) => {
        const isMappingAlreadyPresent = await delinkAndLinkResourcesWithProjectCorrespondingToGivenBoundary(
            linkData.resource,
            linkData.messageObject,
            linkData.boundary,
            linkData.code,
            linkData.isDelink
        );
        if (!isMappingAlreadyPresent) {
            mapBoundaryCodes(linkData.resource, linkData.code, linkData.boundaryCode, boundaryCodes, allBoundaries);
        }
    }));
}


async function enrichBoundaryWithProject(messageObject: any, boundaryWithProject: any, boundaryCodes: any) {
    const projectSearchBody = {
        RequestInfo: messageObject?.RequestInfo,
        Projects: [{
            id: messageObject?.Campaign?.rootProjectId,
            tenantId: messageObject?.Campaign?.tenantId
        }],
        apiOperation: "SEARCH"
    }
    const params = {
        tenantId: messageObject?.Campaign?.tenantId,
        offset: 0,
        limit: 100,
        includeDescendants: true
    }
    logger.info("params : " + JSON.stringify(params));
    logger.info("boundaryCodes : " + JSON.stringify(boundaryCodes));
    const response = await httpRequest(config.host.projectHost + config.paths.projectSearch, projectSearchBody, params);
    await createBoundaryWithProjectMapping(response?.Project, boundaryWithProject);
    logger.debug(`boundaryWise Project mapping : ${getFormattedStringForDebug(boundaryWithProject)}`);
    logger.info("boundaryCodes mapping : " + JSON.stringify(boundaryCodes));
}

async function getProjectMappingBody(messageObject: any, boundaryWithProject: any, boundaryCodes: any) {
    const Campaign: any = {
        id: messageObject?.Campaign?.id,
        tenantId: messageObject?.Campaign?.tenantId,
        CampaignDetails: []
    }
    for (const key of Object.keys(boundaryWithProject)) {
        if (boundaryWithProject[key]) {
            const resources: any[] = [];
            const pvarIds = getPvarIds(messageObject);
            if (pvarIds && Array.isArray(pvarIds) && pvarIds.length > 0) {
                resources.push({
                    type: "resource",
                    resourceIds: pvarIds
                })
            }
            for (const type of Object.keys(boundaryCodes)) {
                if (boundaryCodes[type][key] && Array.isArray(boundaryCodes[type][key]) && boundaryCodes[type][key].length > 0) {
                    resources.push({
                        type: type == "user" ? "staff" : type,
                        resourceIds: [...boundaryCodes[type][key]]
                    })
                }
            }
            Campaign.CampaignDetails.push({
                projectId: boundaryWithProject[key],
                resources: resources
            })
        }
    }
    return {
        RequestInfo: messageObject?.RequestInfo,
        Campaign: Campaign,
        CampaignDetails: messageObject?.CampaignDetails
    }
}

async function fetchAndMap(resources: any[], messageObject: any) {
    await persistTrack(messageObject?.Campaign?.id, processTrackTypes.prepareResourceForMapping, processTrackStatuses.inprogress)
    const localizationMap = await getLocalizedMessagesHandlerViaRequestInfo(messageObject?.RequestInfo, messageObject?.Campaign?.tenantId);
    messageObject.localizationMap = localizationMap
    try {
        const localizationMap = messageObject?.localizationMap;
        const sheetName: any = {
            "user": getLocalizedName(createAndSearch?.user?.parseArrayConfig?.sheetName, localizationMap),
            "facility": getLocalizedName(createAndSearch?.facility?.parseArrayConfig?.sheetName, localizationMap)
        }
        // Object to store boundary codes
        const boundaryCodes: any = {};

        await enrichBoundaryCodes(resources, messageObject, boundaryCodes, sheetName);
        logger.info("boundaryCodes : " + JSON.stringify(boundaryCodes));
        var boundaryWithProject: any = {};
        await enrichBoundaryWithProject(messageObject, boundaryWithProject, boundaryCodes);
        logger.info("boundaryWithProject : " + JSON.stringify(boundaryWithProject));
        var projectMappingBody = await getProjectMappingBody(messageObject, boundaryWithProject, boundaryCodes);
        logger.info("projectMappingBody : " + JSON.stringify(projectMappingBody));
        logger.info("projectMapping started ");
    } catch (error: any) {
        console.log(error)
        await persistTrack(messageObject?.Campaign?.id, processTrackTypes.prepareResourceForMapping, processTrackStatuses.failed, { error: String((error?.message + (error?.description ? ` : ${error?.description}` : '')) || error) });
        throw new Error(error)
    }
    await persistTrack(messageObject?.Campaign?.id, processTrackTypes.prepareResourceForMapping, processTrackStatuses.completed)
    await createCampaignService(projectMappingBody);
}

async function searchResourceDetailsById(resourceDetailId: string, messageObject: any) {
    var searchBody = {
        RequestInfo: messageObject?.RequestInfo,
        SearchCriteria: {
            id: [resourceDetailId],
            tenantId: messageObject?.Campaign?.tenantId
        }
    }
    const response: any = await httpRequest(config.host.projectFactoryBff + "project-factory/v1/data/_search", searchBody);
    return response?.ResourceDetails?.[0];
}

async function validateMappingId(messageObject: any, id: string) {
    const searchBody = {
        RequestInfo: messageObject?.RequestInfo,
        CampaignDetails: {
            ids: [id],
            tenantId: messageObject?.Campaign?.tenantId,
        }
    }
    const response: any = await httpRequest(config.host.projectFactoryBff + "project-factory/v1/project-type/search", searchBody);
    if (!response?.CampaignDetails?.[0]) {
        throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "Campaign with id " + id + " does not exist");
    }
    return response?.CampaignDetails?.[0];
}

async function processCampaignMapping(messageObject: any) {
    const resourceDetailsIds = messageObject?.Campaign?.resourceDetailsIds
    const id = messageObject?.Campaign?.id
    if (!id) {
        throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "Campaign id is missing");
    }
    const campaignDetails = await validateMappingId(messageObject, id);
    if (campaignDetails?.status == campaignStatuses.inprogress) {
        logger.info("Campaign Already In Progress and Mapped");
    }
    else {
        await persistTrack(id, processTrackTypes.confirmingResourceCreation, processTrackStatuses.inprogress);
        try {
            var completedResources: any = []
            var resources = [];
            for (const resourceDetailId of resourceDetailsIds) {
                var retry = 75;
                while (retry--) {
                    const response = await searchResourceDetailsById(resourceDetailId, messageObject);
                    logger.info(`response for resourceDetailId: ${resourceDetailId}`);
                    logger.debug(` response : ${getFormattedStringForDebug(response)}`)
                    if (response?.status == "invalid") {
                        logger.error(`resource type ${response?.type} is invalid`);
                        throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "Data File for resource type " + response?.type + " is invalid");
                        break;
                    }
                    else if (response?.status == resourceDataStatuses.failed) {
                        logger.error(`resource type ${response?.type} is ${resourceDataStatuses.failed}`);
                        throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", `Resource creation of type ${response?.type} failed : with errorlog ${response?.additionalDetails?.error}`);
                        break;
                    }
                    else if (response?.status == resourceDataStatuses.completed) {
                        completedResources.push(resourceDetailId);
                        resources.push(response);
                        break;
                    }
                    else {
                        logger.info(`Waiting for 20 seconds for resource with id ${resourceDetailId} on retry ${retry}`);
                        await new Promise(resolve => setTimeout(resolve, 20000));
                    }
                }
            }
            var uncompletedResourceIds = resourceDetailsIds?.filter((x: any) => !completedResources.includes(x));
            logger.info("uncompletedResourceIds " + JSON.stringify(uncompletedResourceIds));
            logger.info("completedResources " + JSON.stringify(completedResources));
            if (uncompletedResourceIds?.length > 0) {
                throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "resource with id " + JSON.stringify(uncompletedResourceIds) + " is not completed after long wait. Check file");
            }
        } catch (error: any) {
            console.log(error)
            await persistTrack(id, processTrackTypes.confirmingResourceCreation, processTrackStatuses.failed, { error: String((error?.message + (error?.description ? ` : ${error?.description}` : '')) || error) });
            throw new Error(error)
        }
        await persistTrack(id, processTrackTypes.confirmingResourceCreation, processTrackStatuses.completed);
        await fetchAndMap(resources, messageObject);
    }
}

export async function handleCampaignMapping(messageObject: any) {
    try {
        logger.info("Received a message for campaign mapping");
        logger.debug("Message Object of campaign mapping: " + getFormattedStringForDebug(messageObject));
        await processCampaignMapping(messageObject);
    } catch (error) {
        logger.error("Error in campaign mapping: " + error);
        await enrichAndPersistCampaignWithError(messageObject, error);
    }
}

export async function handleStaffMapping(mappingArray: any[], campaignId: string, messageObject: any) {
    await persistTrack(campaignId, processTrackTypes.staffMapping, processTrackStatuses.inprogress);
    try {
        const promises = []
        logger.debug("Array of staff mapping: " + getFormattedStringForDebug(mappingArray));
        for (const staffMapping of mappingArray) {
            const { resource, projectId, resouceBody, tenantId, startDate, endDate } = staffMapping;
            for (const resourceId of resource?.resourceIds) {
                promises.push(createStaffHelper(resourceId, projectId, resouceBody, tenantId, startDate, endDate))
            }
        }
        await Promise.all(promises);
    } catch (error: any) {
        logger.error("Error in staff mapping: " + error);
        await persistTrack(campaignId, processTrackTypes.staffMapping, processTrackStatuses.failed, { error: String((error?.message + (error?.description ? ` : ${error?.description}` : '')) || error) });
        await enrichAndPersistCampaignWithError(messageObject, error);
        throw new Error(error)
    }
    await persistTrack(campaignId, processTrackTypes.staffMapping, processTrackStatuses.completed);
}

export async function handleResourceMapping(mappingArray: any, campaignId: any, messageObject: any) {
    await persistTrack(campaignId, processTrackTypes.resourceMapping, processTrackStatuses.inprogress);
    try {
        const promises = []
        logger.debug("Arrray of resource mapping: " + getFormattedStringForDebug(mappingArray));
        for (const mapping of mappingArray) {
            const { resource, projectId, resouceBody, tenantId, startDate, endDate } = mapping;
            for (const resourceId of resource?.resourceIds) {
                promises.push(createProjectResourceHelper(resourceId, projectId, resouceBody, tenantId, startDate, endDate));
            }
        }
        await Promise.all(promises);
    } catch (error: any) {
        logger.error("Error in resource mapping: " + error);
        await persistTrack(campaignId, processTrackTypes.resourceMapping, processTrackStatuses.failed, { error: String((error?.message + (error?.description ? ` : ${error?.description}` : '')) || error) });
        await enrichAndPersistCampaignWithError(messageObject, error);
        throw new Error(error)
    }
    await persistTrack(campaignId, processTrackTypes.resourceMapping, processTrackStatuses.completed);
}

export async function handleFacilityMapping(mappingArray: any, campaignId: any, messageObject: any) {
    await persistTrack(campaignId, processTrackTypes.facilityMapping, processTrackStatuses.inprogress);
    try {
        const promises = []
        logger.debug("Array of facility mapping: " + getFormattedStringForDebug(mappingArray));
        for (const mapping of mappingArray) {
            const { resource, projectId, resouceBody, tenantId } = mapping;
            for (const resourceId of resource?.resourceIds) {
                promises.push(createProjectFacilityHelper(resourceId, projectId, resouceBody, tenantId));
            }
        }
        await Promise.all(promises);
    } catch (error: any) {
        logger.error("Error in facility mapping: " + error);
        await persistTrack(campaignId, processTrackTypes.facilityMapping, processTrackStatuses.failed, { error: String((error?.message + (error?.description ? ` : ${error?.description}` : '')) || error) });
        await enrichAndPersistCampaignWithError(messageObject, error);
        throw new Error(error)
    }
    await persistTrack(campaignId, processTrackTypes.facilityMapping, processTrackStatuses.completed);
}

export async function processMapping(mappingObject: any) {
    try {
        if (mappingObject?.mappingArray && Array.isArray(mappingObject?.mappingArray) && mappingObject?.mappingArray?.length > 0) {
            const resourceMappingArray = mappingObject?.mappingArray?.filter((mappingObject: any) => mappingObject?.type == "resource");
            const facilityMappingArray = mappingObject?.mappingArray?.filter((mappingObject: any) => mappingObject?.type == "facility");
            const staffMappingArray = mappingObject?.mappingArray?.filter((mappingObject: any) => mappingObject?.type == "staff");
            await handleResourceMapping(resourceMappingArray, mappingObject?.CampaignDetails?.id, mappingObject);
            await handleFacilityMapping(facilityMappingArray, mappingObject?.CampaignDetails?.id, mappingObject);
            await handleStaffMapping(staffMappingArray, mappingObject?.CampaignDetails?.id, mappingObject);
        }
        logger.info("Mapping completed successfully for campaign: " + mappingObject?.CampaignDetails?.id);
        mappingObject.CampaignDetails.status = campaignStatuses.inprogress
        const produceMessage: any = {
            CampaignDetails: mappingObject?.CampaignDetails
        }
        await produceModifiedMessages(produceMessage, config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC)
        await persistTrack(mappingObject?.CampaignDetails?.id, processTrackTypes.campaignCreation, processTrackStatuses.completed)
    } catch (error) {
        logger.error("Error in campaign mapping: " + error);
        await enrichAndPersistCampaignWithError(mappingObject, error);
    }
}


export {
    processCampaignMapping,
    validateMappingId
}
