import createAndSearch from "../config/createAndSearch";
import config from "../config";
import { logger } from "../utils/logger";
import { httpRequest } from "../utils/request";
import { getHeadersOfBoundarySheet, getHierarchy, handleResouceDetailsError } from "../api/campaignApis";
import { campaignDetailsSchema } from "../config/models/campaignDetails";
import Ajv from "ajv";
import { calculateKeyIndex, getLocalizedHeaders, getLocalizedMessagesHandler, modifyTargetData, replicateRequest, throwError } from "../utils/genericUtils";
import { createBoundaryMap, generateProcessedFileAndPersist, getLocalizedName } from "../utils/campaignUtils";
import { validateBodyViaSchema, validateCampaignBodyViaSchema, validateHierarchyType } from "./genericValidator";
import { searchCriteriaSchema } from "../config/models/SearchCriteria";
import { searchCampaignDetailsSchema } from "../config/models/searchCampaignDetails";
import { campaignDetailsDraftSchema } from "../config/models/campaignDetailsDraftSchema";
import { downloadRequestSchema } from "../config/models/downloadRequestSchema";
import { createRequestSchema } from "../config/models/createRequestSchema"
import { getSheetData, getTargetWorkbook } from "../api/genericApis";
const _ = require('lodash');
import * as XLSX from 'xlsx';
import { searchDataService } from "../service/dataManageService";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { campaignStatuses, resourceDataStatuses } from "../config/constants";
import { getBoundaryColumnName, getBoundaryTabName } from "../utils/boundaryUtils";





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
    var response = await httpRequest(config.host.boundaryHost + config.paths.boundaryRelationship, request.body, boundaryEntitySearchParams);
    const TenantBoundary = response.TenantBoundary;
    TenantBoundary.forEach((tenantBoundary: any) => {
        const { boundary } = tenantBoundary;
        processBoundary(responseBoundaries, request, boundary);
    });
    return responseBoundaries;
}

function processBoundaryfromCampaignDetails(responseBoundaries: any[], request: any, boundaryItems: any[]) {
    boundaryItems.forEach((boundaryItem: any) => {
        const { code, boundaryType, children } = boundaryItem;
        responseBoundaries.push({ code, boundaryType });
        if (children.length > 0) {
            processBoundaryfromCampaignDetails(responseBoundaries, request, children);
        }
    });
}

async function fetchBoundariesFromCampaignDetails(request: any) {
    const { tenantId, hierarchyType } = request.body.CampaignDetails;
    const boundaryEntitySearchParams: any = {
        tenantId, hierarchyType, includeChildren: true
    };
    const responseBoundaries: any[] = [];
    var response = await httpRequest(config.host.boundaryHost + config.paths.boundaryRelationship, request.body, boundaryEntitySearchParams);
    const TenantBoundary = response.TenantBoundary;
    TenantBoundary.forEach((tenantBoundary: any) => {
        const { boundary } = tenantBoundary;
        processBoundaryfromCampaignDetails(responseBoundaries, request, boundary);
    });
    return responseBoundaries;
}

