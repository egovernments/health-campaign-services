import createAndSearch from "../config/createAndSearch";
import config from "../config";
import { getDataFromSheet, getLocalizedMessagesHandlerViaRequestInfo, replicateRequest, throwError } from "./genericUtils";
import { getFormattedStringForDebug, logger } from "./logger";
import { defaultheader, httpRequest } from "./request";
import { produceModifiedMessages } from "../kafka/Producer";
import { enrichAndPersistCampaignWithError, enrichAndPersistCampaignWithErrorProcessingTask, getLocalizedName } from "./campaignUtils";
import { allProcesses, campaignStatuses, processStatuses, resourceDataStatuses, usageColumnStatus } from "../config/constants";
import { createCampaignService, searchProjectTypeCampaignService } from "../service/campaignManageService";
import { persistTrack } from "./processTrackUtils";
import { processTrackTypes, processTrackStatuses } from "../config/constants";
import { createProjectFacilityHelper, createProjectResourceHelper, createProjectStaffHelper } from "../api/genericApis";
import { buildSearchCriteria, delinkAndLinkResourcesWithProjectCorrespondingToGivenBoundary, getResourceFromResourceId, processResources } from "./onGoingCampaignUpdateUtils";
import { searchDataService } from "../service/dataManageService";
import { getHierarchy } from "../api/campaignApis";
import { consolidateBoundaries } from "./boundariesConsolidationUtils";
import { startResourceMapping } from "./resourceMappingUtils";
import { startUserMappingAndDemapping } from "./userMappingUtils";
import { startFacilityMappingAndDemapping } from "./facilityMappingUtils";
import { sendNotificationEmail } from "./mailUtil";


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

