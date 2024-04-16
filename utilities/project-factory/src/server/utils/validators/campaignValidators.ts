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
            throwError(`Boundary codes ${missingCodes.join(', ')} do not exist`, 500, "VALIDATION_ERROR");
        } else {
            throwError("Error in Boundary Search. Check Boundary codes", 500, "BOUNDARY_SEARCH_ERROR");
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
            throwError(`Boundary Code is required for element at index ${index}`, 400, "VALIDATION_ERROR");
        }

        const boundaryList = boundaries.split(",").map((boundary: any) => boundary.trim());
        if (boundaryList.length === 0) {
            throwError(`At least 1 boundary is required for element at index ${index}`, 400, "VALIDATION_ERROR");
        }

        for (const boundary of boundaryList) {
            if (!boundary) {
                throwError(`Boundary format is invalid at ${index}. Put it with one comma between boundary codes`, 400, "VALIDATION_ERROR");
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
            throwError(errorMessage, 400, "VALIDATION_ERROR");
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
        throwError(`Object at index ${index} is missing field "${fieldName}".`, 400, "VALIDATION_ERROR");
    }

    if (typeof obj[fieldName] !== 'boolean') {
        throwError(`Object at index ${index} has invalid type for field "${fieldName}". It should be a boolean.`, 400, "VALIDATION_ERROR");
    }
}

function validateStringField(obj: any, fieldName: any, index: any) {
    if (!obj.hasOwnProperty(fieldName)) {
        throwError(`Object at index ${index} is missing field "${fieldName}".`, 400, "VALIDATION_ERROR");
    }
    if (typeof obj[fieldName] !== 'string') {
        throwError(`Object at index ${index} has invalid type for field "${fieldName}". It should be a string.`, 400, "VALIDATION_ERROR");
    }
    if (obj[fieldName].length < 1) {
        throwError(`Object at index ${index} has empty value for field "${fieldName}".`, 400, "VALIDATION_ERROR");
    }
    if (obj[fieldName].length > 128) {
        throwError(`Object at index ${index} has value for field "${fieldName}" that exceeds the maximum length of 128 characters.`, 400, "VALIDATION_ERROR");
    }
}

function validateStorageCapacity(obj: any, index: any) {
    if (!obj.hasOwnProperty('storageCapacity')) {
        throwError(`Object at index ${index} is missing field "storageCapacity".`, 400, "VALIDATION_ERROR");
    }
    if (typeof obj.storageCapacity !== 'number') {
        throwError(`Object at index ${index} has invalid type for field "storageCapacity". It should be a number.`, 400, "VALIDATION_ERROR");
    }
}

function validateAction(action: string) {
    if (!(action == "create" || action == "validate")) {
        throwError("Invalid action", 400, "VALIDATION_ERROR");
    }
}

function validateResourceType(type: string) {
    if (!createAndSearch[type]) {
        throwError("Invalid resource type", 400, "VALIDATION_ERROR");
    }
}

