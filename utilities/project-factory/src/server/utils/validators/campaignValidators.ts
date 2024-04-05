import createAndSearch from "../../config/createAndSearch";
import config from "../../config";
import { logger } from "../logger";
import { httpRequest } from "../request";
import { getFacilityIds, matchFacilityData } from "../genericUtils";
import { getFacilitiesViaIds } from "../../api/campaignApis";
import { campaignDetailsSchema } from "../../config/campaignDetails";
import Ajv from "ajv";
import axios from "axios";
import { createBoundaryMap } from "../campaignUtils";


async function fetchBoundariesInChunks(uniqueBoundaries: any[], request: any) {
    const tenantId = request.body.ResourceDetails.tenantId;
    const boundaryEnitiySearchParams: any = {
        tenantId
    };
    const responseBoundaries: any[] = [];

    for (let i = 0; i < uniqueBoundaries.length; i += 10) {
        const chunk = uniqueBoundaries.slice(i, i + 10);
        const concatenatedString = chunk.join(',');
        boundaryEnitiySearchParams.codes = concatenatedString;

        const response = await httpRequest(config.host.boundaryHost + config.paths.boundaryEntity, request.body, boundaryEnitiySearchParams);

        if (!Array.isArray(response?.Boundary)) {
            throw new Error("Error in Boundary Search. Check Boundary codes");
        }

        responseBoundaries.push(...response.Boundary);
    }

    return responseBoundaries;
}

function compareBoundariesWithUnique(uniqueBoundaries: any[], responseBoundaries: any[]) {
    if (responseBoundaries.length >= uniqueBoundaries.length) {
        logger.info("Boundary codes exist");
    } else {
        const responseCodes = responseBoundaries.map(boundary => boundary.code);
        const missingCodes = uniqueBoundaries.filter(code => !responseCodes.includes(code));
        if (missingCodes.length > 0) {
            throw new Error(`Boundary codes ${missingCodes.join(', ')} do not exist`);
        } else {
            throw new Error("Error in Boundary Search. Check Boundary codes");
        }
    }
}

async function validateUniqueBoundaries(uniqueBoundaries: any[], request: any) {
    const responseBoundaries = await fetchBoundariesInChunks(uniqueBoundaries, request);
    compareBoundariesWithUnique(uniqueBoundaries, responseBoundaries);
}



async function validateBoundaryData(data: any[], request: any, boundaryColumn: any) {
    const boundarySet = new Set(); // Create a Set to store unique boundaries

    data.forEach((element, index) => {
        const boundaries = element[boundaryColumn];
        if (!boundaries) {
            throw new Error(`Boundary Code is required for element at index ${index}`);
        }

        const boundaryList = boundaries.split(",").map((boundary: any) => boundary.trim());
        if (boundaryList.length === 0) {
            throw new Error(`At least 1 boundary is required for element at index ${index}`);
        }

        for (const boundary of boundaryList) {
            if (!boundary) {
                throw new Error(`Boundary format is invalid at ${index}. Put it with one comma between boundary codes`);
            }
            boundarySet.add(boundary); // Add boundary to the set
        }
    });
    const uniqueBoundaries = Array.from(boundarySet);
    await validateUniqueBoundaries(uniqueBoundaries, request);
}

async function validateViaSchema(data: any, schema: any) {
    const ajv = new Ajv();
    const validate = ajv.compile(schema);
    const validationErrors: any[] = [];
    data.forEach((facility: any, index: any) => {
        if (!validate(facility)) {
            validationErrors.push({ index, errors: validate.errors });
        }
    });

    // Throw errors if any
    if (validationErrors.length > 0) {
        const errorMessage = validationErrors.map(({ index, errors }) => `Facility at index ${index}: ${JSON.stringify(errors)}`).join('\n');
        throw new Error(`Validation errors:\n${errorMessage}`);
    } else {
        logger.info("All Facilities rows are valid.");
    }
}

async function validateSheetData(data: any, request: any, schema: any, boundaryValidation: any) {
    await validateViaSchema(data, schema);
    if (boundaryValidation) {
        await validateBoundaryData(data, request, boundaryValidation?.column);
    }
}
function validateBooleanField(obj: any, fieldName: any, index: any) {
    if (!obj.hasOwnProperty(fieldName)) {
        throw new Error(`Object at index ${index} is missing field "${fieldName}".`);
    }
    if (typeof obj[fieldName] !== 'boolean') {
        throw new Error(`Object at index ${index} has invalid type for field "${fieldName}". It should be a boolean.`);
    }
}