export function getPvarIds(messageObject: any) {
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
    logger.info(`campaign product resource found items : ${JSON.stringify(uniquePvarIds)}`);
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


function splitBoundaryCodes(boundaryCode: string) {
    return boundaryCode.includes(',')
        ? boundaryCode.split(',').map((code: string) => code.trim()).filter(Boolean)
        : [boundaryCode.trim()].filter(Boolean);
}


async function fetchActiveIdentifiersFromParentCampaign(
    resource: any,
    resourcesFromParentCampaign: any[],
    messageObject: any,
    sheetName: any,
    localizationMap: any,
    activeColumn: string,
    uniqueCodeColumn: string
): Promise<any[]> {
    let activeUniqueIdentifiersFromParent: any[] = [];

    if (messageObject?.parentCampaign) {
        const matchingParentResource = resourcesFromParentCampaign.find(
            (parentResource: any) => parentResource.type === resource.type
        );

        if (matchingParentResource) {
            const parentCreateResourceId = matchingParentResource.createResourceId || '';
            const searchCriteria = buildSearchCriteria(messageObject, [parentCreateResourceId], matchingParentResource?.type);
            const responseFromDataSearch = await searchDataService(replicateRequest(messageObject, searchCriteria));

            const parentProcessedFileStoreId = responseFromDataSearch?.[0]?.processedFilestoreId;

            const parentResourceData: any = await getDataFromSheet(
                messageObject,
                parentProcessedFileStoreId,
                messageObject?.Campaign?.tenantId,
                undefined,
                sheetName[matchingParentResource?.type],
                localizationMap
            );

            activeUniqueIdentifiersFromParent = parentResourceData
                .filter((row: any) => row[activeColumn] === usageColumnStatus.active)
                .map((row: any) => row[uniqueCodeColumn]);
        }
    }

    return activeUniqueIdentifiersFromParent;
}


async function enrichBoundaryCodes(resources: any[], messageObject: any, boundaryCodes: any, sheetName: any) {
    const localizationMap: any = messageObject?.localizationMap
    const allBoundaries = await getAllBoundaries(messageObject, messageObject?.Campaign?.tenantId, messageObject?.Campaign?.boundaryCode, messageObject?.Campaign?.hierarchyType);
    const delinkOperations: any = [];
    const linkOperations: any = [];
    const resourcesFromParentCampaign = messageObject?.parentCampaign?.resources || [];
    for (const resource of resources) {
        const uniqueCodeColumn = getLocalizedName(createAndSearch?.[resource?.type]?.uniqueIdentifierColumnName, localizationMap)
        let activeColumn: any;
        if (createAndSearch?.[resource?.type]?.activeColumn && createAndSearch?.[resource?.type]?.activeColumnName) {
            activeColumn = getLocalizedName(createAndSearch?.[resource?.type]?.activeColumnName, localizationMap);
        }
        const processedFilestoreId = resource?.processedFilestoreId;
        const activeUniqueIdentifiersFromParent = await fetchActiveIdentifiersFromParentCampaign(
            resource,
            resourcesFromParentCampaign,
            messageObject,
            sheetName,
            localizationMap,
            activeColumn,
            uniqueCodeColumn
        );
        if (processedFilestoreId) {
            const dataFromSheet: any = await getDataFromSheet(messageObject, processedFilestoreId, messageObject?.Campaign?.tenantId, undefined, sheetName[resource?.type], localizationMap);
            for (const data of dataFromSheet) {
                const code = data[uniqueCodeColumn];
                if (code) {
                    // Extract boundary codes
                    const boundaryCode = data[getLocalizedName(createAndSearch?.[resource?.type]?.boundaryValidation?.column, localizationMap)];
                    let active: any = usageColumnStatus.active;
                    if (createAndSearch?.[resource?.type]?.activeColumn && createAndSearch?.[resource?.type]?.activeColumnName) {
                        active = data[activeColumn];
                    }
                    if (boundaryCode && active === usageColumnStatus.active) {
                        if (!messageObject?.parentCampaign) {
                            mapBoundaryCodes(resource, code, boundaryCode, boundaryCodes, allBoundaries);
                        }
                        else {
                            const existingBoundaryColumn = splitBoundaryCodes(
                                data[getLocalizedName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE_OLD", localizationMap)] || ""
                            );

                            const newBoundaryColumn = splitBoundaryCodes(boundaryCode);

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
                    else {
                        if (messageObject?.parentCampaign) {
                            if (boundaryCode && activeUniqueIdentifiersFromParent.includes(code) && active !== usageColumnStatus.active) {
                                const boundariesToBeDelinked = splitBoundaryCodes(data[getLocalizedName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE_OLD", localizationMap)] || "");
                                boundariesToBeDelinked.forEach((boundary: any) => {
                                    delinkOperations.push({
                                        resource, messageObject, boundary, code, isDelink: true
                                    });
                                });

                            }
                        }
                    }
                }
                else {
                    logger.info(`Code ${code} is somehow null or empty for resource ${resource?.type} for uniqueCodeColumn ${uniqueCodeColumn}`)
                }
            }
        }
    }

    // Process delink operations sequentially
    for (const delinkData of delinkOperations) {
        try {
            const isMappingAlreadyPresent = await delinkAndLinkResourcesWithProjectCorrespondingToGivenBoundary(
                delinkData.resource,
                delinkData.messageObject,
                delinkData.boundary,
                delinkData.code,
                delinkData.isDelink
            );
            logger.info(`Delinking ${delinkData.boundary} from ${delinkData.code} resource`);
            logger.info("Delink operation complete, mapping present:", isMappingAlreadyPresent);
        } catch (err: any) {
            logger.error(`Error during delink operation for ${delinkData.boundary}: ${err.message}`);
        }
    }

    // Process link operations sequentially
    for (const linkData of linkOperations) {
        try {
            const isMappingAlreadyPresent = await delinkAndLinkResourcesWithProjectCorrespondingToGivenBoundary(
                linkData.resource,
                linkData.messageObject,
                linkData.boundary,
                linkData.code,
                linkData.isDelink
            );
            if (!isMappingAlreadyPresent) {
                mapBoundaryCodes(linkData.resource, linkData.code, linkData.boundary, boundaryCodes, allBoundaries);
            }
        } catch (err: any) {
            logger.error(`Error during link operation for ${linkData.boundary}: ${err.message}`);
        }
    }
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
function filterBoundariesByHierarchy(hierarchy: any, boundaries: any) {
    // Iterate through the hierarchy in order
    for (const level of hierarchy) {
        // Find boundaries matching the current level type
        const matchingBoundaries = boundaries.filter((boundary: any) => boundary.type === level);

        if (matchingBoundaries.length > 0) {
            // If matches are found, return them
            return matchingBoundaries;
        }
    }

    // If no matches are found, return an empty array
    return [];
}

async function getProjectMappingBody(messageObject: any, boundaryWithProject: any, boundaryCodes: any) {
    const Campaign: any = {
        id: messageObject?.Campaign?.id,
        tenantId: messageObject?.Campaign?.tenantId,
        CampaignDetails: []
    }
    const newlyAddedBoundaryCodes = new Set(); // A set to store unique boundary codes
    if (messageObject?.CampaignDetails?.parentId) {
        const CampaignDetails = {
            "ids": [messageObject?.CampaignDetails?.id],
            "tenantId": messageObject?.CampaignDetails?.tenantId
        }
        const campaignSearchResponse = await searchProjectTypeCampaignService(CampaignDetails);
        const boundaries = campaignSearchResponse?.CampaignDetails?.[0]?.boundaries;
        const hierarchy = await getHierarchy(messageObject?.CampaignDetails?.tenantId, messageObject?.CampaignDetails?.hierarchyType);
        const boundariesWhichAreRootInThisFlow = filterBoundariesByHierarchy(hierarchy, boundaries);
        for (const boundary of boundariesWhichAreRootInThisFlow) {
            const boundaryCodesFetchedFromGivenRoot = await consolidateBoundaries(
                messageObject,
                messageObject?.CampaignDetails?.hierarchyType,
                messageObject?.CampaignDetails?.tenantId,
                boundary?.code,
                boundaries
            );
            // Add each boundary code to the set if it exists
            if (boundaryCodesFetchedFromGivenRoot &&
                Array.isArray(boundaryCodesFetchedFromGivenRoot) &&
                boundaryCodesFetchedFromGivenRoot.length > 0) {
                boundaryCodesFetchedFromGivenRoot
                    .filter((boundary: any) => boundary?.code) // Filter boundaries with valid codes
                    .forEach((boundary: any) => newlyAddedBoundaryCodes.add(boundary.code));
            }
        }
    }


    for (const key of Object.keys(boundaryWithProject)) {
        if (boundaryWithProject[key]) {
            const resources: any[] = [];
            if (messageObject?.CampaignDetails?.parentId && newlyAddedBoundaryCodes.has(key)) {
                logger.info("project resource mapping for newly created projects in update flow")
                const pvarIds = getPvarIds(messageObject);
                if (pvarIds && Array.isArray(pvarIds) && pvarIds.length > 0) {
                    resources.push({
                        type: "resource",
                        resourceIds: pvarIds
                    })
                }
            }

            if (!messageObject?.CampaignDetails?.parentId) {
                const pvarIds = getPvarIds(messageObject);
                if (pvarIds && Array.isArray(pvarIds) && pvarIds.length > 0) {
                    resources.push({
                        type: "resource",
                        resourceIds: pvarIds
                    })
                }
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
            });
        }
    }
    return {
        RequestInfo: messageObject?.RequestInfo,
        Campaign: Campaign,
        CampaignDetails: messageObject?.CampaignDetails,
        parentCampaign: messageObject?.parentCampaign
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
                var retry: any = config?.retryUntilResourceCreationComplete;
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

export async function handleStaffMapping(mappingArray: any[], campaignId: string, messageObject: any, type: string) {
    await persistTrack(campaignId, processTrackTypes.staffMapping, processTrackStatuses.inprogress);
    try {
        logger.debug(`staff mapping count: ${mappingArray.length}`);
        await processResourceOrFacilityOrUserMappingsInBatches(type, mappingArray, config?.batchSize || 100);
        // for (const staffMapping of mappingArray) {
        //     const { resource, projectId, resouceBody, tenantId, startDate, endDate } = staffMapping;
        //     for (const resourceId of resource?.resourceIds) {
        //         promises.push(createStaffHelper(resourceId, projectId, resouceBody, tenantId, startDate, endDate))
        //     }
        // }
    } catch (error: any) {
        logger.error("Error in staff mapping: " + error);
        await persistTrack(campaignId, processTrackTypes.staffMapping, processTrackStatuses.failed, { error: String((error?.message + (error?.description ? ` : ${error?.description}` : '')) || error) });
        await enrichAndPersistCampaignWithError(messageObject, error);
        throw new Error(error)
    }
    await persistTrack(campaignId, processTrackTypes.staffMapping, processTrackStatuses.completed);
}

async function processResourceOrFacilityOrUserMappingsInBatches(type: string, mappingArray: any, batchSize: number) {
    logger.info("Processing resource mappings in batches...");
    let promises: Promise<void>[] = [];
    let totalCreated = 0; // To keep track of the total number of created resources
    let batchCount = 0;   // To log batch-wise progress
    // Determine the helper function to use based on the type
    let createHelperFn: any;
    if (type === 'resource') {
        createHelperFn = createProjectResourceHelper;
    } else if (type === 'staff') {
        createHelperFn = createProjectStaffHelper;
    } else if (type === 'facility') {
        createHelperFn = createProjectFacilityHelper;
    } else {
        logger.error(`Unsupported type: ${type}`);
        return;  // Exit the function if the type is unsupported
    }

    for (const mapping of mappingArray) {
        const { resource, projectId, resouceBody, tenantId, startDate, endDate } = mapping;

        for (const resourceId of resource?.resourceIds || []) {
                promises.push(
                    createHelperFn(resourceId, projectId, resouceBody, tenantId, startDate, endDate).then(() => {
                        totalCreated++;
                    })
                );

            if (promises.length >= batchSize) {
                batchCount++;
                logger.info(`Processing batch ${batchCount} with ${promises.length} promises.`);
                try {
                    await Promise.all(promises); // Wait for all promises in the current batch
                } catch (error) {
                    logger.error(`Batch ${batchCount} failed:`, error);
                    throw error; // Ensure any error in the batch is propagated
                } promises = []; // Reset the array for the next batch
            }
        }
    }

    // Process any remaining promises
    if (promises.length > 0) {
        batchCount++;
        logger.info(`Processing final batch ${batchCount} with ${promises.length} promises.`);
        await Promise.all(promises);
    }

    logger.info(`Processing completed. Total resources created: ${totalCreated}`);
}


export async function handleResourceMapping(mappingArray: any[], campaignId: any, messageObject: any, type: string) {
    await persistTrack(campaignId, processTrackTypes.resourceMapping, processTrackStatuses.inprogress);
    try {
        logger.debug(`Resource mapping count: ${mappingArray.length}`);
        await processResourceOrFacilityOrUserMappingsInBatches(type, mappingArray, config?.batchSize || 100);
    } catch (error: any) {
        logger.error("Error in resource mapping: " + error);
        await persistTrack(campaignId, processTrackTypes.resourceMapping, processTrackStatuses.failed, { error: String((error?.message + (error?.description ? ` : ${error?.description}` : '')) || error) });
        await enrichAndPersistCampaignWithError(messageObject, error);
        throw new Error(error)
    }
    await persistTrack(campaignId, processTrackTypes.resourceMapping, processTrackStatuses.completed);
}

export async function handleFacilityMapping(mappingArray: any, campaignId: any, messageObject: any, type: string) {
    await persistTrack(campaignId, processTrackTypes.facilityMapping, processTrackStatuses.inprogress);
    try {
        logger.debug(`facility mapping count: ${mappingArray.length}`);
        // for (const mapping of mappingArray) {
        //     const { resource, projectId, resouceBody, tenantId } = mapping;
        //     for (const resourceId of resource?.resourceIds) {
        //         promises.push(createProjectFacilityHelper(resourceId, projectId, resouceBody, tenantId));
        //     }
        // }
        await processResourceOrFacilityOrUserMappingsInBatches(type, mappingArray, config?.batchSize || 100);
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
            await handleResourceMapping(resourceMappingArray, mappingObject?.CampaignDetails?.id, mappingObject, "resource");
            await handleFacilityMapping(facilityMappingArray, mappingObject?.CampaignDetails?.id, mappingObject, "facility");
            await handleStaffMapping(staffMappingArray, mappingObject?.CampaignDetails?.id, mappingObject, "staff");
        }
        logger.info("Mapping completed successfully for campaign: " + mappingObject?.CampaignDetails?.id);
        mappingObject.CampaignDetails.status = campaignStatuses.inprogress
        if (mappingObject?.parentCampaign) {
            await processResources(mappingObject);
            mappingObject.CampaignDetails.campaignDetails.boundaries = [
                ...mappingObject.CampaignDetails.campaignDetails.boundaries,
                ...mappingObject.parentCampaign.boundaries
            ];
        }
        const produceMessage: any = {
            CampaignDetails: mappingObject?.CampaignDetails
        }
        await produceModifiedMessages(produceMessage, config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC)
        await persistTrack(mappingObject?.CampaignDetails?.id, processTrackTypes.campaignCreation, processTrackStatuses.completed);

            logger.info("Step 1: Starting user credential email process for campaign ID: " + mappingObject?.CampaignDetails?.id);

            const resources = mappingObject?.CampaignDetails?.resources || [];
            logger.info("Step 2: Extracted resources. Count: " + resources.length);

            const userResource = resources.find((res: any) => res.type === "user");
            if (!userResource) {
                logger.error("Step 3: No 'user' type resource found in resources.");
                throw new Error("User resource not found");
            }
            logger.info("Step 3: Found user resource: " + JSON.stringify(userResource));

            const userCreateResourceIds = userResource?.createResourceId ? [userResource.createResourceId] : [];
            if(userCreateResourceIds.length === 0) {
                logger.error("Step 4: No createResourceId found in user resource.");
                throw new Error("Create resource ID missing in user resource");
            }
            logger.info("Step 4: Found user create resource IDs: " + JSON.stringify(userCreateResourceIds));

            const currentResourceSearchResponse = await getResourceFromResourceId(mappingObject, userCreateResourceIds, userResource);
            if (!currentResourceSearchResponse || currentResourceSearchResponse.length === 0) {
                logger.error("Step 5: Resource search response is empty.");
                throw new Error("No processed resource found");
            }
            logger.info("Step 5: Resource search successful: " + JSON.stringify(currentResourceSearchResponse));

            const userProcessedFileStoreId = currentResourceSearchResponse?.[0]?.processedFilestoreId;
            if (!userProcessedFileStoreId) {
                logger.error("Step 6: Processed file store ID not found in search response.");
            }
            logger.info("Step 6: Found processed file store ID: " + userProcessedFileStoreId);

            const userCredentialFileMap = { [userProcessedFileStoreId]: "userCredentials.xlsx" };
            logger.info("Step 7: Created userCredentialFileMap: " + JSON.stringify(userCredentialFileMap));
            sendNotificationEmail(userCredentialFileMap, mappingObject);
    } catch (error) {
        logger.error("Error in campaign mapping: " + error);
        await enrichAndPersistCampaignWithError(mappingObject, error);
    }
}

export async function handleMappingTaskForCampaign(messageObject: any) {
    try {
        const { CampaignDetails, task, useruuid } = messageObject;
        const processName = task?.processName
        logger.info(`Mapping for campaign ${CampaignDetails?.id} : ${processName} started..`);
        if(processName == allProcesses.resourceMapping) {
            await startResourceMapping(CampaignDetails, useruuid);
        }
        else if(processName == allProcesses.facilityMapping) {
            await startFacilityMappingAndDemapping(CampaignDetails, useruuid);
        }
        else if (processName == allProcesses.userMapping) {
            await startUserMappingAndDemapping(CampaignDetails, useruuid);
        }
        task.status = processStatuses.completed;
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC);
    } catch (error) {
        let task = messageObject?.task;
        task.status = processStatuses.failed;
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC);
        logger.error(`Error in campaign mapping: ${error}`);
        await enrichAndPersistCampaignWithErrorProcessingTask(messageObject?.CampaignDetails, messageObject?.parentCampaign, messageObject?.useruuid, error);
    }
}


export {
    processCampaignMapping,
    validateMappingId
}
