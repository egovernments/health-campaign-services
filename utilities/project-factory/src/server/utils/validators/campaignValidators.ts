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
import { validateHierarchyType } from "./genericValidator";



function processBoundary(responseBoundaries: any[], request: any, boundaryItems: any[], parentId?: string) {
    const { tenantId, hierarchyType } = request.body.ResourceDetails;
    boundaryItems.forEach((boundaryItem: any) => {
        const { id, code, boundaryType, children } = boundaryItem;
        responseBoundaries.push({ tenantId, hierarchyType, parentId, id, code, boundaryType });
        if (children.length > 0) {
            processBoundary(responseBoundaries, request, children, id);
        }
    });
}
async function fetchBoundariesInChunks(request: any) {
    const { tenantId, hierarchyType } = request.body.ResourceDetails;
    const boundaryEnitiySearchParams: any = {
        tenantId, hierarchyType, includeChildren: true
    };
    const responseBoundaries: any[] = [];
    logger.info("Boundary search url : " + config.host.boundaryHost + config.paths.boundaryRelationship);
    logger.info("Boundary search params : " + JSON.stringify(boundaryEnitiySearchParams));
    var response = await httpRequest(config.host.boundaryHost + config.paths.boundaryRelationship, request.body, boundaryEnitiySearchParams);
    const TenantBoundary = response.TenantBoundary;
    TenantBoundary.forEach((tenantBoundary: any) => {
        const { boundary } = tenantBoundary;
        processBoundary(responseBoundaries, request, boundary);
    });
    return responseBoundaries;
}



// Compares unique boundaries with response boundaries and throws error for missing codes.
function compareBoundariesWithUnique(uniqueBoundaries: any[], responseBoundaries: any[], request: any) {
    // Extracts boundary codes from response boundaries
    const responseBoundaryCodes = responseBoundaries.map(boundary => boundary.code);

    // Finds missing codes from unique boundaries
    const missingCodes = uniqueBoundaries.filter(code => !responseBoundaryCodes.includes(code));

    // Throws error if missing codes exist
    if (missingCodes.length > 0) {
        throwError(
            "COMMON",
            400,
            "VALIDATION_ERROR",
            `Boundary codes ${missingCodes.join(', ')} do not exist in hierarchyType ${request?.body?.ResourceDetails?.hierarchyType}`
        );
    }
}

// Validates unique boundaries against the response boundaries.
async function validateUniqueBoundaries(uniqueBoundaries: any[], request: any) {
    // Fetches response boundaries in chunks
    const responseBoundaries = await fetchBoundariesInChunks(request);

    // Compares unique boundaries with response boundaries
    compareBoundariesWithUnique(uniqueBoundaries, responseBoundaries, request);
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

async function validateUnique(schema: any, data: any[], request: any) {
    if (schema?.unique) {
        const uniqueElements = schema.unique;
        const errors = [];

        for (const element of uniqueElements) {
            const uniqueMap = new Map();

            // Iterate over each data object and check uniqueness
            for (const item of data) {
                const uniqueIdentifierColumnName = createAndSearch?.[request?.body?.ResourceDetails?.type]?.uniqueIdentifierColumnName;
                const value = item[element];
                const rowNum = item['!row#number!'] + 1;

                if (!uniqueIdentifierColumnName || !item[uniqueIdentifierColumnName]) {
                    // Check if the value is already in the map
                    if (uniqueMap.has(value)) {
                        errors.push(`Duplicate value '${value}' found for '${element}' at row number ${rowNum}.`);
                    }
                    // Add the value to the map
                    uniqueMap.set(value, rowNum);
                }
            }
        }

        if (errors.length > 0) {
            // Throw an error or return the errors based on your requirement
            throwError("FILE", 400, "INVALID_FILE_ERROR", errors.join(" ; "));
        }
    }
}




async function validateViaSchema(data: any, schema: any, request: any) {
    if (schema) {
        const ajv = new Ajv();
        const validate = ajv.compile(schema);
        const validationErrors: any[] = [];
        data.forEach((item: any) => {
            if (!item?.[createAndSearch?.[request?.body?.ResourceDetails?.type]?.uniqueIdentifierColumnName])
                if (!validate(item)) {
                    validationErrors.push({ index: item?.["!row#number!"] + 1, errors: validate.errors });
                }
        });
        await validateUnique(schema, data, request)

        // Throw errors if any
        if (validationErrors.length > 0) {
            const errorMessage = validationErrors.map(({ index, errors }) => {
                const formattedErrors = errors.map((error: any) => {
                    let formattedError = `${error.dataPath}: ${error.message}`;
                    if (error.keyword === 'enum' && error.params && error.params.allowedValues) {
                        formattedError += `. Allowed values are: ${error.params.allowedValues.join(', ')}`;
                    }
                    return formattedError;
                }).join(', ');
                return `Data at row ${index}: ${formattedErrors}`;
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
        await validateHierarchyType(request, request?.body?.ResourceDetails?.hierarchyType, request?.body?.ResourceDetails?.tenantId);

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

async function validateResources(resources: any, request: any) {
    for (const resource of resources) {
        const resourceDetails = {
            type: resource.type,
            fileStoreId: resource.filestoreId,
            tenantId: request?.body?.CampaignDetails?.tenantId,
            action: "validate",
            hierarchyType: request?.body?.CampaignDetails?.hierarchyType,
            additionalDetails: {}
        };
        try {
            await axios.post(`${config.host.projectFactoryBff}project-factory/v1/data/_create`, {
                RequestInfo: request.body.RequestInfo,
                ResourceDetails: resourceDetails
            });
        } catch (error: any) {
            logger.error(`Error during resource validation of ${resourceDetails.fileStoreId} :` + error?.response?.data?.Errors?.[0]?.description || error?.response?.data?.Errors?.[0]?.message);
            throwError("COMMON", error?.response?.status, error?.response?.data?.Errors?.[0]?.code, `Error during resource validation of ${resourceDetails.fileStoreId} :` + error?.response?.data?.Errors?.[0]?.description || error?.response?.data?.Errors?.[0]?.message);
        }
    }
}

async function validateProjectCampaignResources(resources: any, request: any) {
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
        if (request?.body?.CampaignDetails?.action == "create" && request?.body?.CampaignDetails?.resources) {
            await validateResources(request.body.CampaignDetails.resources, request);
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
        await validateProjectCampaignResources(resources, request);
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
    // map boundary code and type 
    createBoundaryMap(boundaryData, boundaryMap);
    const hierarchy = await getHierarchy(request, request?.query?.tenantId, request?.query?.hierarchyType);
    // validation of filters object
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


async function validateDownloadRequest(request: any) {
    const { tenantId, type, hierarchyType } = request.query;
    if (!tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required");
    }
    if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId in userInfo and query should be the same");
    }
    if (!type) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "type is required");
    }
    if (!["facility", "user", "boundary", "facilityWithBoundary"].includes(String(type))) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Type should be facility, user, boundary, or facilityWithBoundary");
    }
    if (!hierarchyType) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "hierarchyType is required");
    }
    await validateHierarchyType(request, hierarchyType, tenantId);
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
    validateBoundarySheetData,
    validateDownloadRequest
}