// Compares unique boundaries with response boundaries and throws error for missing codes.
function compareBoundariesWithUnique(uniqueBoundaries: any[], responseBoundaries: any[], request: any) {
    // Extracts boundary codes from response boundaries
    const responseBoundaryCodes = responseBoundaries.map(boundary => boundary.code.trim());

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

async function validateTargetBoundaryData(data: any[], request: any, boundaryColumn: any, errors: any[], localizationMap?: any) {
    const responseBoundaries = await fetchBoundariesInChunks(request);
    const responseBoundaryCodes = responseBoundaries.map(boundary => boundary.code);
    // Iterate through each array of objects
    for (const key in data) {
        const isNotBoundaryOrReadMeTab = key != getLocalizedName(getBoundaryTabName(), localizationMap) && key != getLocalizedName(config?.readMeTab, localizationMap);
        if (isNotBoundaryOrReadMeTab) {
            if (Array.isArray(data[key])) {
                const boundaryData = data[key];
                const boundarySet = new Set(); // Create a Set to store unique boundaries for given sheet 
                boundaryData.forEach((element: any, index: number) => {
                    const boundaries = element?.[boundaryColumn]; // Access "Boundary Code" property directly
                    if (!boundaries) {
                        errors.push({ status: "INVALID", rowNumber: element["!row#number!"], errorDetails: `Boundary Code is required for element at row ${element["!row#number!"] + 1} for sheet ${key}`, sheetName: key })
                    } else {
                        if (typeof boundaries !== 'string') {
                            errors.push({ status: "INVALID", rowNumber: element["!row#number!"], errorDetails: `Boundary Code is not of type string at row ${element["!row#number!"] + 1} in boundary sheet ${key}`, sheetName: key });
                        } else {
                            const boundaryList = boundaries.split(",").map((boundary: any) => boundary.trim());
                            if (boundaryList.length === 0 || boundaryList.includes('')) {
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
                        }
                    }
                });
            }
        }
    }
}



async function validateTargetsAtLowestLevelPresentOrNot(data: any[], request: any, errors: any[], localizationMap?: any) {
    const hierarchy = await getHierarchy(request, request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
    const modifiedHierarchy = hierarchy.map(ele => `${request?.body?.ResourceDetails?.hierarchyType}_${ele}`.toUpperCase())
    const localizedHierarchy = getLocalizedHeaders(modifiedHierarchy, localizationMap);
    const dataToBeValidated = modifyTargetData(data);
    let maxKeyIndex = -1;
    dataToBeValidated.forEach(obj => {
        const keyIndex = calculateKeyIndex(obj, localizedHierarchy, localizationMap);
        if (keyIndex > maxKeyIndex) {
            maxKeyIndex = keyIndex;
        }
    })
    const lowestLevelHierarchy = localizedHierarchy[maxKeyIndex];
    validateTargets(data, lowestLevelHierarchy, errors, localizationMap);
}
//
function validateTargets(data: any[], lowestLevelHierarchy: any, errors: any[], localizationMap?: any) {
    for (const key in data) {
        if (key != getLocalizedName(getBoundaryTabName(), localizationMap) && key != getLocalizedName(config?.readMeTab, localizationMap)) {
            if (Array.isArray(data[key])) {
                const boundaryData = data[key];
                boundaryData.forEach((obj: any, index: number) => {
                    if (obj.hasOwnProperty(lowestLevelHierarchy) && obj[lowestLevelHierarchy]) {
                        const localizedTargetColumnName = getLocalizedName("ADMIN_CONSOLE_TARGET", localizationMap);
                        const target = obj[localizedTargetColumnName];
                        if (target === undefined || typeof target !== 'number' || target <= 0 || target > 100000 || !Number.isInteger(target)) {
                            errors.push({ status: "INVALID", rowNumber: obj["!row#number!"], errorDetails: `Invalid target value at row ${obj['!row#number!'] + 1}. of sheet ${key}`, sheetName: key })
                        }
                    }
                });
            }
        }
    }
}

async function validateUnique(schema: any, data: any[], request: any) {
    const localizationMap = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId);
    if (schema?.unique) {
        const uniqueElements = schema.unique;
        const errors = [];

        for (const element of uniqueElements) {
            const uniqueMap = new Map();

            // Iterate over each data object and check uniqueness
            for (const item of data) {
                const uniqueIdentifierColumnName = createAndSearch?.[request?.body?.ResourceDetails?.type]?.uniqueIdentifierColumnName;
                const localizedUniqueIdentifierColumnName = await getLocalizedName(uniqueIdentifierColumnName, localizationMap);
                const value = item[element];
                const rowNum = item['!row#number!'] + 1;
                if (!localizedUniqueIdentifierColumnName || !item[localizedUniqueIdentifierColumnName]) {
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

function validatePhoneNumber(datas: any[]) {
    var digitErrorRows = [];
    var missingNumberRows = [];
    for (const data of datas) {
        if (data["Phone Number (Mandatory)"]) {
            var phoneNumber = data["Phone Number (Mandatory)"];
            phoneNumber = phoneNumber.toString().replace(/^0+/, '');
            if (phoneNumber.length != 10) {
                digitErrorRows.push(data["!row#number!"] + 1);
            }
        }
        else {
            missingNumberRows.push(data["!row#number!"] + 1);
        }
    }
    var isError = false;
    var errorMessage = "";
    if (digitErrorRows.length > 0) {
        isError = true;
        errorMessage = "PhoneNumber should be of 10 digit on rows " + digitErrorRows.join(" , ");
    }
    if (missingNumberRows.length > 0) {
        isError = true;
        if (errorMessage.length > 0) {
            errorMessage += " :: ";
        }
        errorMessage += "PhoneNumber is missing on rows " + missingNumberRows.join(" , ");
    }
    if (isError) {
        throwError("COMMON", 400, "VALIDATION_ERROR", errorMessage);
    }
}

async function validateViaSchema(data: any, schema: any, request: any, localizationMap?: any) {
    if (schema) {
        const ajv = new Ajv();
        const validate = ajv.compile(schema);
        const validationErrors: any[] = [];
        const uniqueIdentifierColumnName = getLocalizedName(createAndSearch?.[request?.body?.ResourceDetails?.type]?.uniqueIdentifierColumnName, localizationMap)
        if (request?.body?.ResourceDetails?.type == "user") {
            validatePhoneNumber(data)
        }
        if (data?.length > 0) {
            data.forEach((item: any) => {
                if (!item?.[uniqueIdentifierColumnName])
                    if (!validate(item)) {
                        validationErrors.push({ index: item?.["!row#number!"] + 1, errors: validate.errors });
                    }
            });
            await validateUnique(schema, data, request)
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
            throwError("FILE", 400, "INVALID_FILE_ERROR", "Data rows cannot be empty");
        }
    }
    else {
        logger.info("skipping schema validation")
    }
}



async function validateSheetData(data: any, request: any, schema: any, boundaryValidation: any, localizationMap?: { [key: string]: string }) {
    await validateViaSchema(data, schema, request, localizationMap);
    if (boundaryValidation) {
        const localisedBoundaryCode = getLocalizedName(boundaryValidation?.column, localizationMap)
        await validateBoundaryData(data, request, localisedBoundaryCode);
    }
}

async function validateTargetSheetData(data: any, request: any, boundaryValidation: any, localizationMap?: any) {
    try {
        const errors: any[] = [];
        if (boundaryValidation) {
            const localizedBoundaryValidationColumn = getLocalizedName(boundaryValidation?.column, localizationMap)
            await validateTargetBoundaryData(data, request, localizedBoundaryValidationColumn, errors, localizationMap);
            await validateTargetsAtLowestLevelPresentOrNot(data, request, errors, localizationMap);
        }
        request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
        if (request.body.sheetErrorDetails.length != 0) {
            request.body.ResourceDetails.status = resourceDataStatuses.invalid;
        }
        await generateProcessedFileAndPersist(request, localizationMap);
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


async function validateCreateRequest(request: any) {
    if (!request?.body?.ResourceDetails || Object.keys(request.body.ResourceDetails).length === 0) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "ResourceDetails is missing or empty or null");
    }
    else {
        // validate create request body 
        validateBodyViaSchema(createRequestSchema, request.body.ResourceDetails);
        await validateHierarchyType(request, request?.body?.ResourceDetails?.hierarchyType, request?.body?.ResourceDetails?.tenantId);
        if (request?.body?.ResourceDetails?.tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is not matching with userInfo");
        }
        const fileUrl = await validateFile(request);
        const localizationMap = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId);
        if (request.body.ResourceDetails.type == 'boundary') {
            await validateBoundarySheetData(request, fileUrl, localizationMap);
        }
        if (request?.body?.ResourceDetails?.type == 'boundaryWithTarget') {
            const targetWorkbook: any = await getTargetWorkbook(fileUrl);
            // const mainSheetName = targetWorkbook.SheetNames[1];
            // const sheetData = await getSheetData(fileUrl, mainSheetName, true, undefined, localizationMap);
            // const hierachy = await getHierarchy(request, request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
            // validateForRootElementExists(sheetData, hierachy, mainSheetName);
            validateTabsWithTargetInTargetSheet(request, targetWorkbook);
        }
    }
}

function validateTabsWithTargetInTargetSheet(request: any, targetWorkbook: any) {
    const sheet = targetWorkbook.Sheets[targetWorkbook.SheetNames[2]];
    const expectedHeaders = XLSX.utils.sheet_to_json(sheet, {
        header: 1,
    })[0];
    for (let i = 2; i < targetWorkbook.SheetNames.length; i++) {
        const sheetName = targetWorkbook?.SheetNames[i];
        const sheet = targetWorkbook?.Sheets[sheetName];
        // Convert the sheet to JSON to extract headers
        const headersToValidate = XLSX.utils.sheet_to_json(sheet, {
            header: 1,
        })[0];
        if (!_.isEqual(expectedHeaders, headersToValidate)) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `Headers not according to the template in target sheet ${sheetName}`)
        }
    }

}

async function validateBoundarySheetData(request: any, fileUrl: any, localizationMap?: any) {
    const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap);
    const headersOfBoundarySheet = await getHeadersOfBoundarySheet(fileUrl, localizedBoundaryTab, false, localizationMap);
    const hierarchy = await getHierarchy(request, request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
    const modifiedHierarchy = hierarchy.map(ele => `${request?.body?.ResourceDetails?.hierarchyType}_${ele}`.toUpperCase())
    const localizedHierarchy = getLocalizedHeaders(modifiedHierarchy, localizationMap);
    await validateHeaders(localizedHierarchy, headersOfBoundarySheet, request, localizationMap)
    const boundaryData = await getSheetData(fileUrl, localizedBoundaryTab, true, undefined, localizationMap);
    //validate for whether root boundary level column should not be empty
    validateForRootElementExists(boundaryData, localizedHierarchy, localizedBoundaryTab);
    // validate for duplicate rows(array of objects)
    validateForDupicateRows(boundaryData);
}

function validateForRootElementExists(boundaryData: any[], hierachy: any[], sheetName: string) {
    const root = hierachy[0];
    if (!(boundaryData.filter(e => e[root]).length == boundaryData.length)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Invalid Boundary Sheet. Root level Boundary not present in every row  of Sheet ${sheetName}`)
    }
}
function validateForDupicateRows(boundaryData: any[]) {
    const uniqueRows = _.uniqWith(boundaryData, (obj1: any, obj2: any) => {
        // Exclude '!row#number!' property when comparing objects
        const filteredObj1 = _.omit(obj1, ['!row#number!']);
        const filteredObj2 = _.omit(obj2, ['!row#number!']);
        return _.isEqual(filteredObj1, filteredObj2);
    });
    const duplicateBoundaryRows = boundaryData.filter(e => !uniqueRows.includes(e));
    const duplicateRowNumbers = duplicateBoundaryRows.map(obj => obj['!row#number!'] + 1);
    const rowNumbersSeparatedWithCommas = duplicateRowNumbers.join(', ');
    if (duplicateRowNumbers.length > 0) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary Sheet has duplicate rows at rowNumber ${rowNumbersSeparatedWithCommas}`);
    }
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

function throwMissingCodesError(missingCodes: any, hierarchyType: any) {
    const missingCodesMessage = missingCodes.map((code: any) =>
        `'${code.code}' for type '${code.type}'`
    ).join(', ');
    throwError(
        "COMMON",
        400,
        "VALIDATION_ERROR",
        `The following boundary codes (${missingCodesMessage}) do not exist for hierarchy type '${hierarchyType}'.`
    );
}

async function validateCampaignBoundary(boundaries: any[], hierarchyType: any, tenantId: any, request: any): Promise<void> {
    const boundaryCodesToMatch = Array.from(new Set(boundaries.map((boundary: any) => ({
        code: boundary.code.trim(),
        type: boundary.type.trim()
    }))));
    const responseBoundaries = await fetchBoundariesFromCampaignDetails(request);
    const responseBoundaryCodes = responseBoundaries.map(boundary => ({
        code: boundary.code.trim(),
        type: boundary.boundaryType.trim()
    }));
    logger.info("responseBoundaryCodes " + JSON.stringify(responseBoundaryCodes))
    logger.info("boundaryCodesToMatch " + JSON.stringify(boundaryCodesToMatch))
    function isEqual(obj1: any, obj2: any) {
        return obj1.code === obj2.code && obj1.type === obj2.type;
    }

    // Find missing codes
    const missingCodes = boundaryCodesToMatch.filter(codeToMatch =>
        !responseBoundaryCodes.some(responseCode => isEqual(codeToMatch, responseCode))
    );

    if (missingCodes.length > 0) {
        throwMissingCodesError(missingCodes, hierarchyType)
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
            }
            if (rootBoundaryCount !== 1) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "Exactly one boundary should have isRoot=true");
            }
            await validateCampaignBoundary(boundaries, hierarchyType, tenantId, request);
        }
        else {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Missing boundaries array");
        }
    }
}

