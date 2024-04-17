import createAndSearch from "../../config/createAndSearch";
import config from "../../config";
import { logger } from "../logger";
import { httpRequest } from "../request";
import { getHierarchy } from "../../api/campaignApis";
import { campaignDetailsSchema } from "../../config/campaignDetails";
import Ajv from "ajv";
import axios from "axios";
import { createBoundaryMap } from "../campaignUtils";
import { throwError } from "../genericUtils";


async function fetchBoundariesInChunks(request: any) {
    const { tenantId, hierarchyType } = request.body.ResourceDetails;
    const boundaryEnitiySearchParams: any = {
        tenantId, hierarchyType, includeChildren: true
    };
    const responseBoundaries: any[] = [];
    logger.info("Boundary search url : " + config.host.boundaryHost + config.paths.boundaryRelationship);
    logger.info("Boundary search params : " + JSON.stringify(boundaryEnitiySearchParams));
    var response = await httpRequest(config.host.boundaryHost + config.paths.boundaryRelationship, request.body, boundaryEnitiySearchParams);
    const processBoundary = (boundaryItems: any[], parentId?: string) => {
        boundaryItems.forEach((boundaryItem: any) => {
            const { id, code, boundaryType, children } = boundaryItem;
            responseBoundaries.push({ tenantId, hierarchyType, parentId, id, code, boundaryType });
            if (children.length > 0) {
                processBoundary(children, id);
            }
        });
    };
    const TenantBoundary = response.TenantBoundary;
    TenantBoundary.forEach((tenantBoundary: any) => {
        const { boundary } = tenantBoundary;
        processBoundary(boundary);
    });
    return responseBoundaries;
}


