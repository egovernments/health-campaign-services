import createAndSearch from "../config/createAndSearch";
import config from "../config";
import { getDataFromSheet, throwError } from "./genericUtils";
import { getFormattedStringForDebug, logger } from "./logger";
import { defaultheader, httpRequest } from "./request";
import { produceModifiedMessages } from "../kafka/Listener";
import { getLocalizedName } from "./campaignUtils";
import { campaignStatuses, resourceDataStatuses } from "../config/constants";
import { createCampaignService } from "../service/campaignManageService";


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
    const deliveryRules = messageObject?.CampaignDetails?.deliveryRules;
    const uniquePvarIds = new Set(); // Create a Set to store unique pvar IDs
    if (deliveryRules) {
        for (const deliveryRule of deliveryRules) {
            const products = deliveryRule?.products;
            if (products) {
                for (const product of products) {
                    uniquePvarIds.add(product?.value); // Add pvar ID to the Set
                }
            }
        }
    }
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
    const boundaryCodesArray = boundaryCode.split(',').map((bc: string) => bc.trim());
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
                    if (boundaryCode && active == "Active") {
                        mapBoundaryCodes(resource, code, boundaryCode, boundaryCodes, allBoundaries);
                    }
                }
                else {
                    logger.info(`Code ${code} is somehow null or empty for resource ${resource?.type} for uniqueCodeColumn ${uniqueCodeColumn}`)
                }
            }
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
        Campaign: Campaign
    }
}

async function fetchAndMap(resources: any[], messageObject: any) {

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
    const projectMappingBody = await getProjectMappingBody(messageObject, boundaryWithProject, boundaryCodes);
    logger.info("projectMappingBody : " + JSON.stringify(projectMappingBody));
    logger.info("projectMapping started ");
    const projectMappingResponse: any = await createCampaignService(projectMappingBody);
    logger.info("Project Mapping Response received");
    if (projectMappingResponse) {
        logger.info("Campaign Mapping done")
        messageObject.CampaignDetails.status = campaignStatuses.inprogress
        produceModifiedMessages(messageObject, config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC)
    }
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
        var completedResources: any = []
        var resources = [];
        for (const resourceDetailId of resourceDetailsIds) {
            var retry = 75;
            while (retry--) {
                const response = await searchResourceDetailsById(resourceDetailId, messageObject);
                logger.info(`response for resourceDetailId: ${resourceDetailId}`);
                logger.debug(` response : ${getFormattedStringForDebug(response)}`)
                if (response?.status == "invalid") {
                    logger.error(`resource with id ${resourceDetailId} is invalid`);
                    throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "resource with id " + resourceDetailId + " is invalid");
                    break;
                }
                else if (response?.status == resourceDataStatuses.failed) {
                    logger.error(`resource with id ${resourceDetailId} is ${resourceDataStatuses.failed}`);
                    throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", `resource with id ${resourceDetailId} is ${resourceDataStatuses.failed} : with errorlog ${response?.additionalDetails?.error}`);
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
            throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "resource with id " + JSON.stringify(uncompletedResourceIds) + " is not validated after long wait. Check file");
        }
        await fetchAndMap(resources, messageObject);
    }
}


export {
    processCampaignMapping,
    validateMappingId
}
