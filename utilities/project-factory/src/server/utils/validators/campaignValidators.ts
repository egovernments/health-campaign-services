import createAndSearch from "../../config/createAndSearch";
import config from "../../config";
import { logger } from "../logger";
import { httpRequest } from "../request";
import { getHeadersOfBoundarySheet, getHierarchy, handleResouceDetailsError } from "../../api/campaignApis";
import { campaignDetailsSchema } from "../../config/models/campaignDetails";
import Ajv from "ajv";
import axios from "axios";
import { createBoundaryMap, generateProcessedFileAndPersist } from "../campaignUtils";
import { calculateKeyIndex, modifyTargetData, throwError } from "../genericUtils";
import { validateBodyViaSchema, validateHierarchyType } from "./genericValidator";
import { searchCriteriaSchema } from "../../config/models/SearchCriteria";
import { searchCampaignDetailsSchema } from "../../config/models/searchCampaignDetails";
import { campaignDetailsDraftSchema } from "../../config/models/campaignDetailsDraftSchema";



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
    const boundaryEntitySearchParams: any = {
        tenantId, hierarchyType, includeChildren: true
    };
    const responseBoundaries: any[] = [];
    logger.info("Boundary search url : " + config.host.boundaryHost + config.paths.boundaryRelationship);
    logger.info("Boundary search params : " + JSON.stringify(boundaryEntitySearchParams));
    var response = await httpRequest(config.host.boundaryHost + config.paths.boundaryRelationship, request.body, boundaryEntitySearchParams);
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
            throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary Code is required for element in rowNumber ${element['!row#number!'] + 1}`);
        }

        const boundaryList = boundaries.split(",").map((boundary: any) => boundary.trim());
        if (boundaryList.length === 0) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `At least 1 boundary is required for element in rowNumber ${element['!row#number!'] + 1}`);
        }

        for (const boundary of boundaryList) {
            if (!boundary) {
                throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary format is invalid in rowNumber ${element['!row#number!'] + 1}. Put it with one comma between boundary codes`);
            }
            boundarySet.add(boundary); // Add boundary to the set
        }
    });
    const uniqueBoundaries = Array.from(boundarySet);
    await validateUniqueBoundaries(uniqueBoundaries, request);
}

async function validateTargetBoundaryData(data: any[], request: any, boundaryColumn: any, errors: any[]) {
    const responseBoundaries = await fetchBoundariesInChunks(request);
    const responseBoundaryCodes = responseBoundaries.map(boundary => boundary.code);

    // Iterate through each array of objects
    for (const key in data) {
        if (Array.isArray(data[key])) {
            const boundaryData = data[key];
            const boundarySet = new Set(); // Create a Set to store unique boundaries for given sheet 
            boundaryData.forEach((element: any, index: number) => {
                const boundaries = element[boundaryColumn]; // Access "Boundary Code" property directly
                if (!boundaries) {
                    errors.push({ status: "INVALID", rowNumber: element["!row#number!"], errorDetails: `Boundary Code is required for element at row ${element["!row#number!"] + 1} for sheet ${key}`, sheetName: key })
                }
                const boundaryList = boundaries.split(",").map((boundary: any) => boundary.trim());
                if (boundaryList.length === 0) {
                    errors.push({ status: "INVALID", rowNumber: element["!row#number!"], errorDetails: `No boundary code found for row ${element["!row#number!"] + 1} in boundary sheet ${key}`, sheetName: key })
                }
                if (boundaryList.length > 1) {
                    errors.push({ status: "INVALID", rowNumber: element["!row#number!"], errorDetails: `More than one Boundary Code found at row ${element["!row#number!"] + 1} of sheet ${key}`, sheetName: key })
                }
                if (boundaryList.length === 1) {
                    const boundaryCode = boundaryList[0];
                    if (boundarySet.has(boundaryCode)) {
                        errors.push({ status: "INVALID", rowNumber: element["!row#number!"], errorDetails: `Duplicacy of boundary Code at row ${element["!row#number!"] + 1} of sheet ${key}`, sheetName: key })
                    }
                    if (!responseBoundaryCodes.includes(boundaryCode)) {
                        errors.push({ status: "INVALID", rowNumber: element["!row#number!"], errorDetails: `Boundary Code at row ${element["!row#number!"] + 1}  of sheet ${key}not found in the system`, sheetName: key })
                    }
                    boundarySet.add(boundaryCode);
                }
            });
        }
    }
}