function compareBoundariesWithUnique(uniqueBoundaries: any[], responseBoundaries: any[]) {
    if (responseBoundaries.length >= uniqueBoundaries.length) {
        logger.info("Boundary codes exist");
    } else {
        const responseCodes = responseBoundaries.map(boundary => boundary.code);
        const missingCodes = uniqueBoundaries.filter(code => !responseCodes.includes(code));
        if (missingCodes.length > 0) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary codes ${missingCodes.join(', ')} do not exist`);
        } else {
            throwError("BOUNDARY", 500, "BOUNDARY_SEARCH_ERROR");
        }
    }
}

async function validateUniqueBoundaries(uniqueBoundaries: any[], request: any) {
    const responseBoundaries = await fetchBoundariesInChunks(request);
    compareBoundariesWithUnique(uniqueBoundaries, responseBoundaries);
}



async function validateBoundaryData(data: any[], request: any, boundaryColumn: any) {
    const boundarySet = new Set(); // Create a Set to store unique boundaries

    data.forEach((element, index) => {
        const boundaries = element[boundaryColumn];
        if (!boundaries) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary Code is required for element at index ${index}`);
        }

        const boundaryList = boundaries.split(",").map((boundary: any) => boundary.trim());
        if (boundaryList.length === 0) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `At least 1 boundary is required for element at index ${index}`);
        }

        for (const boundary of boundaryList) {
            if (!boundary) {
                throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary format is invalid at ${index}. Put it with one comma between boundary codes`);
            }
            boundarySet.add(boundary); // Add boundary to the set
        }
    });
    const uniqueBoundaries = Array.from(boundarySet);
    await validateUniqueBoundaries(uniqueBoundaries, request);
}


async function validateViaSchema(data: any, schema: any, request: any) {

    if (schema) {
        const ajv = new Ajv();
        const validate = ajv.compile(schema);
        const validationErrors: any[] = [];
        data.forEach((item: any, index: any) => {
            if (!item?.[createAndSearch?.[request?.body?.ResourceDetails?.type]?.uniqueIdentifierColumnName])
                if (!validate(item)) {
                    validationErrors.push({ index, errors: validate.errors });
                }
        });

        // Throw errors if any
        if (validationErrors.length > 0) {
            const errorMessage = validationErrors.map(({ index, errors }) => {
                const formattedErrors = errors.map((error: any) => `${error.dataPath}: ${error.message}`).join(', ');
                return `Data at index ${index}: ${formattedErrors}`;
            }).join(' , ');
            throwError("COMMON", 400, "VALIDATION_ERROR", errorMessage);
        } else {
            logger.info("All Data rows are valid.");
        }
    }

    else {
        logger.info("skipping schema validation")
    }
}



async function validateSheetData(data: any, request: any, schema: any, boundaryValidation: any) {
    await validateViaSchema(data, schema, request);
    if (boundaryValidation) {
        await validateBoundaryData(data, request, boundaryValidation?.column);
    }
}
function validateBooleanField(obj: any, fieldName: any, index: any) {
    if (!obj.hasOwnProperty(fieldName)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Object at index ${index} is missing field "${fieldName}".`);
    }

    if (typeof obj[fieldName] !== 'boolean') {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Object at index ${index} has invalid type for field "${fieldName}". It should be a boolean.`);
    }
}

function validateStringField(obj: any, fieldName: any, index: any) {
    if (!obj.hasOwnProperty(fieldName)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Object at index ${index} is missing field "${fieldName}".`);
    }
    if (typeof obj[fieldName] !== 'string') {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Object at index ${index} has invalid type for field "${fieldName}". It should be a string.`);
    }
    if (obj[fieldName].length < 1) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Object at index ${index} has empty value for field "${fieldName}".`);
    }
    if (obj[fieldName].length > 128) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Object at index ${index} has value for field "${fieldName}" that exceeds the maximum length of 128 characters.`);
    }
}

function validateStorageCapacity(obj: any, index: any) {
    if (!obj.hasOwnProperty('storageCapacity')) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Object at index ${index} is missing field "storageCapacity".`);
    }
    if (typeof obj.storageCapacity !== 'number') {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Object at index ${index} has invalid type for field "storageCapacity". It should be a number.`);
    }
}

function validateAction(action: string) {
    if (!(action == "create" || action == "validate")) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid action");
    }
}

function validateResourceType(type: string) {
    if (!createAndSearch[type]) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid resource type");
    }
}

async function validateCreateRequest(request: any) {
    if (!request?.body?.ResourceDetails) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "ResourceDetails is missing");
    } else {
        if (!request?.body?.ResourceDetails?.fileStoreId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "fileStoreId is missing");
        }
        if (!request?.body?.ResourceDetails?.type) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "type is missing");
        }
        if (!request?.body?.ResourceDetails?.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is missing");
        }
        if (!request?.body?.ResourceDetails?.action) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "action is missing");
        }
        if (!request?.body?.ResourceDetails?.hierarchyType) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "hierarchyType is missing");
        }
        await validateHierarchyType(request);

        if (request?.body?.ResourceDetails?.tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is not matching with userInfo");
        }
        validateAction(request?.body?.ResourceDetails?.action);
        validateResourceType(request?.body?.ResourceDetails?.type);
    }
}

function validateFacilityCreateData(data: any) {
    data.forEach((obj: any) => {
        const originalIndex = obj.originalIndex;

        // Validate string fields
        const stringFields = ['tenantId', 'name', 'usage'];
        stringFields.forEach(field => {
            validateStringField(obj, field, originalIndex);
        });

        // Validate storageCapacity
        validateStorageCapacity(obj, originalIndex);

        // Validate isPermanent
        validateBooleanField(obj, 'isPermanent', originalIndex);
    });

}

async function validateCampaignBoundary(boundary: any, hierarchyType: any, tenantId: any, request: any): Promise<void> {
    const params = {
        tenantId: tenantId,
        codes: boundary.code,
        boundaryType: boundary.type,
        hierarchyType: hierarchyType,
        includeParents: true
    };
    const boundaryResponse = await httpRequest(config.host.boundaryHost + config.paths.boundaryRelationship, { RequestInfo: request.body.RequestInfo }, params);
    if (!boundaryResponse?.TenantBoundary || !Array.isArray(boundaryResponse.TenantBoundary) || boundaryResponse.TenantBoundary.length === 0) {
        throwError("BOUNDARY", 400, "BOUNDARY_NOT_FOUND", `Boundary with code ${boundary.code} not found for boundary type ${boundary.type} and hierarchy type ${hierarchyType}`);
    }

    const boundaryData = boundaryResponse.TenantBoundary[0]?.boundary;

    if (!boundaryData || !Array.isArray(boundaryData) || boundaryData.length === 0) {
        throwError("BOUNDARY", 400, "BOUNDARY_NOT_FOUND", `Boundary with code ${boundary.code} not found for boundary type ${boundary.type} and hierarchy type ${hierarchyType}`);
    }

    if (boundary.isRoot && boundaryData[0]?.code !== boundary.code) {
        throwError("BOUNDARY", 400, "BOUNDARY_NOT_FOUND", `Boundary with code ${boundary.code} not found for boundary type ${boundary.type} and hierarchy type ${hierarchyType}`);
    }
}

async function validateProjectCampaignBoundaries(boundaries: any[], hierarchyType: any, tenantId: any, request: any): Promise<void> {
    if (!request?.body?.CampaignDetails?.projectId) {
        if (boundaries) {
            if (!Array.isArray(boundaries)) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "boundaries should be an array");
            }
            let rootBoundaryCount = 0;
            for (const boundary of boundaries) {
                if (!boundary.code) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", "Boundary code is required");
                }
                if (!boundary.type) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", "Boundary type is required");
                }

                if (boundary.isRoot) {
                    rootBoundaryCount++;
                }
                await validateCampaignBoundary(boundary, hierarchyType, tenantId, request);
            }
            if (rootBoundaryCount !== 1) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "Exactly one boundary should have isRoot=true");
            }
        }
        else {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Missing boundaries array");
        }
    }
}

async function validateProjectCampaignResources(resources: any[]) {
    if (resources) {
        if (!Array.isArray(resources)) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "resources should be an array");
        }
        for (const resource of resources) {
            const { type } = resource;
            if (!createAndSearch[type]) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid resource type");
            }
        }
    }
}


function validateProjectCampaignMissingFields(CampaignDetails: any) {
    const ajv = new Ajv();
    const validate = ajv.compile(campaignDetailsSchema);
    const valid = validate(CampaignDetails);
    if (!valid) {
        throwError("COMMON", 400, "VALIDATION_ERROR", 'Invalid data: ' + ajv.errorsText(validate.errors));
    }
    const { startDate, endDate } = CampaignDetails;
    if (startDate && endDate && (new Date(endDate).getTime() - new Date(startDate).getTime()) < (24 * 60 * 60 * 1000)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "endDate must be at least one day after startDate");
    }
    const today: any = Date.now();
    if (startDate <= today) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "startDate cannot be today or past date");
    }
}

async function validateCampaignName(request: any) {
    const CampaignDetails = request.body.CampaignDetails;
    const { campaignName, tenantId } = CampaignDetails;
    if (!campaignName) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "campaignName is required");
    }
    if (!tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required");
    }
    const searchBody = {
        RequestInfo: request.body.RequestInfo,
        CampaignDetails: {
            tenantId: tenantId,
            campaignName: campaignName
        }
    }
    logger.info("searchBody : " + JSON.stringify(searchBody));
    logger.info("Url : " + config.host.projectFactoryBff + "project-factory/v1/project-type/search");
    const searchResponse: any = await axios.post(config.host.projectFactoryBff + "project-factory/v1/project-type/search", searchBody);
    if (Array.isArray(searchResponse?.data?.CampaignDetails)) {
        if (searchResponse?.data?.CampaignDetails?.length > 0) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Campaign name already exists");
        }
    }
    else {
        throwError("CAMPAIGN", 500, "CAMPAIGN_SEARCH_ERROR");
    }
}

async function validateById(request: any) {
    const { id, tenantId } = request?.body?.CampaignDetails
    if (!id) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "id is required");
    }
    const searchBody = {
        RequestInfo: request.body.RequestInfo,
        CampaignDetails: {
            tenantId: tenantId,
            ids: [id]
        }
    }
    logger.info("searchBody : " + JSON.stringify(searchBody));
    logger.info("Url : " + config.host.projectFactoryBff + "project-factory/v1/project-type/search");
    const searchResponse: any = await axios.post(config.host.projectFactoryBff + "project-factory/v1/project-type/search", searchBody);
    if (Array.isArray(searchResponse?.data?.CampaignDetails)) {
        if (searchResponse?.data?.CampaignDetails?.length > 0) {
            logger.info("CampaignDetails : " + JSON.stringify(searchResponse?.data?.CampaignDetails));
            request.body.ExistingCampaignDetails = searchResponse?.data?.CampaignDetails[0];
            if (request.body.ExistingCampaignDetails?.campaignName != request?.body?.CampaignDetails?.campaignName) {
                throwError("CAMPAIGN", 400, "CAMPAIGNNAME_MISMATCH", `CampaignName mismatch, Provided CampaignName = ${request?.body?.CampaignDetails?.campaignName} but Existing CampaignName = ${request.body.ExistingCampaignDetails?.campaignName}`);
            }
        }
        else {
            throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND");
        }
    }
    else {
        throwError("CAMPAIGN", 500, "CAMPAIGN_SEARCH_ERROR");
    }
}

async function validateProjectCampaignRequest(request: any, actionInUrl: any) {
    const CampaignDetails = request.body.CampaignDetails;
    const { hierarchyType, action, tenantId, boundaries, resources } = CampaignDetails;
    if (!CampaignDetails) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "CampaignDetails is required");
    }
    if (!(action == "create" || action == "draft")) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "action can only be create or draft");
    }
    if (actionInUrl == "create") {
        await validateCampaignName(request);
    }
    if (action == "create") {
        validateProjectCampaignMissingFields(CampaignDetails);

        if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is not matching with userInfo");
        }

        await validateProjectCampaignBoundaries(boundaries, hierarchyType, tenantId, request);
        await validateProjectCampaignResources(resources);
    }
    if (actionInUrl == "update") {
        await validateById(request);
    }
}

async function validateSearchProjectCampaignRequest(request: any) {
    const CampaignDetails = request.body.CampaignDetails;
    if (!CampaignDetails) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "CampaignDetails is required");
    }
    if (!CampaignDetails.tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required");
    }
    if (CampaignDetails.ids) {
        if (!Array.isArray(CampaignDetails.ids)) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "ids should be an array");
        }
    }
    const { pagination } = CampaignDetails;
    if (pagination?.limit || pagination?.limit === 0) {
        if (typeof pagination.limit === 'number') {
            if (pagination.limit > 100 || pagination.limit < 1) {
                throwError("COMMON", 400, "INVALID_PAGINATION", "Pagination Limit should be from 1 to 100");
            }
        } else {
            throwError("COMMON", 400, "INVALID_PAGINATION", "Pagination Limit should be a number");
        }
    }
}

async function validateSearchRequest(request: any) {
    const { SearchCriteria } = request.body;
    if (!SearchCriteria) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "SearchCriteria is required");
    }
    const { tenantId } = SearchCriteria;
    if (!tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required");
    }
}


async function validateFilters(request: any, boundaryData: any[]) {
    const boundaries = request?.body?.Filters?.boundaries;
    if (!Array.isArray(boundaries)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid Filter Criteria: 'boundaries' should be an array.");
    }

    const boundaryMap = new Map<string, string>();
    createBoundaryMap(boundaryData, boundaryMap);
    const hierarchy = await getHierarchy(request, request?.query?.tenantId, request?.query?.hierarchyType);
    validateBoundariesOfFilters(boundaries, boundaryMap, hierarchy);

    const rootBoundaries = boundaries.filter((boundary: any) => boundary.isRoot);

    if (rootBoundaries.length !== 1) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Invalid Filter Criteria: Exactly one root boundary is required, but found "${rootBoundaries.length}`);
    }

    const boundaryTypeOfRoot = rootBoundaries[0]?.boundaryType;

    const boundariesOfTypeOfSameAsRoot = boundaries.filter((boundary: any) => boundary.boundaryType === boundaryTypeOfRoot);

    if (boundariesOfTypeOfSameAsRoot.length > 1) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `"Invalid Filter Criteria: Multiple boundaries of the same type as the root found. Only one is allowed.`);
    }
}