async function validateBoundaryOfResouces(CampaignDetails: any, request: any, localizationMap?: any) {
    const resource = request?.body?.ResourceDetails
    if (resource?.type == "user" || resource?.type == "facility") {
        const { boundaries, tenantId } = CampaignDetails;
        const localizedTab = getLocalizedName(createAndSearch?.[resource.type]?.parseArrayConfig?.sheetName, localizationMap);
        const boundaryCodes = new Set(boundaries.map((boundary: any) => boundary.code.trim()));

        // Fetch file response
        const fileResponse = await httpRequest(config.host.filestore + config.paths.filestore + "/url", {}, { tenantId, fileStoreIds: resource.fileStoreId }, "get");
        const datas = await getSheetData(fileResponse?.fileStoreIds?.[0]?.url, localizedTab, true, undefined, localizationMap);

        const boundaryColumn = getLocalizedName(createAndSearch?.[resource.type]?.boundaryValidation?.column, localizationMap);

        // Initialize resource boundary codes as a set for uniqueness
        const resourceBoundaryCodesArray: any[] = [];
        datas.forEach((data: any) => {
            const codes = data?.[boundaryColumn]?.split(',').map((code: string) => code.trim()) || [];
            resourceBoundaryCodesArray.push({ boundaryCodes: codes, rowNumber: data?.['!row#number!'] })
        });

        // Convert sets to arrays for comparison
        const boundaryCodesArray = Array.from(boundaryCodes);
        var errors = []
        // Check for missing boundary codes
        for (const rowData of resourceBoundaryCodesArray) {
            var missingBoundaries = rowData.boundaryCodes.filter((code: any) => !boundaryCodesArray.includes(code));
            if (missingBoundaries.length > 0) {
                const errorString = `The following boundary codes are not present in selected boundaries : ${missingBoundaries.join(', ')}`
                errors.push({ status: "BOUNDARYMISSING", rowNumber: rowData.rowNumber, errorDetails: errorString })
            }
        }
        request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
    }
}