function validateStringField(obj: any, fieldName: any, index: any) {
    if (!obj.hasOwnProperty(fieldName)) {
        throw new Error(`Object at index ${index} is missing field "${fieldName}".`);
    }
    if (typeof obj[fieldName] !== 'string') {
        throw new Error(`Object at index ${index} has invalid type for field "${fieldName}". It should be a string.`);
    }
    if (obj[fieldName].length < 1) {
        throw new Error(`Object at index ${index} has empty value for field "${fieldName}".`);
    }
    if (obj[fieldName].length > 128) {
        throw new Error(`Object at index ${index} has value for field "${fieldName}" that exceeds the maximum length of 128 characters.`);
    }
}

function validateStorageCapacity(obj: any, index: any) {
    if (!obj.hasOwnProperty('storageCapacity')) {
        throw new Error(`Object at index ${index} is missing field "storageCapacity".`);
    }
    if (typeof obj.storageCapacity !== 'number') {
        throw new Error(`Object at index ${index} has invalid type for field "storageCapacity". It should be a number.`);
    }
}

function validateAction(action: string) {
    if (!(action == "create" || action == "validate")) {
        throw new Error("Invalid action")
    }
}

function validateResourceType(type: string) {
    if (!createAndSearch[type]) {
        throw new Error("Invalid resource type")
    }
}

async function validateCreateRequest(request: any) {
    if (!request?.body?.ResourceDetails) {
        throw new Error("ResourceDetails is missing")
    }
    else {
        if (!request?.body?.ResourceDetails?.fileStoreId) {
            throw new Error("fileStoreId is missing")
        }
        if (!request?.body?.ResourceDetails?.type) {
            throw new Error("type is missing")
        }
        if (!request?.body?.ResourceDetails?.tenantId) {
            throw new Error("tenantId is missing")
        }
        if (!request?.body?.ResourceDetails?.action) {
            throw new Error("action is missing")
        }
        if (request?.body?.ResourceDetails?.tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throw new Error("tenantId is not matching with userInfo")
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

async function validateFacilityViaSearch(tenantId: string, data: any, requestBody: any) {
    const ids = getFacilityIds(data);
    const searchedFacilities = await getFacilitiesViaIds(tenantId, ids, requestBody)
    matchFacilityData(data, searchedFacilities)
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
        throw new Error(`Boundary with code ${boundary.code} not found for boundary type ${boundary.type} and hierarchy type ${hierarchyType}`);
    }

    const boundaryData = boundaryResponse.TenantBoundary[0]?.boundary;

    if (!boundaryData || !Array.isArray(boundaryData) || boundaryData.length === 0) {
        throw new Error(`Boundary with code ${boundary.code} not found for boundary type ${boundary.type} and hierarchy type ${hierarchyType}`);
    }

    if (boundary.isRoot && boundaryData[0]?.code !== boundary.code) {
        throw new Error(`Boundary with code ${boundary.code} is not root`);
    }
}

async function validateProjectCampaignBoundaries(boundaries: any[], hierarchyType: any, tenantId: any, request: any): Promise<void> {
    if (!Array.isArray(boundaries)) {
        throw new Error("Boundaries should be an array");
    }

    let rootBoundaryCount = 0;

    for (const boundary of boundaries) {
        if (!boundary.code) {
            throw new Error("Boundary code is required");
        }

        if (!boundary.type) {
            throw new Error("Boundary type is required");
        }

        if (boundary.isRoot) {
            rootBoundaryCount++;
        }

        await validateCampaignBoundary(boundary, hierarchyType, tenantId, request);
    }

    if (rootBoundaryCount !== 1) {
        throw new Error("Exactly one boundary should have isRoot=true");
    }
}

async function validateProjectCampaignResources(resources: any[]) {
    if (!Array.isArray(resources)) {
        throw new Error("resources should be an array");
    }
    for (const resource of resources) {
        const { type } = resource;
        if (!createAndSearch[type]) {
            throw new Error("Invalid resource type");
        }
    }

}

function validateProjectCampaignMissingFields(CampaignDetails: any) {
    const ajv = new Ajv();
    const validate = ajv.compile(campaignDetailsSchema);
    const valid = validate(CampaignDetails);
    if (!valid) {
        throw new Error('Invalid data: ' + ajv.errorsText(validate.errors));
    }
    const { startDate, endDate } = CampaignDetails;
    if (startDate && endDate && (new Date(endDate).getTime() - new Date(startDate).getTime()) < (24 * 60 * 60 * 1000)) {
        throw new Error("endDate must be at least one day after startDate");
    }
}
async function validateCampaignName(request: any) {
    const CampaignDetails = request.body.CampaignDetails;
    const { campaignName, tenantId } = CampaignDetails;
    if (!campaignName) {
        throw new Error("campaignName is required");
    }
    if (!tenantId) {
        throw new Error("tenantId is required");
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
                throw new Error("Campaign name already exists");
            }
        }
        else {
            throw new Error("Some error occured during campaignName search");
        }
    } catch (error: any) {
        // Handle error for individual resource creation
        logger.error(`Error searching campaign name ${error?.response?.data?.Errors?.[0]?.message ? error?.response?.data?.Errors?.[0]?.message : error}`);
        throw new Error(String(error?.response?.data?.Errors?.[0]?.message ? error?.response?.data?.Errors?.[0]?.message : error))
    }

}