function validateBoundariesOfFilters(boundaries: any[], boundaryMap: Map<string, string>, hierarchy: any) {
    for (const boundary of boundaries) {
        if (!boundary.code) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Boundary Code is null or empty or undefined in Filters of Request Body");
        }
        if (!boundary.boundaryType) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Boundary Type is null or empty or undefined in Filters of Request Body");
        }
        if (typeof boundary.isRoot !== 'boolean') {
            throwError("COMMON", 400, "VALIDATION_ERROR", `isRoot can only be true or false. It is invalid for '${boundary.code}'`);
        }
        if (typeof boundary.includeAllChildren !== 'boolean') {
            throwError("COMMON", 400, "VALIDATION_ERROR", `includeAllChildren can only be true or false. It is invalid for '${boundary.code}'`);
        }
        if (!boundaryMap.has(boundary?.code)) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary data with code '${boundary.code}' specified in 'Filters' of the request body was not found for the given hierarchy.`);
        }
        if (!hierarchy.includes(boundary?.boundaryType)) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `${boundary.boundaryType} boundary Type not found for given hierachy`);
        }
        if (boundaryMap.get(boundary.code) !== boundary.boundaryType) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary type mismatch for code '${boundary.code}' specified in 'Filters' of the request body. Expected type: ${boundaryMap.get(boundary.code)}, but found a different type.`);
        }
    }
}