async function validateResources(resources: any, request: any) {
    for (const resource of resources) {
        if (resource?.resourceId) {
            var searchBody = {
                RequestInfo: request?.body?.RequestInfo,
                SearchCriteria: {
                    id: [resource?.resourceId],
                    tenantId: request?.body?.CampaignDetails?.tenantId
                }
            }
            const req: any = replicateRequest(request, searchBody);
            const res: any = await searchDataService(req);
            if (res?.[0]) {
                if (!(res?.[0]?.status == resourceDataStatuses.completed && res?.[0]?.action == "validate")) {
                    logger.error(`Error during validation of type ${resource.type}, validation is not successful or not completed. Resource id : ${resource?.resourceId}`);
                    throwError("COMMON", 400, "VALIDATION_ERROR", `Error during validation of type ${resource.type}, validation is not successful or not completed.`);
                }
                if (res?.[0]?.fileStoreId != resource?.filestoreId) {
                    logger.error(`fileStoreId doesn't match for resource with Id ${resource?.resourceId}. Expected fileStoreId ${resource?.filestoreId} but received ${res?.[0]?.fileStoreId}`);
                    throwError("COMMON", 400, "VALIDATION_ERROR", `Uploaded file doesn't match for resource of type ${resource.type}.`)
                }
            }
            else {
                logger.error(`No resource data found for resource with Id ${resource?.resourceId}`);
                throwError("COMMON", 400, "VALIDATION_ERROR", `No resource data found for validation of resource type ${resource.type}.`);
            }
        }
    }
}