async function validateTargetsAtLowestLevelPresentOrNot(data: any[], request: any, errors: any[]) {
    const hierachy = await getHierarchy(request, request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
    const dataToBeValidated = modifyTargetData(data);
    let maxKeyIndex = -1;
    dataToBeValidated.forEach(obj => {
        const keyIndex = calculateKeyIndex(obj, hierachy);
        if (keyIndex > maxKeyIndex) {
            maxKeyIndex = keyIndex;
        }
    })
    const lowestLevelHierarchy = hierachy[maxKeyIndex];
    validateTargets(data, lowestLevelHierarchy, errors);
}
//
function validateTargets(data: any[], lowestLevelHierarchy: any, errors: any[]) {
    for (const key in data) {
        if (Array.isArray(data[key])) {
            const boundaryData = data[key];
            boundaryData.forEach((obj: any, index: number) => {
                if (obj.hasOwnProperty(lowestLevelHierarchy)) {
                    const target = obj['Target at the Selected Boundary level'];
                    if (target === undefined || typeof target !== 'number' || target < 0 || !Number.isInteger(target)) {
                        errors.push({ status: "INVALID", rowNumber: obj["!row#number!"], errorDetails: `Invalid target value at row ${obj['!row#number!'] + 1}. of sheet ${key}`, sheetName: key })
                    }
                }
            });
        }
    }
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

async function validateTargetSheetData(data: any, request: any, boundaryValidation: any) {
    try {
        const errors: any[] = [];
        if (boundaryValidation) {
            await validateTargetBoundaryData(data, request, boundaryValidation?.column, errors);
            await validateTargetsAtLowestLevelPresentOrNot(data, request, errors);
        }
        request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
        await generateProcessedFileAndPersist(request);
    }
    catch (error) {
        await handleResouceDetailsError(request, error);
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
        const fileUrl = await validateFile(request);
        if (request.body.ResourceDetails.type == 'boundary') {
            await validateBoundarySheetData(request, fileUrl);
        }
    }
}

async function validateBoundarySheetData(request: any, fileUrl: any) {
    const headersOfBoundarySheet = await getHeadersOfBoundarySheet(fileUrl, config.sheetName, false);
    await validateHeaders(headersOfBoundarySheet, request)
}

async function validateFile(request: any) {
    const fileResponse = await httpRequest(config.host.filestore + config.paths.filestore + "/url", {}, { tenantId: request?.body?.ResourceDetails?.tenantId, fileStoreIds: request?.body?.ResourceDetails?.fileStoreId }, "get");
    if (!fileResponse || !fileResponse.fileStoreIds || !fileResponse.fileStoreIds[0] || !fileResponse.fileStoreIds[0].url) {
        throwError("FILE", 400, "INVALID_FILE");
    }
    else {
        return (fileResponse?.fileStoreIds?.[0]?.url);
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

function validateDraftProjectCampaignMissingFields(CampaignDetails: any) {
    const ajv = new Ajv();
    const validate = ajv.compile(campaignDetailsDraftSchema);
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

async function validateCampaignName(request: any, actionInUrl: any) {
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
    const searchResponse: any = await httpRequest(config.host.projectFactoryBff + "project-factory/v1/project-type/search", searchBody);
    if (Array.isArray(searchResponse?.CampaignDetails)) {
        if (searchResponse?.CampaignDetails?.length > 0 && actionInUrl == "create") {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Campaign name already exists");
        }
        else if (searchResponse?.CampaignDetails?.length > 0 && actionInUrl == "update" && searchResponse?.CampaignDetails?.[0]?.id != CampaignDetails?.id) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Campaign name already exists for another campaign");
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
            if (request.body.ExistingCampaignDetails?.campaignName != request?.body?.CampaignDetails?.campaignName && request.body.ExistingCampaignDetails?.status != "drafted") {
                throwError("CAMPAIGN", 400, "CAMPAIGNNAME_MISMATCH", `CampaignName cannot be updated in drafted state. CampaignName mismatch, Provided CampaignName = ${request?.body?.CampaignDetails?.campaignName} but Existing CampaignName = ${request.body.ExistingCampaignDetails?.campaignName}`);
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

async function validateDeliveryRules(request: any) {
    const { deliveryRules } = request?.body?.CampaignDetails;
    if (deliveryRules) {
        for (let i = 0; i < deliveryRules.length; i++) {
            const rule = deliveryRules[i];
            // Convert timestamps to dates
            var startDate = new Date(rule.startDate);
            startDate.setHours(0, 0, 0, 0);
            var endDate = new Date(rule.endDate);
            endDate.setHours(0, 0, 0, 0);

            // Validation 1: startDate should be less than endDate
            if (startDate >= endDate) {
                throwError("COMMON", 400, "VALIDATION_ERROR", `DeliveryRule ${i + 1}: Start date should be before end date.`);
            }

            // Validation 3: Check for overlapping dates
            for (let j = i + 1; j < deliveryRules.length; j++) {
                const otherRule = deliveryRules[j];
                var otherStartDate = new Date(otherRule.startDate);
                otherStartDate.setHours(0, 0, 0, 0);
                var otherEndDate = new Date(otherRule.endDate);
                endDate.setHours(0, 0, 0, 0);
                if ((startDate >= otherStartDate && startDate <= otherEndDate) ||
                    (endDate >= otherStartDate && endDate <= otherEndDate) ||
                    (startDate <= otherStartDate && endDate >= otherEndDate)) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", `DeliveryRule ${i + 1} and Rule ${j + 1}: Overlapping dates are not allowed.`);
                }
            }
        }
    }
}




async function validateProjectCampaignRequest(request: any, actionInUrl: any) {
    const CampaignDetails = request.body.CampaignDetails;
    const { hierarchyType, action, tenantId, boundaries, resources } = CampaignDetails;
    if (!CampaignDetails) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "CampaignDetails is required");
    }
    if (!action) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "CampaignDetails.action is required and must be either 'create' or 'draft'")
    }
    if (!(action == "create" || action == "draft")) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "action can only be create or draft");
    }
    await validateCampaignName(request, actionInUrl);
    if (action == "create") {
        validateProjectCampaignMissingFields(CampaignDetails);
        if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is not matching with userInfo");
        }
        await validateProjectCampaignBoundaries(boundaries, hierarchyType, tenantId, request);
        await validateProjectCampaignResources(resources, request);
        await validateDeliveryRules(request);
    }
    else {
        validateDraftProjectCampaignMissingFields(CampaignDetails);
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
    validateBodyViaSchema(searchCampaignDetailsSchema, CampaignDetails);
    let count = 0;
    let validFields = ["ids", "startDate", "endDate", "projectType", "campaignName", "status", "createdBy", "campaignNumber"];
    for (const key in CampaignDetails) {
        if (key !== 'tenantId') {
            if (validFields.includes(key)) {
                count++;
            }
        }
    }
    if (count === 0) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "At least one more field other than tenantID is required");
    }
}