async function validateHierarchyType(request: any) {
    const requestBody = {
        "RequestInfo": { ...request?.body?.RequestInfo },
        "BoundaryTypeHierarchySearchCriteria": {
            "tenantId": request?.body?.ResourceDetails?.tenantId,
            "hierarchyType": request?.body?.ResourceDetails?.hierarchyType
        }
    }
    const url = config?.host?.boundaryHost + config?.paths?.boundaryHierarchy;
    const response = await httpRequest(url, requestBody, undefined, "post", undefined, undefined);
    if (!response?.BoundaryHierarchy) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Boundary Hierarchy not present for given tenantId");
    }
}

async function validateBoundarySheetData(headersOfBoundarySheet: any, request: any) {
    const hierarchy = await getHierarchy(request, request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
    validateBoundarySheetHeaders(headersOfBoundarySheet, hierarchy, request);
}
function validateBoundarySheetHeaders(headersOfBoundarySheet: any, hierarchy: any, request: any) {
    const boundaryCodeIndex = headersOfBoundarySheet.indexOf('Boundary Code');
    const keysBeforeBoundaryCode = boundaryCodeIndex === -1 ? headersOfBoundarySheet : headersOfBoundarySheet.slice(0, boundaryCodeIndex);
    if (keysBeforeBoundaryCode.some((key: string, index: number) => key !== hierarchy[index])) {
        const errorMessage = `"Boundary Sheet Headers are not the same as the hierarchy present for the given tenant and hierarchy type: ${request?.body?.ResourceDetails?.hierarchyType}"`;
        throwError("BOUNDARY", 500, "BOUNDARY_SHEET_HEADER_ERROR", errorMessage);
    }
}







export {
    validateSheetData,
    validateCreateRequest,
    validateFacilityCreateData,
    validateProjectCampaignRequest,
    validateSearchProjectCampaignRequest,
    validateSearchRequest,
    validateFilters,
    validateHierarchyType,
    validateBoundarySheetData
}