async function validateProjectCampaignResources(resources: any, request: any) {
    const requiredTypes = ["user", "facility", "boundaryWithTarget"];
    const typeCounts: any = {
        "user": 0,
        "facility": 0,
        "boundaryWithTarget": 0
    };

    const missingTypes: string[] = [];

    if (!resources || !Array.isArray(resources) || resources.length === 0) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "resources should be a non-empty array");
    }

    for (const resource of resources) {
        const { type } = resource;
        if (!type || !requiredTypes.includes(type)) {
            throwError(
                "COMMON",
                400,
                "VALIDATION_ERROR",
                `Invalid resource type. Allowed types are: ${requiredTypes.join(', ')}`
            );
        }
        typeCounts[type]++;
    }

    for (const type of requiredTypes) {
        if (typeCounts[type] === 0) {
            missingTypes.push(type);
        }
    }

    if (missingTypes.length > 0) {
        const missingTypesMessage = `Missing resources of types: ${missingTypes.join(', ')}`;
        throwError("COMMON", 400, "VALIDATION_ERROR", missingTypesMessage);
    }

    if (request?.body?.CampaignDetails?.action === "create" && request?.body?.CampaignDetails?.resources) {
        await validateResources(request.body.CampaignDetails.resources, request);
    }
}




function validateProjectCampaignMissingFields(CampaignDetails: any) {
    validateCampaignBodyViaSchema(campaignDetailsSchema, CampaignDetails)
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
    validateCampaignBodyViaSchema(campaignDetailsDraftSchema, CampaignDetails)
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
    if (campaignName.length >= 2) {
        const searchBody = {
            RequestInfo: request.body.RequestInfo,
            CampaignDetails: {
                tenantId: tenantId,
                campaignName: campaignName
            }
        }
        const req: any = replicateRequest(request, searchBody)
        const searchResponse: any = await searchProjectTypeCampaignService(req)
        if (Array.isArray(searchResponse?.CampaignDetails)) {
            if (searchResponse?.CampaignDetails?.length > 0) {
                const allCampaigns = searchResponse?.CampaignDetails;
                logger.info(`campaignName to match : ${"'"}${campaignName}${"'"}`)
                const campaignWithMatchingName: any = allCampaigns.find((campaign: any) => "'" + campaign?.campaignName + "'" == "'" + campaignName + "'") || null;
                if (campaignWithMatchingName && actionInUrl == "create") {
                    throwError("CAMPAIGN", 400, "CAMPAIGN_NAME_ERROR");
                }
                else if (campaignWithMatchingName && actionInUrl == "update" && campaignWithMatchingName?.id != CampaignDetails?.id) {
                    throwError("CAMPAIGN", 400, "CAMPAIGN_NAME_ERROR");
                }
            }
        }
        else {
            throwError("CAMPAIGN", 500, "CAMPAIGN_SEARCH_ERROR");
        }
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
    const req: any = replicateRequest(request, searchBody)
    const searchResponse: any = await searchProjectTypeCampaignService(req)
    if (Array.isArray(searchResponse?.CampaignDetails)) {
        if (searchResponse?.CampaignDetails?.length > 0) {
            logger.info("CampaignDetails : " + JSON.stringify(searchResponse?.CampaignDetails));
            request.body.ExistingCampaignDetails = searchResponse?.CampaignDetails[0];
            if (request.body.ExistingCampaignDetails?.campaignName != request?.body?.CampaignDetails?.campaignName && request.body.ExistingCampaignDetails?.status != campaignStatuses?.drafted) {
                throwError("CAMPAIGN", 400, "CAMPAIGNNAME_MISMATCH", `CampaignName can only be updated in ${campaignStatuses?.drafted} state. CampaignName mismatch, Provided CampaignName = ${request?.body?.CampaignDetails?.campaignName} but Existing CampaignName = ${request.body.ExistingCampaignDetails?.campaignName}`);
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

async function validateProjectType(request: any, projectType: any, tenantId: any) {
    if (!projectType) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "projectType is required");
    }
    else {
        const searchBody = {
            RequestInfo: request.body.RequestInfo,
            "MdmsCriteria": {
                "tenantId": tenantId,
                "moduleDetails": [
                    {
                        "moduleName": "HCM-PROJECT-TYPES",
                        "masterDetails": [
                            {
                                "name": "projectTypes",
                                "filter": "*.code"
                            }
                        ]
                    }
                ]
            }
        }
        const params = { tenantId: tenantId }
        const searchResponse: any = await httpRequest(config.host.mdms + "egov-mdms-service/v1/_search", searchBody, params);
        if (searchResponse?.MdmsRes?.["HCM-PROJECT-TYPES"]?.projectTypes && Array.isArray(searchResponse?.MdmsRes?.["HCM-PROJECT-TYPES"]?.projectTypes)) {
            const projectTypes = searchResponse?.MdmsRes?.["HCM-PROJECT-TYPES"]?.projectTypes;
            if (!projectTypes.includes(projectType)) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "projectType is not valid");
            }
        }
        else {
            throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", "Error occured during projectType search");
        }
    }
}




async function validateProjectCampaignRequest(request: any, actionInUrl: any) {
    const CampaignDetails = request.body.CampaignDetails;
    const { id, hierarchyType, action, tenantId, boundaries, resources, projectType } = CampaignDetails;
    if (actionInUrl == "update") {
        if (!id) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "id is required for update");
        }
    }
    if (!CampaignDetails) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "CampaignDetails is required");
    }
    if (!action) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "CampaignDetails.action is required and must be either 'create' or 'draft'")
    }
    if (!(action == "create" || action == "draft")) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "action can only be create or draft");
    }
    if (action == "create") {
        validateProjectCampaignMissingFields(CampaignDetails);
        await validateCampaignName(request, actionInUrl);
        if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is not matching with userInfo");
        }
        await validateHierarchyType(request, hierarchyType, tenantId);
        await validateProjectType(request, projectType, tenantId);
        await validateProjectCampaignBoundaries(boundaries, hierarchyType, tenantId, request);
        await validateProjectCampaignResources(resources, request);
    }
    else {
        validateDraftProjectCampaignMissingFields(CampaignDetails);
        await validateCampaignName(request, actionInUrl);
        await validateHierarchyType(request, hierarchyType, tenantId);
        await validateProjectType(request, projectType, tenantId);
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
        throwError("COMMON", 400, "VALIDATION_ERROR", `Invalid Filter Criteria: Exactly one root boundary can be there, but found "${rootBoundaries.length}`);
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




async function validateHeaders(hierarchy: any[], headersOfBoundarySheet: any, request: any, localizationMap?: any) {
    validateBoundarySheetHeaders(headersOfBoundarySheet, hierarchy, request, localizationMap);
}
function validateBoundarySheetHeaders(headersOfBoundarySheet: any[], hierarchy: any[], request: any, localizationMap?: any) {
    const localizedBoundaryCode = getLocalizedName(getBoundaryColumnName(), localizationMap)
    const boundaryCodeIndex = headersOfBoundarySheet.indexOf(localizedBoundaryCode);
    const keysBeforeBoundaryCode = boundaryCodeIndex === -1 ? headersOfBoundarySheet : headersOfBoundarySheet.slice(0, boundaryCodeIndex);
    if (keysBeforeBoundaryCode.some((key: any, index: any) => (key === undefined || key === null) || key !== hierarchy[index]) || keysBeforeBoundaryCode.length !== hierarchy.length) {
        const errorMessage = `"Boundary Sheet Headers are not the same as the hierarchy present for the given tenant and hierarchy type: ${request?.body?.ResourceDetails?.hierarchyType}"`;
        throwError("BOUNDARY", 400, "BOUNDARY_SHEET_HEADER_ERROR", errorMessage);
    }
}


async function validateDownloadRequest(request: any) {
    const { tenantId, hierarchyType } = request.query;
    validateBodyViaSchema(downloadRequestSchema, request.query);
    if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId in userInfo and query should be the same");
    }
    await validateHierarchyType(request, hierarchyType, tenantId);
}