async function validateSearchRequest(request: any) {
    const { SearchCriteria } = request.body;
    if (!SearchCriteria) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "SearchCriteria is required");
    }
    validateBodyViaSchema(searchCriteriaSchema, SearchCriteria);
}


async function validateFilters(request: any, boundaryData: any[]) {
    // boundaries should be present under filters object 
    if (!request?.body?.Filters?.boundaries) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid Filter Criteria: 'boundaries' should be present under filters ");
    }
    const boundaries = request?.body?.Filters?.boundaries;
    // boundaries should be an array and not empty
    if (!Array.isArray(boundaries) || boundaries?.length == 0) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid Filter Criteria: 'boundaries' should be an array and should not be empty.");
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




async function validateHeaders(headersOfBoundarySheet: any, request: any) {
    const hierarchy = await getHierarchy(request, request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
    validateBoundarySheetHeaders(headersOfBoundarySheet, hierarchy, request);
}
function validateBoundarySheetHeaders(headersOfBoundarySheet: any[], hierarchy: any[], request: any) {
    const boundaryCodeIndex = headersOfBoundarySheet.indexOf('Boundary Code');
    const keysBeforeBoundaryCode = boundaryCodeIndex === -1 ? headersOfBoundarySheet : headersOfBoundarySheet.slice(0, boundaryCodeIndex);
    if (keysBeforeBoundaryCode.some((key: any, index: any) => (key === undefined || key === null) || key !== hierarchy[index]) || keysBeforeBoundaryCode.length !== hierarchy.length) {
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
    fetchBoundariesInChunks,
    validateSheetData,
    validateCreateRequest,
    validateFacilityCreateData,
    validateProjectCampaignRequest,
    validateSearchProjectCampaignRequest,
    validateSearchRequest,
    validateFilters,
    validateHierarchyType,
    validateBoundarySheetData,
    validateDownloadRequest,
    validateTargetSheetData
}