async function validateProjectCampaignRequest(request: any) {
    const CampaignDetails = request.body.CampaignDetails;
    const { hierarchyType, action, tenantId, boundaries, resources } = CampaignDetails;
    if (!CampaignDetails) {
        throw new Error("CampaignDetails is required");
    }
    if (!(action == "create" || action == "draft")) {
        throw new Error("action can only be create or draft")
    }
    await validateCampaignName(request);
    if (action == "create") {
        validateProjectCampaignMissingFields(CampaignDetails)
        if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throw new Error("tenantId is not matching with userInfo")
        }
        await validateProjectCampaignBoundaries(boundaries, hierarchyType, tenantId, request);
        await validateProjectCampaignResources(resources)
    }
}

async function validateSearchProjectCampaignRequest(request: any) {
    const CampaignDetails = request.body.CampaignDetails;
    if (!CampaignDetails) {
        throw new Error("CampaignDetails is required");
    }
    if (!CampaignDetails.tenantId) {
        throw new Error("tenantId is required")
    }
    if (CampaignDetails.ids) {
        if (!Array.isArray(CampaignDetails.ids)) {
            throw new Error("ids should be an array")
        }
    }
}

async function validateSearchRequest(request: any) {
    const { SearchCriteria } = request.body;
    if (!SearchCriteria) {
        throw new Error("SearchCriteria is required");
    }
    const { tenantId } = SearchCriteria;
    if (!tenantId) {
        throw new Error("tenantId is required");
    }
}

function validateFilters(request: any, boundaryData: any[]) {
    const boundaries = request?.body?.Filters?.boundaries;
    if (!Array.isArray(boundaries)) {
        throw new Error("Invalid Filter Criteria: 'boundaries' should be an array.");
    }

    const boundaryMap = new Map<string, string>();
    createBoundaryMap(boundaryData, boundaryMap);
    validateBoundariesOfFilters(boundaries, boundaryMap);

    const rootBoundaries = boundaries.filter((boundary: any) => boundary.isRoot);

    if (rootBoundaries.length !== 1) {
        throw new Error("Invalid Filter Criteria: Exactly one root boundary is required, but found " + rootBoundaries.length);
    }

    const boundaryTypeOfRoot = rootBoundaries[0]?.boundaryType;

    const boundariesOfTypeOfSameAsRoot = boundaries.filter((boundary: any) => boundary.boundaryType === boundaryTypeOfRoot);

    if (boundariesOfTypeOfSameAsRoot.length > 1) {
        throw new Error("Invalid Filter Criteria: Multiple boundaries of the same type as the root found. Only one is allowed.");
    }
}

function validateBoundariesOfFilters(boundaries: any[], boundaryMap: Map<string, string>): void {
    for (const boundary of boundaries) {
        if (!boundaryMap.has(boundary.code)) {
            throw new Error(`Boundary data with code '${boundary.code}' specified in 'Filters' of the request body was not found for the given hierarchy.`);
        } else if (boundaryMap.get(boundary.code) !== boundary.boundaryType) {
            throw new Error(`Boundary type mismatch for code '${boundary.code}' specified in 'Filters' of the request body. Expected type: ${boundary.boundaryType}, but found a different type.`);
        }
    }
}





export {
    validateSheetData,
    validateCreateRequest,
    validateFacilityCreateData,
    validateFacilityViaSearch,
    validateProjectCampaignRequest,
    validateSearchProjectCampaignRequest,
    validateSearchRequest,
    validateFilters
}