function immediateValidationForTargetSheet(dataFromSheet: any, localizationMap: any) {
    for (const key in dataFromSheet) {
        if (Object.prototype.hasOwnProperty.call(dataFromSheet, key)) {
            const dataArray = (dataFromSheet as { [key: string]: any[] })[key];
            if (dataArray.length === 0) {
                throwError("COMMON", 400, "VALIDATION_ERROR", `The Target Sheet ${key} you have uploaded is empty`)
            }
            if (key != getLocalizedName(getBoundaryTabName(), localizationMap) && key != getLocalizedName(config?.readMeTab, localizationMap)) {
                const root = getLocalizedName(config.generateDifferentTabsOnBasisOf, localizationMap);
                for (const boundaryRow of dataArray) {
                    for (const columns in boundaryRow) {
                        if (columns.startsWith('__EMPTY')) {
                            throwError("COMMON", 400, "VALIDATION_ERROR", `Invalid column has some random data in Target Sheet ${key} at row number ${boundaryRow['!row#number!'] + 1}`);
                        }
                    }
                    if (!boundaryRow[root]) {
                        throwError("COMMON", 400, "VALIDATION_ERROR", ` ${root} column is empty in Target Sheet ${key} at row number ${boundaryRow['!row#number!'] + 1}`);
                    }
                }
            }
        }
    }
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
    validateTargetSheetData,
    immediateValidationForTargetSheet,
    validateBoundaryOfResouces
}