async function validateCreateRequest(request: any) {
    if (!request?.body?.ResourceDetails) {
        throwError("ResourceDetails is missing", 400, "VALIDATION_ERROR");
    } else {
        if (!request?.body?.ResourceDetails?.fileStoreId) {
            throwError("fileStoreId is missing", 400, "VALIDATION_ERROR");
        }
        if (!request?.body?.ResourceDetails?.type) {
            throwError("type is missing", 400, "VALIDATION_ERROR");
        }
        if (!request?.body?.ResourceDetails?.tenantId) {
            throwError("tenantId is missing", 400, "VALIDATION_ERROR");
        }
        if (!request?.body?.ResourceDetails?.action) {
            throwError("action is missing", 400, "VALIDATION_ERROR");
        }
        if (!request?.body?.ResourceDetails?.hierarchyType) {
            throwError("hierarchyType is missing", 400, "VALIDATION_ERROR");
        }
        await validateHierarchyType(request);

        if (request?.body?.ResourceDetails?.tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throwError("tenantId is not matching with userInfo", 400, "VALIDATION_ERROR");
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
        throwError(`Boundary with code ${boundary.code} not found for boundary type ${boundary.type} and hierarchy type ${hierarchyType}`, 400, "BOUNDARY_NOT_FOUND");
    }

    const boundaryData = boundaryResponse.TenantBoundary[0]?.boundary;

    if (!boundaryData || !Array.isArray(boundaryData) || boundaryData.length === 0) {
        throwError(`Boundary with code ${boundary.code} not found for boundary type ${boundary.type} and hierarchy type ${hierarchyType}`, 400, "BOUNDARY_NOT_FOUND");
    }

    if (boundary.isRoot && boundaryData[0]?.code !== boundary.code) {
        throwError(`Boundary with code ${boundary.code} not found for boundary type ${boundary.type} and hierarchy type ${hierarchyType}`, 400, "BOUNDARY_NOT_FOUND");
    }
}

async function validateProjectCampaignBoundaries(boundaries: any[], hierarchyType: any, tenantId: any, request: any): Promise<void> {
    if (!request?.body?.CampaignDetails?.projectId) {
        if (boundaries) {
            if (!Array.isArray(boundaries)) {
                throwError("boundaries should be an array", 400, "VALIDATION_ERROR");
            }
            let rootBoundaryCount = 0;
            for (const boundary of boundaries) {
                if (!boundary.code) {
                    throwError("Boundary code is required", 400, "VALIDATION_ERROR");
                }
                if (!boundary.type) {
                    throwError("Boundary type is required", 400, "VALIDATION_ERROR");
                }

                if (boundary.isRoot) {
                    rootBoundaryCount++;
                }
                await validateCampaignBoundary(boundary, hierarchyType, tenantId, request);
            }
            if (rootBoundaryCount !== 1) {
                throwError("Exactly one boundary should have isRoot=true", 400, "VALIDATION_ERROR");
            }
        }
        else {
            throwError("Missing boundaries array", 400, "MISSING_BOUNDARY");
        }
    }
}

async function validateProjectCampaignResources(resources: any[]) {
    if (resources) {
        if (!Array.isArray(resources)) {
            throwError("resources should be an array", 400, "VALIDATION_ERROR");
        }
        for (const resource of resources) {
            const { type } = resource;
            if (!createAndSearch[type]) {
                throwError("Invalid resource type", 400, "VALIDATION_ERROR");
            }
        }
    }
}


function validateProjectCampaignMissingFields(CampaignDetails: any) {
    const ajv = new Ajv();
    const validate = ajv.compile(campaignDetailsSchema);
    const valid = validate(CampaignDetails);
    if (!valid) {
        throwError('Invalid data: ' + ajv.errorsText(validate.errors), 400, "VALIDATION_ERROR");
    }
    const { startDate, endDate } = CampaignDetails;
    if (startDate && endDate && (new Date(endDate).getTime() - new Date(startDate).getTime()) < (24 * 60 * 60 * 1000)) {
        throwError("endDate must be at least one day after startDate", 400, "VALIDATION_ERROR");
    }
}

async function validateCampaignName(request: any) {
    const CampaignDetails = request.body.CampaignDetails;
    const { campaignName, tenantId } = CampaignDetails;
    if (!campaignName) {
        throwError("campaignName is required", 400, "VALIDATION_ERROR");
    }
    if (!tenantId) {
        throwError("tenantId is required", 400, "VALIDATION_ERROR");
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
    try {
        const searchResponse: any = await axios.post(config.host.projectFactoryBff + "project-factory/v1/project-type/search", searchBody);
        if (Array.isArray(searchResponse?.data?.CampaignDetails)) {
            if (searchResponse?.data?.CampaignDetails?.length > 0) {
                throwError("Campaign name already exists", 400, "VALIDATION_ERROR");
            }
        }
        else {
            throwError("Some error occurred during campaignName search", 500, "CAMPAIGN_SEARCH_ERROR");
        }
    } catch (error: any) {
        // Handle error for individual resource creation
        logger.error(`Error searching campaign name ${error?.response?.data?.Errors?.[0]?.message ? error?.response?.data?.Errors?.[0]?.message : error}`);
        throwError(String(error?.response?.data?.Errors?.[0]?.message ? error?.response?.data?.Errors?.[0]?.message : error), 500, "CAMPAIGN_SEARCH_ERROR");
    }
}

async function validateById(request: any) {
    const { id, tenantId } = request?.body?.CampaignDetails
    if (!id) {
        throwError("id is required", 400, "VALIDATION_ERROR");
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
    try {
        const searchResponse: any = await axios.post(config.host.projectFactoryBff + "project-factory/v1/project-type/search", searchBody);
        if (Array.isArray(searchResponse?.data?.CampaignDetails)) {
            if (searchResponse?.data?.CampaignDetails?.length > 0) {
                logger.info("CampaignDetails : " + JSON.stringify(searchResponse?.data?.CampaignDetails));
                request.body.ExistingCampaignDetails = searchResponse?.data?.CampaignDetails[0];
                if (request.body.ExistingCampaignDetails?.campaignName != request?.body?.CampaignDetails?.campaignName) {
                    throwError(`CampaignName mismatch, Provided CampaignName = ${request?.body?.CampaignDetails?.campaignName} but Existing CampaignName = ${request.body.ExistingCampaignDetails?.campaignName}`, 400, "CAMPAIGNNAME_MISMATCH");
                }
            }
            else {
                throwError("Campaign not found", 400, "CAMPAIGN_NOT_FOUND");
            }
        }
        else {
            throwError("Some error occurred during campaignDetails search", 500, "CAMPAIGN_SEARCH_ERROR");
        }
    } catch (error: any) {
        // Handle error for individual resource creation
        logger.error(`Error searching campaign ${error?.response?.data?.Errors?.[0]?.message ? error?.response?.data?.Errors?.[0]?.message : error}`);
        throwError(String(error?.response?.data?.Errors?.[0]?.message ? error?.response?.data?.Errors?.[0]?.message : error), 500, "CAMPAIGN_SEARCH_ERROR");
    }
}

async function validateProjectCampaignRequest(request: any, actionInUrl: any) {
    const CampaignDetails = request.body.CampaignDetails;
    const { hierarchyType, action, tenantId, boundaries, resources } = CampaignDetails;
    if (!CampaignDetails) {
        throwError("CampaignDetails is required", 400, "VALIDATION_ERROR");
    }
    if (!(action == "create" || action == "draft")) {
        throwError("action can only be create or draft", 400, "VALIDATION_ERROR");
    }
    if (actionInUrl == "create") {
        await validateCampaignName(request);
    }
    if (action == "create") {
        validateProjectCampaignMissingFields(CampaignDetails);
        if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throwError("tenantId is not matching with userInfo", 400, "VALIDATION_ERROR");
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
        throwError("CampaignDetails is required", 400, "VALIDATION_ERROR");
    }
    if (!CampaignDetails.tenantId) {
        throwError("tenantId is required", 400, "VALIDATION_ERROR");
    }
    if (CampaignDetails.ids) {
        if (!Array.isArray(CampaignDetails.ids)) {
            throwError("ids should be an array", 400, "VALIDATION_ERROR");
        }
    }
    const { pagination } = CampaignDetails;
    if (pagination?.limit || pagination?.limit === 0) {
        if (typeof pagination.limit === 'number') {
            if (pagination.limit > 100 || pagination.limit < 1) {
                throwError("Pagination Limit should be from 1 to 100", 400, "INVALID_PAGINATION");
            }
        } else {
            throwError("Pagination Limit should be a number", 400, "INVALID_PAGINATION");
        }
    }
}

async function validateSearchRequest(request: any) {
    const { SearchCriteria } = request.body;
    if (!SearchCriteria) {
        throwError("SearchCriteria is required", 400, "VALIDATION_ERROR");
    }
    const { tenantId } = SearchCriteria;
    if (!tenantId) {
        throwError("tenantId is required", 400, "VALIDATION_ERROR");
    }
}

async function validateFilters(request: any, boundaryData: any[]) {
    const boundaries = request?.body?.Filters?.boundaries;
    if (!Array.isArray(boundaries)) {
        throwError("Invalid Filter Criteria: 'boundaries' should be an array.", 400, "VALIDATION_ERROR");
    }

    const boundaryMap = new Map<string, string>();
    createBoundaryMap(boundaryData, boundaryMap);
    const hierarchy = await getHierarchy(request, request?.query?.tenantId, request?.query?.hierarchyType);
    validateBoundariesOfFilters(boundaries, boundaryMap, hierarchy);

    const rootBoundaries = boundaries.filter((boundary: any) => boundary.isRoot);

    if (rootBoundaries.length !== 1) {
        throw Object.assign(new Error(`Invalid Filter Criteria: Exactly one root boundary is required, but found "${rootBoundaries.length}`), { code: "ROOT_BOUNDARY_ERROR" })
    }

    const boundaryTypeOfRoot = rootBoundaries[0]?.boundaryType;

    const boundariesOfTypeOfSameAsRoot = boundaries.filter((boundary: any) => boundary.boundaryType === boundaryTypeOfRoot);

    if (boundariesOfTypeOfSameAsRoot.length > 1) {
        throw Object.assign(new Error(`"Invalid Filter Criteria: Multiple boundaries of the same type as the root found. Only one is allowed.`), { code: "MORE_THAN_ONE_BOUNDARY_AT_ROOT_LEVEL_ERROR" })
    }
}

function validateBoundariesOfFilters(boundaries: any[], boundaryMap: Map<string, string>, hierarchy: any) {
    for (const boundary of boundaries) {
        if (!boundary.code) {
            throw Object.assign(new Error(`Boundary Code is null or empty or undefined in Filters of Request Body`), { code: "BOUNDRY_CODE_NULL" })
        }
        if (!boundary.boundaryType) {
            throw Object.assign(new Error(`Boundary Type is null or empty or undefined in Filters of Request Body`), { code: "BOUNDRY_TYPE_NULL" })
        }
        if (typeof boundary.isRoot !== 'boolean') {
            throw Object.assign(
                new Error(`isRoot can only be true or false. It is invalid for '${boundary.code}'`),
                { code: "IS_ROOT_INVALID" }
            );
        }
        if (typeof boundary.includeAllChildren !== 'boolean') {
            throw Object.assign(
                new Error(`includeAllChildren can only be true or false. It is invalid for '${boundary.code}'`),
                { code: "INCLUDE_ALL_CHILDREN_INVALID" }
            );
        }
        if (!boundaryMap.has(boundary?.code)) {
            throw Object.assign(new Error(`Boundary data with code '${boundary.code}' specified in 'Filters' of the request body was not found for the given hierarchy.`), { code: "BOUNDARY_CODE_IN_FILTERS_INVALID" });
        }
        if (!hierarchy.includes(boundary?.boundaryType)) {
            throw Object.assign(new Error(`${boundary.boundaryType} boundary Type not found for given hierachy`), { code: "BOUNDARY_TYPE_INVALID" });
        }
        if (boundaryMap.get(boundary.code) !== boundary.boundaryType) {
            throw Object.assign(new Error(`Boundary type mismatch for code '${boundary.code}' specified in 'Filters' of the request body. Expected type: ${boundaryMap.get(boundary.code)}, but found a different type.`), { code: "BOUNDARY_CODE_AND_TYPE_IN_FILTERS_MISMATCH" });
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
        throwError("Boundary Hierarchy not present for given tenantId", 400, "VALIDATION_ERROR")
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
        throwError(errorMessage, 500, "BOUNDARY_SHEET_HEADER_ERROR");
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
