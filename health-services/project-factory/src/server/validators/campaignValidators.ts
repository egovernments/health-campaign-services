import createAndSearch from "../config/createAndSearch";
import config from "../config";
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { defaultheader, httpRequest } from "../utils/request";
import { getCampaignSearchResponse, getHeadersOfBoundarySheet, getHierarchy, handleResouceDetailsError } from "../api/campaignApis";
import { campaignDetailsSchema } from "../config/models/campaignDetails";
import Ajv from "ajv";
import { getDifferentDistrictTabs, getLocalizedHeaders, getMdmsDataBasedOnCampaignType, throwError } from "../utils/genericUtils";
import { createBoundaryMap, enrichInnerCampaignDetails, generateProcessedFileAndPersist, getFinalValidHeadersForTargetSheetAsPerCampaignType, getLocalizedName } from "../utils/campaignUtils";
import { validateBodyViaSchema, validateCampaignBodyViaSchema, validateHierarchyType } from "./genericValidator";
import { searchCriteriaSchema } from "../config/models/SearchCriteria";
import { searchCampaignDetailsSchema } from "../config/models/searchCampaignDetails";
import { campaignDetailsDraftSchema } from "../config/models/campaignDetailsDraftSchema";
import { downloadRequestSchema } from "../config/models/downloadRequestSchema";
import { createRequestSchema } from "../config/models/createRequestSchema"
import { getSheetData, getTargetWorkbook } from "../api/genericApis";
const _ = require('lodash');
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { campaignStatuses, resourceDataStatuses, usageColumnStatus } from "../config/constants";
import { getBoundaryColumnName, getBoundaryTabName } from "../utils/boundaryUtils";
import addAjvErrors from "ajv-errors";
import { generateTargetColumnsBasedOnDeliveryConditions, isDynamicTargetTemplateForProjectType, modifyDeliveryConditions } from "../utils/targetUtils";
import { getBoundariesFromCampaignSearchResponse, validateMissingBoundaryFromParent } from "../utils/onGoingCampaignUpdateUtils";
import { validateExtraBoundariesForMicroplan, validateLatLongForMicroplanCampaigns, validatePhoneNumberSheetWise, validateRequiredTargetsForMicroplanCampaigns, validateUniqueSheetWise, validateUserForMicroplan } from "./microplanValidators";
import { produceModifiedMessages } from "../kafka/Producer";
import { isMicroplanRequest, planConfigSearch, planFacilitySearch } from "../utils/microplanUtils";
import { getPvarIds } from "../utils/campaignMappingUtils";
import { fetchProductVariants } from "../api/healthApis";
import { validateFileMetaDataViaFileUrl } from "../utils/excelUtils";
import { getLocaleFromRequest } from "../utils/localisationUtils";
import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { fetchFileFromFilestore, searchBoundaryRelationshipDefinition } from "../api/coreApis";
import { processTemplateConfigs } from "../config/processTemplateConfigs";
import { GenerateTemplateQuery } from "../models/GenerateTemplateQuery";
import { generationtTemplateConfigs } from "../config/generationtTemplateConfigs";
import addErrors from "ajv-errors";


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
    const params: any = {
        tenantId, hierarchyType, includeChildren: true
    };
    const header = {
        ...defaultheader,
        cachekey: `boundaryRelationShipSearch${params?.hierarchyType}${params?.tenantId}${params.codes || ''}${params?.includeChildren || ''}`,
    }
    const responseBoundaries: any[] = [];
    var response = await httpRequest(config.host.boundaryHost + config.paths.boundaryRelationship, request.body, params, undefined, undefined, header);
    const TenantBoundary = response.TenantBoundary;
    TenantBoundary.forEach((tenantBoundary: any) => {
        const { boundary } = tenantBoundary;
        processBoundaryfromCampaignDetails(responseBoundaries, request, boundary);
    });
    return responseBoundaries;
}

function validateTargetForNormalCampaigns(data: any, errors: any, localizedTargetColumnNames: any, isGenericType: boolean, localizationMap?: { [key: string]: string }) {
    for (const key in data) {
        if (key !== getLocalizedName(getBoundaryTabName(), localizationMap) && key !== getLocalizedName(config.values?.readMeTab, localizationMap)) {
            if (Array.isArray(data[key])) {
                const boundaryData = data[key];
                const targetColumnsToValidate = isGenericType ? localizedTargetColumnNames.filter((column: string) =>
                    boundaryData.some((row: any) => row.hasOwnProperty(column) && row[column] !== null && row[column] !== undefined)
                ) : localizedTargetColumnNames;
                boundaryData.forEach((obj: any, index: number) => {
                    for (const targetColumn of targetColumnsToValidate) {
                        const target = obj[targetColumn];
                        if (!target && !isGenericType) {
                            errors.push({
                                status: "INVALID",
                                rowNumber: obj["!row#number!"],
                                errorDetails: `Data in column '${targetColumn}' cannot be empty or zero.(It can only be positive numbers less than or equal to 1 Million)`,
                                sheetName: key
                            });
                        }
                        else if (target) {
                            if (typeof target !== 'number') {
                                errors.push({
                                    status: "INVALID",
                                    rowNumber: obj["!row#number!"],
                                    errorDetails: `Data in column '${targetColumn}' is not a number.(It can only be positive numbers less than or equal to 1 Million)`,
                                    sheetName: key
                                });
                            } else if (target <= 0 || target > 1000000) {
                                errors.push({
                                    status: "INVALID",
                                    rowNumber: obj["!row#number!"],
                                    errorDetails: `Data in column '${targetColumn}' is out of range.(It can only be positive numbers less than or equal to 1 Million)`,
                                    sheetName: key
                                });
                            } else if (!Number.isInteger(target)) {
                                errors.push({
                                    status: "INVALID",
                                    rowNumber: obj["!row#number!"],
                                    errorDetails: `Data in column '${targetColumn}' is not an integer.(It can only be positive numbers less than or equal to 1 Million)`,
                                    sheetName: key
                                });
                            }
                        }
                    }
                });
            }
        }
    }
}

export function validateTargetFromTargetConfigs(
    datas: any[],
    errors: any[],
    targetColumnsToBeValidated: string[],
    allTargetColumns: string[],
    localizationMap?: { [key: string]: string },
    minTarget: number = 1,
    maxTarget: number = 1000000
) {
    const optionalTargetColumns = allTargetColumns.filter(
        (column: string) => !targetColumnsToBeValidated.includes(column)
    );

    const errorMessage = (key: string, min: number, max: number) =>
        `Data in column '${ getLocalizedName(key, localizationMap)}' cannot be empty and should be a number between ${min} and ${max} inclusive.`;

    for (let i = 0; i < datas.length; i++) {
        const data = datas[i];

        // Required target columns
        for (const key of targetColumnsToBeValidated) {
            const value = data[key];

            if (value === undefined || value === null || value === '') {
                errors.push({ row: i + 3, message: errorMessage(key, minTarget, maxTarget) });
            } else if (typeof value !== 'number') {
                errors.push({ row: i + 3, message: errorMessage(key, minTarget, maxTarget) });
            } else if (value < minTarget || value > maxTarget) {
                errors.push({ row: i + 3, message: errorMessage(key, minTarget, maxTarget) });
            }
        }

        // Optional target columns
        for (const key of optionalTargetColumns) {
            const value = data[key];

            if (value !== undefined && value !== null && value !== '') {
                if (typeof value !== 'number') {
                    errors.push({ row: i + 3, message: errorMessage(key, minTarget, maxTarget) });
                } else if (value < minTarget || value > maxTarget) {
                    errors.push({ row: i + 3, message: errorMessage(key, minTarget, maxTarget) });
                }
            }
        }
    }
}
  


async function validateTargets(request: any, data: any[], errors: any[], localizationMap?: any) {
    let columnsToValidate: any;
    let targetColumnsWhoseValidationIsNotMandatory: any;
    const responseFromCampaignSearch = await getCampaignSearchResponse(request);
    const campaignObject = responseFromCampaignSearch?.CampaignDetails?.[0];
    if (isDynamicTargetTemplateForProjectType(campaignObject?.projectType) && campaignObject.deliveryRules && campaignObject.deliveryRules.length > 0) {
        const modifiedUniqueDeliveryConditions = modifyDeliveryConditions(campaignObject.deliveryRules);
        columnsToValidate = generateTargetColumnsBasedOnDeliveryConditions(modifiedUniqueDeliveryConditions, localizationMap);
    }
    else {
        const schema = await getMdmsDataBasedOnCampaignType(request);
        const columnsNotToBeFreezed = schema?.columnsNotToBeFreezed;
        const requiredColumns = schema?.required;
        columnsToValidate = columnsNotToBeFreezed.filter((element: any) => requiredColumns.includes(element));
        targetColumnsWhoseValidationIsNotMandatory = columnsNotToBeFreezed.filter((element: any) => !requiredColumns.includes(element));
    }
    const localizedRequiredTargetColumnNames = getLocalizedHeaders(columnsToValidate, localizationMap);
    const localizedNotRequiredTargetColumnNames = getLocalizedHeaders(targetColumnsWhoseValidationIsNotMandatory, localizationMap);
    if (request?.body?.ResourceDetails?.additionalDetails?.source === "microplan") {
        validateRequiredTargetsForMicroplanCampaigns(data, errors, localizedRequiredTargetColumnNames, localizationMap);
        validateLatLongForMicroplanCampaigns(data, errors, localizationMap);
    }
    else {
        validateTargetForNormalCampaigns(data, errors, localizedRequiredTargetColumnNames, false, localizationMap);
        // validate target columns which are not required but has data present in those columns for genric camapign types 
        validateTargetForNormalCampaigns(data, errors, localizedNotRequiredTargetColumnNames, true, localizationMap);
    }
}

function validateUnique(schema: any, data: any[], request: any, localizationMap: any) {
    if (schema?.unique) {
        const uniqueElements = schema.unique;
        const errors = [];

        for (const element of uniqueElements) {
            const uniqueMap = new Map();

            // Iterate over each data object and check uniqueness
            for (const item of data) {
                const uniqueIdentifierColumnName = createAndSearch?.[request?.body?.ResourceDetails?.type]?.uniqueIdentifierColumnName;
                const localizedUniqueIdentifierColumnName = getLocalizedName(uniqueIdentifierColumnName, localizationMap);
                const value = item[element];
                const rowNum = item['!row#number!'];
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

function validatePhoneNumber(datas: any[], localizationMap: any) {
    var digitErrorRows = [];
    var missingNumberRows = [];
    for (const data of datas) {
        const phoneColumn = getLocalizedName("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", localizationMap);
        if (data[phoneColumn]) {
            var phoneNumber = data[phoneColumn];
            phoneNumber = phoneNumber.toString().replace(/^0+/, '');
            if (phoneNumber.length != config?.user?.phoneNumberLength) {
                digitErrorRows.push(data["!row#number!"]);
            }
        }
        else {
            missingNumberRows.push(data["!row#number!"]);
        }
    }
    var isError = false;
    var errorMessage = "";
    if (digitErrorRows.length > 0) {
        isError = true;
        errorMessage = `PhoneNumber should be of ${config?.user?.phoneNumberLength} digits on rows ${digitErrorRows.join(" , ")}`;
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


async function changeSchemaErrorMessage(schema: any, localizationMap?: any) {
    if (schema?.errorMessage) {
        for (const key in schema.errorMessage) {
            const value = schema.errorMessage[key];
            delete schema.errorMessage[key];
            schema.errorMessage[getLocalizedName(key, localizationMap)] = value;
        }
    }
    return schema; // Return unmodified schema if no error message
}

function validateData(data: any[], validationErrors: any[], activeColumnName: any, uniqueIdentifierColumnName: any, validate: any) {
    data.forEach((item: any) => {
        if (activeColumnName) {
            if (!item?.[activeColumnName]) {
                validationErrors.push({ index: item?.["!row#number!"], errors: [{ instancePath: `${activeColumnName}`, message: `should not be empty` }] });
            }
            else if (item?.[activeColumnName] != usageColumnStatus.active && item?.[activeColumnName] != usageColumnStatus.inactive) {
                validationErrors.push({ index: item?.["!row#number!"], errors: [{ instancePath: `${activeColumnName}`, message: `should be equal to one of the allowed values. Allowed values are ${usageColumnStatus.active}, ${usageColumnStatus.inactive}` }] });
            }
        }
        const active = activeColumnName ? item[activeColumnName] : usageColumnStatus.active;
        if (active == usageColumnStatus.active || !item?.[uniqueIdentifierColumnName]) {
            const validationResult = validate(item);
            if (!validationResult) {
                validationErrors.push({ index: item?.["!row#number!"], errors: validate.errors });
            }
        }
    });
}

function enrichRowMappingViaValidation(validationErrors: any[], rowMapping: any, localizationMap?: any) {
    if (validationErrors.length > 0) {
        const errorMessage = validationErrors.map(({ index, message, errors }) => {
            const formattedErrors = errors ? errors.map((error: any) => {
                let instancePath = error.instancePath || ''; // Assign an empty string if dataPath is not available
                if (instancePath.startsWith('/')) {
                    instancePath = instancePath.slice(1);
                }
                if (error.keyword === 'required') {
                    const missingProperty = error.params?.missingProperty || '';
                    return `Data at row ${index} in column '${missingProperty}' should not be empty`;
                }
                let formattedError = `in column '${instancePath}' ${getLocalizedName(error.message, localizationMap)}`;
                if (error.keyword === 'enum' && error.params && error.params.allowedValues) {
                    formattedError += `. Allowed values are: ${error.params.allowedValues.join(', ')}`;
                }
                return `Data at row ${index} ${formattedError}`
            }).join(' ; ') : message;
            return formattedErrors;
        }).join(' ; ');
        throwError("COMMON", 400, "VALIDATION_ERROR", errorMessage);
    } else {
        logger.info("All Data rows are valid.");
    }
}


export async function validateViaSchema(data: any, schema: any, request: any, localizationMap?: any) {
    if (schema) {
        const newSchema: any = await changeSchemaErrorMessage(schema, localizationMap)
        const ajv = new Ajv({ allErrors: true, strict: false }); // enable allErrors to get all validation errors
        addAjvErrors(ajv);
        const validate = ajv.compile(newSchema);
        const validationErrors: any[] = [];
        const uniqueIdentifierColumnName = getLocalizedName(createAndSearch?.[request?.body?.ResourceDetails?.type]?.uniqueIdentifierColumnName, localizationMap);
        const activeColumnName = createAndSearch?.[request?.body?.ResourceDetails?.type]?.activeColumnName ? getLocalizedName(createAndSearch?.[request?.body?.ResourceDetails?.type]?.activeColumnName, localizationMap) : null;
        if (request?.body?.ResourceDetails?.type == "user") {
            validatePhoneNumber(data, localizationMap);
        }
        if (data?.length > 0 && request?.body?.ResourceDetails?.additionalDetails?.source != "microplan") {
            if (!request?.body?.parentCampaignObject && data[0]?.[getLocalizedName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE_OLD", localizationMap)]) {
                throwError("COMMON", 400, "VALIDATION_ERROR", `${request?.body?.ResourceDetails?.type} template downloaded from update campaign flow has been uploaded in create campaign flow`);
            }
            validateData(data, validationErrors, activeColumnName, uniqueIdentifierColumnName, validate);
            validateUnique(newSchema, data, request, localizationMap);
            enrichRowMappingViaValidation(validationErrors, request?.body?.rowMapping, localizationMap);
        }
        if (data?.length == 0) {
            throwError("FILE", 400, "INVALID_FILE_ERROR", "Data rows cannot be empty");
        }
    } else {
        logger.info("Skipping schema validation");
    }
}

export function validateMultiSelect(
    data: any[],
    schemaProperties: Record<string, any>,
    localizationMap: any
) {
    if (!data?.length || !schemaProperties || !Object.keys(schemaProperties).length) return [];

    const userSheet = getLocalizedName(createAndSearch?.user?.parseArrayConfig?.sheetName, localizationMap);
    const errors: any[] = [];

    data.forEach((item) => {
        Object.entries(schemaProperties).forEach(([propertyKey, property]) => {
            const minSelections = property?.multiSelectDetails?.minSelections || 0;
            const maxSelections = property?.multiSelectDetails?.maxSelections || 0;
            if (!minSelections || !maxSelections) return;

            const mainPreFix = property?.name;
            const valuesSet = new Set();
            const duplicatesSet : any = new Set();

            for (let i = 1; i <= maxSelections; i++) {
                const key = getLocalizedName(`${mainPreFix}_MULTISELECT_${i}`, localizationMap);
                const value = item[key];
                if (value) {
                    if (!valuesSet.has(value)) {
                        valuesSet.add(value);
                    } else {
                        duplicatesSet.add(value);
                    }
                }
            }

            // Collect all errors at once
            const errorDetails = [];
            if (duplicatesSet.size > 0) {
                errorDetails.push(`Duplicate roles`);   
            }
            const mainKey = getLocalizedName(property?.name, localizationMap);
            const mainKeyValueArrayLength = item?.[mainKey]?.split(",")?.length || 0;
            if (valuesSet.size < minSelections && mainKeyValueArrayLength < minSelections) {
                errorDetails.push(`At least ${minSelections} ${getLocalizedName(property?.name, localizationMap)} should be selected.`);
            }

            if (errorDetails.length > 0) {
                errors.push({
                    status: "INVALID",
                    rowNumber: item?.["!row#number!"],
                    sheetName: userSheet,
                    errorDetails: errorDetails.join(" "),
                });
            }
        });
    });

    return errors;
}

function validateDataSheetWise(data: any, validate: any, validationErrors: any[], uniqueIdentifierColumnName: any, activeColumnName: any) {
    data.forEach((item: any) => {
        const validationResult = validate(item);
        if (!validationResult) {
            validationErrors.push({ index: item?.["!row#number!"], errors: validate.errors });
        }
    });
}

function enrichRowMappingViaValidationSheetwise(rowMapping: any, validationErrors: any[], localizationMap: any) {
    if (validationErrors.length > 0) {
        validationErrors.map(({ index, message, errors }) => {
            if (errors) {
                errors.map((error: any) => {
                    let instancePath = error.instancePath || ''; // Assign an empty string if instancePath is not available
                    if (instancePath.startsWith('/')) {
                        instancePath = instancePath.slice(1);
                    }

                    // Handle 'required' keyword errors
                    if (error.keyword === 'required') {
                        const missingProperty = error.params?.missingProperty || '';
                        if (!rowMapping[index]) {
                            rowMapping[index] = [];
                        }
                        rowMapping[index].push(`Data in column '${missingProperty}' should not be empty`);
                    }
                    else {
                        // Format the general error message
                        let formattedError = `Data in column '${instancePath}' ${getLocalizedName(error.message, localizationMap)}`;

                        // Handle 'enum' keyword errors
                        if (error.keyword === 'enum' && error.params && error.params.allowedValues) {
                            formattedError += `. Allowed values are: ${error.params.allowedValues.join(', ')}`;
                        }
                        else if (error.keyword === 'pattern') {
                            formattedError = `Data in column '${instancePath}' is invalid`
                        }

                        // Ensure rowMapping[index] exists
                        if (!rowMapping[index]) {
                            rowMapping[index] = [];
                        }
                        rowMapping[index].push(`${formattedError}`);
                    }
                })
            }
        });
    }
    else {
        logger.info("All Data rows are valid.");
    }
}

export async function validateViaSchemaSheetWise(dataFromExcel: any, schema: any, request: any, localizationMap?: any) {
    const errorMap: any = {};
    for (const sheetName of Object.keys(dataFromExcel)) {
        const data = dataFromExcel[sheetName];
        const rowMapping: any = {};
        if (schema) {
            const newSchema: any = await changeSchemaErrorMessage(schema, localizationMap)
            const ajv = new Ajv({ allErrors: true, strict: false }); // enable allErrors to get all validation errors
            addAjvErrors(ajv);
            const validate = ajv.compile(newSchema);
            const validationErrors: any[] = [];
            const uniqueIdentifierColumnName = getLocalizedName(createAndSearch?.[request?.body?.ResourceDetails?.type]?.uniqueIdentifierColumnName, localizationMap);
            const activeColumnName = createAndSearch?.[request?.body?.ResourceDetails?.type]?.activeColumnName ? getLocalizedName(createAndSearch?.[request?.body?.ResourceDetails?.type]?.activeColumnName, localizationMap) : null;
            if (request?.body?.ResourceDetails?.type == "user" && request?.body?.ResourceDetails?.additionalDetails?.source == "microplan") {
                validateUserForMicroplan(data, sheetName, request, errorMap, newSchema, rowMapping, localizationMap);
            }
            else {
                if (request?.body?.ResourceDetails?.type == "user") {
                    validatePhoneNumberSheetWise(data, localizationMap, rowMapping);
                }
                if (data?.length > 0) {
                    validateDataSheetWise(data, validate, validationErrors, uniqueIdentifierColumnName, activeColumnName);
                    validateUniqueSheetWise(newSchema, data, request, rowMapping, localizationMap);
                    enrichRowMappingViaValidationSheetwise(rowMapping, validationErrors, localizationMap);
                } else {
                    errorMap[sheetName] = { 2: ["Data rows cannot be empty"] };
                }
            }
        } else {
            logger.info("Skipping schema validation");
        }
        if (Object.keys(rowMapping).length > 0) {
            errorMap[sheetName] = rowMapping;
        }
    }
    return errorMap;
}



async function validateSheetData(data: any, request: any, schema: any, boundaryValidation: any, localizationMap?: { [key: string]: string }) {
    await validateViaSchema(data, schema, request, localizationMap);
}

async function validateTargetSheetData(data: any, request: any, boundaryValidation: any, differentTabsBasedOnLevel: any, localizationMap?: any) {
    try {
        const errors: any[] = [];
        await validateHeadersOfTargetSheet(request, differentTabsBasedOnLevel, localizationMap);
        if (boundaryValidation) {
            // const localizedBoundaryValidationColumn = getLocalizedName(boundaryValidation?.column, localizationMap)
            // await validateTargetBoundaryData(data, request, localizedBoundaryValidationColumn, errors, localizationMap);
            await validateTargets(request, data, errors, localizationMap);
        }
        request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
        if (request?.body?.sheetErrorDetails && Array.isArray(request?.body?.sheetErrorDetails) && request?.body?.sheetErrorDetails?.length > 0) {
            request.body.ResourceDetails.status = resourceDataStatuses.invalid;
        }
        await generateProcessedFileAndPersist(request, localizationMap);
        logger.info("target sheet data validation completed");
    }
    catch (error) {
        console.log(error)
        await handleResouceDetailsError(request, error);
    }
}


async function validateHeadersOfTargetSheet(request: any, differentTabsBasedOnLevel: any, localizationMap?: any) {
    const fileUrl = await validateFile(request);
    const targetWorkbook: any = await getTargetWorkbook(fileUrl);
    const hierarchy = await getHierarchy(request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
    const finalValidHeadersForTargetSheetAsPerCampaignType = await getFinalValidHeadersForTargetSheetAsPerCampaignType(request, hierarchy, differentTabsBasedOnLevel, localizationMap);
    logger.info("finalValidHeadersForTargetSheetAsPerCampaignType :" + JSON.stringify(finalValidHeadersForTargetSheetAsPerCampaignType));
    logger.info("validating headers of target sheet started")
    validateHeadersOfTabsWithTargetInTargetSheet(targetWorkbook, finalValidHeadersForTargetSheetAsPerCampaignType);
    logger.info("validation of target sheet headers completed")
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


async function validateCampaignId(request: any) {
    const { campaignId, tenantId, type, additionalDetails } = request?.body?.ResourceDetails;
    if (type == "boundary") {
        return;
    }
    if (!campaignId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "CampaignId is missing");
    }
    else {
        // const searchBody = {
            const CampaignDetails= {
                ids: [campaignId],
                tenantId: tenantId
            }
        // const req: any = replicateRequest(request, searchBody);
        const response = await searchProjectTypeCampaignService(CampaignDetails);
        if (response?.CampaignDetails?.[0]) {
            const boundaries = await getBoundariesFromCampaignSearchResponse(request, response?.CampaignDetails?.[0]);
            if (!boundaries) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "Campaign with given campaignId does not have any boundaries");
            }
            if (!Array.isArray(boundaries)) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "Boundaries of campaign with given campaignId is not an array");
            }
            if (boundaries?.length === 0) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "Campaign with given campaignId does not have any boundaries");
            }
            request.body.campaignBoundaries = boundaries
        }
        else {
            if (!(additionalDetails?.source == "microplan" && type == "user")) {
                throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND", "Campaign not found while validating campaignId");
            }
        }
    }
}


async function validateCreateRequest(request: any, localizationMap?: any) {
    if (!request?.body?.ResourceDetails || Object.keys(request.body.ResourceDetails).length === 0) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "ResourceDetails is missing or empty or null");
    }
    else {
        const type = request?.body?.ResourceDetails?.type;
        // validate create request body 
        validateBodyViaSchema(createRequestSchema, request.body.ResourceDetails);
        if (type !== "boundaryManagement" && request?.body?.ResourceDetails.campaignId !== "default") {
            await validateCampaignId(request);
        }
        await validateHierarchyType(request, request?.body?.ResourceDetails?.hierarchyType, request?.body?.ResourceDetails?.tenantId);
        if (request?.body?.ResourceDetails?.tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is not matching with userInfo");
        }
        const fileUrl = await validateFile(request);
        await validateFileMetaDataViaFileUrl(fileUrl, getLocaleFromRequest(request), request?.body?.ResourceDetails?.campaignId, request?.body?.ResourceDetails?.action);
        // const localizationMap = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId);
        if (request.body.ResourceDetails.type == 'boundary' || request.body.ResourceDetails.type == 'boundaryManagement') {
            await validateBoundarySheetData(request, fileUrl, localizationMap);
        }
    }
}

function validateHeadersOfTabsWithTargetInTargetSheet(targetWorkbook: any, expectedHeadersForTargetSheet: any) {
    targetWorkbook.eachSheet((worksheet: any, sheetId: any) => {
        if (sheetId > 2) { // Starting from the second sheet
            // Convert the sheet to an array of headers
            let headersToValidate = worksheet.getRow(1).values
                .filter((header: any) => header !== undefined && header !== null && header.toString().trim() !== '')
                .map((header: any) => header.toString().trim());
            headersToValidate = headersToValidate.filter((header: string) => header !== '#status#' && header !== '#errorDetails#');
            if (!_.isEqual(expectedHeadersForTargetSheet, headersToValidate)) {
                throwError("COMMON", 400, "VALIDATION_ERROR", `Headers not according to the template in Target sheet ${worksheet.name}`);
            }
        }
    });
}

async function validateBoundarySheetData(request: any, fileUrl: any, localizationMap?: any) {
    const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap);
    const headersOfBoundarySheet = await getHeadersOfBoundarySheet(fileUrl, localizedBoundaryTab, false, localizationMap);
    const hierarchy = await getHierarchy(request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
    const modifiedHierarchy = hierarchy.map(ele => `${request?.body?.ResourceDetails?.hierarchyType}_${ele}`.toUpperCase())
    const localizedHierarchy = getLocalizedHeaders(modifiedHierarchy, localizationMap);
    await validateHeaders(localizedHierarchy, headersOfBoundarySheet, request, localizationMap)
    const boundaryData = await getSheetData(fileUrl, localizedBoundaryTab, true, undefined, localizationMap);
    //validate for whether root boundary level column should not be empty
    validateForRootElementExists(boundaryData, localizedHierarchy, localizedBoundaryTab);
    // validate for duplicate rows(array of objects)
    validateForDuplicateRows(boundaryData);
}

function validateForRootElementExists(boundaryData: any[], hierachy: any[], sheetName: string) {
    const root = hierachy[0];
    if (!(boundaryData.filter(e => e[root]).length == boundaryData.length)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Invalid Boundary Sheet. Root level Boundary not present in every row  of Sheet ${sheetName}`)
    }
}
function validateForDuplicateRows(boundaryData: any[]) {
    // Step 1: Trim strings in all rows
    boundaryData = boundaryData.map(row =>
        Object.fromEntries(
            Object.entries(row).map(([key, value]) =>
                [key, typeof value === "string" ? value.trim() : value]
            )
        )
    );

    const seen = new Set<string>();
    const duplicateRowNumbers: string[] = [];

    for (const row of boundaryData) {
        const rowNumber = row["!row#number!"];
        const rowCopy = { ...row };
        delete rowCopy["!row#number!"];

        // Serialize row as a string (key), which is much faster than deep object comparison
        const rowKey = JSON.stringify(rowCopy);

        if (seen.has(rowKey)) {
            duplicateRowNumbers.push(rowNumber);
        } else {
            seen.add(rowKey);
        }
    }

    if (duplicateRowNumbers.length > 0) {
        const rowNumbersSeparatedWithCommas = duplicateRowNumbers.join(', ');
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
    logger.info("received boundary hiearchy response, checking for valid")

    logger.debug("responseBoundaryCodes " + getFormattedStringForDebug(responseBoundaryCodes))
    logger.debug("boundaryCodesToMatch " + getFormattedStringForDebug(boundaryCodesToMatch))
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

async function validateBoundariesForTabs(CampaignDetails: any, resource: any, request: any, localizedTab: any, localizationMap?: any) {
    const { boundaries, tenantId } = CampaignDetails;
    const boundaryCodes = new Set(boundaries.map((boundary: any) => boundary.code.trim()));

    // Fetch file response
    const fileResponse = await httpRequest(config.host.filestore + config.paths.filestore + "/url", {}, { tenantId, fileStoreIds: resource.fileStoreId }, "get");
    const datas = await getSheetData(fileResponse?.fileStoreIds?.[0]?.url, localizedTab, true, undefined, localizationMap);
    var boundaryColumn: any;
    if (await isMicroplanRequest(request) && resource?.type == "facility") {
        boundaryColumn = getLocalizedName(config.boundary.boundaryCodeMandatoryForMicroplanFacility, localizationMap);
    }
    else {
        boundaryColumn = getLocalizedName(createAndSearch?.[resource.type]?.boundaryValidation?.column, localizationMap);
    }
    // Initialize resource boundary codes as a set for uniqueness
    const resourceBoundaryCodesArray: any[] = [];
    var activeColumnName: any = null;
    if (createAndSearch?.[resource.type]?.activeColumn && createAndSearch?.[resource.type]?.activeColumnName) {
        activeColumnName = getLocalizedName(createAndSearch?.[resource.type]?.activeColumnName, localizationMap);
    }
    datas.forEach((data: any) => {
        const codes = String(data?.[boundaryColumn])?.split(',').map((code: string) => code.trim()) || [];
        var active = activeColumnName ? data?.[activeColumnName] : usageColumnStatus.active;
        if (active == usageColumnStatus.active) {
            resourceBoundaryCodesArray.push({ boundaryCodes: codes, rowNumber: data?.['!row#number!'] })
        }
    });

    // Convert sets to arrays for comparison
    const boundaryCodesArray = Array.from(boundaryCodes);
    var errors = []
    // Check for missing boundary codes
    for (const rowData of resourceBoundaryCodesArray) {
        var missingBoundaries = rowData.boundaryCodes.filter((code: any) => !boundaryCodesArray.includes(code));
        if (missingBoundaries.length > 0) {
            const errorString = `The following boundary codes are not present in selected boundaries : ${missingBoundaries.join(', ')}`
            errors.push({ status: "BOUNDARYERROR", rowNumber: rowData.rowNumber, errorDetails: errorString })
        }
    }
    if (errors?.length > 0) {
        request.body.ResourceDetails.status = resourceDataStatuses.invalid
    }
    request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
}

async function validateBoundaryOfResouces(CampaignDetails: any, request: any, localizationMap?: any) {
    const resource = request?.body?.ResourceDetails
    if (resource?.type == "user" || resource?.type == "facility") {
        const localizedTab = getLocalizedName(createAndSearch?.[resource.type]?.parseArrayConfig?.sheetName, localizationMap);
        await validateBoundariesForTabs(CampaignDetails, resource, request, localizedTab, localizationMap)
    }
}


// async function validateResources(resources: any, request: any) {
//     for (const resource of resources) {
//         if (resource?.resourceId) {
//             // var searchBody = {
//             //     RequestInfo: request?.body?.RequestInfo,
//             //     SearchCriteria: {
//             //         id: [resource?.resourceId],
//             //         tenantId: request?.body?.CampaignDetails?.tenantId
//             //     }
//             // }
//             // const req: any = replicateRequest(request, searchBody);
//             // const res: any = await searchDataService(req);
//             // if (res?.[0]) {
//             //     if (!(res?.[0]?.status == resourceDataStatuses.completed && res?.[0]?.action == "validate")) {
//             //         logger.error(`Error during validation of type ${resource.type}, validation is not successful or not completed. Resource id : ${resource?.resourceId}`);
//             //         throwError("COMMON", 400, "VALIDATION_ERROR", `Error during validation of type ${resource.type}, validation is not successful or not completed.`);
//             //     }
//             //     if (res?.[0]?.fileStoreId != resource?.filestoreId) {
//             //         logger.error(`fileStoreId doesn't match for resource with Id ${resource?.resourceId}. Expected fileStoreId ${resource?.filestoreId} but received ${res?.[0]?.fileStoreId}`);
//             //         throwError("COMMON", 400, "VALIDATION_ERROR", `Uploaded file doesn't match for resource of type ${resource.type}.`)
//             //     }
//             // }
//             // else {
//             //     logger.error(`No resource data found for resource with Id ${resource?.resourceId}`);
//             //     throwError("COMMON", 400, "VALIDATION_ERROR", `No resource data found for validation of resource type ${resource.type}.`);
//             // }
//         }
//     }
// }

async function validateProjectCampaignResources(resources: any, request: any) {
    const requiredTypes = ["user", "facility", "boundary"];
    const typeCounts: any = {
        "user": 0,
        "facility": 0,
        "boundary": 0
    };

    const missingTypes: string[] = [];

    if (!resources || !Array.isArray(resources)) {
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
    if (!request?.body?.parentCampaign) {
        if (missingTypes.length > 0) {
            const missingTypesMessage = `Missing resources of types: ${missingTypes.join(', ')}`;
            throwError("COMMON", 400, "VALIDATION_ERROR", missingTypesMessage);
        }
    }
    else if(request?.body?.parentCampaign) {
        logger.info(`Missing resources of types: ${missingTypes.join(', ')}`);
        const parentResources = request.body?.parentCampaign?.resources || [];

        for (const missingType of missingTypes) {
            const fallback = parentResources.find((r: any) => r.type === missingType);
            if (fallback) {
                resources.push(fallback);
                console.log(`Added missing resource type "${missingType}" from parent campaign`);
            } else {
                throwError(
                    "COMMON",
                    400,
                    "VALIDATION_ERROR",
                    `Missing resource of type "${missingType}" and not found in parent campaign`
                );
            }
        }
    }

    // if (request?.body?.CampaignDetails?.action === "create" && request?.body?.CampaignDetails?.resources) {
    //     logger.info(`skipResourceCheckValidationBeforeCreateForLocalTesting flag is ${config.values.skipResourceCheckValidationBeforeCreateForLocalTesting }`);
    //     // !config.values.skipResourceCheckValidationBeforeCreateForLocalTesting && await validateResources(request.body.CampaignDetails.resources, request);
    // }
}




function validateProjectCampaignMissingFields(CampaignDetails: any) {
    validateCampaignBodyViaSchema(campaignDetailsSchema, CampaignDetails)
}

function validateDraftProjectCampaignMissingFields(CampaignDetails: any) {
    validateCampaignBodyViaSchema(campaignDetailsDraftSchema, CampaignDetails)
}

async function validateParent(request: any, actionInUrl: any) {
    if (request?.body?.CampaignDetails?.parentId) {
        const tenantId = request.body.CampaignDetails?.tenantId
        const CampaignDetails = {
            tenantId: tenantId,
            ids: [request.body.CampaignDetails?.parentId]
        }
        const parentSearchResponse: any = await searchProjectTypeCampaignService(CampaignDetails)
        if (Array.isArray(parentSearchResponse?.CampaignDetails)) {
            if (actionInUrl == "create") {
                if (parentSearchResponse?.CampaignDetails?.length > 0 && parentSearchResponse?.CampaignDetails?.[0]?.status == "created" &&
                    parentSearchResponse?.CampaignDetails?.[0]?.isActive) {
                    request.body.parentCampaign = parentSearchResponse?.CampaignDetails[0]
                }
                else {
                    throwError("CAMPAIGN", 400, "PARENT_CAMPAIGN_ERROR", "Parent Campaign can't be inactive when creating child campaign");
                }
            }
            else {
                if (parentSearchResponse?.CampaignDetails?.length > 0 && parentSearchResponse?.CampaignDetails?.[0]?.status == "created" &&
                    !parentSearchResponse?.CampaignDetails?.[0]?.isActive) {
                    request.body.parentCampaign = parentSearchResponse?.CampaignDetails[0]
                }
                else {
                    throwError("CAMPAIGN", 400, "PARENT_CAMPAIGN_ERROR", "Parent Campaign can't be active when  updating child campaign");
                }

            }
        }
    }
}

async function validateCampaignName(request: any, actionInUrl: any) {
    const { campaignName, tenantId } = request.body.CampaignDetails;
    if (!campaignName) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "campaignName is required");
    }
    if (!tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required");
    }
    if (campaignName.length >= 2) {
        // const searchBody = {
        //     RequestInfo: request.body.RequestInfo,
            const CampaignDetails = {
                tenantId: tenantId,
                campaignName: campaignName,
                status: [campaignStatuses.drafted, campaignStatuses.started, campaignStatuses.inprogress],
            }
        // }
        if (request.body?.parentCampaign) {
            if (request?.body?.CampaignDetails?.campaignName != request?.body?.parentCampaign?.campaignName) {
                throwError("CAMPAIGN", 400, "CAMPAIGN_NAME_NOT_MATCHING_PARENT_ERROR", "Campaign name should be same as that of parent");
            }
        }
        // const req: any = replicateRequest(request, searchBody)
        const searchResponse: any = await searchProjectTypeCampaignService(CampaignDetails)
        if (Array.isArray(searchResponse?.CampaignDetails)) {
            if (searchResponse?.CampaignDetails?.length > 0) {
                const allCampaigns = searchResponse?.CampaignDetails;
                logger.info(`campaignName to match : ${"'"}${campaignName}${"'"}`)
                const matchingCampaigns: any[] = allCampaigns.filter((campaign: any) => campaign?.campaignName === campaignName);
                for (const campaignWithMatchingName of matchingCampaigns) {
                    if (campaignWithMatchingName && actionInUrl == "create") {
                        if (!request.body.CampaignDetails?.parentId) {
                            throwError("CAMPAIGN", 400, "CAMPAIGN_NAME_ERROR");
                        }
                        else if (campaignWithMatchingName?.id != request.body.CampaignDetails?.parentId) {
                            throwError("CAMPAIGN", 400, "CAMPAIGN_NAME_ERROR");
                        }
                    }
                    else if (campaignWithMatchingName && actionInUrl == "update" && campaignWithMatchingName?.id != request.body.CampaignDetails?.id) {
                        throwError("CAMPAIGN", 400, "CAMPAIGN_NAME_ERROR");
                    }
                }
            }
        }
        else {
            throwError("CAMPAIGN", 500, "CAMPAIGN_SEARCH_ERROR");
        }
    }
}

async function validateById(request: any) {
    const { id, tenantId, action } = request?.body?.CampaignDetails
    if (!id) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "id is required");
    }
    // const searchBody = {
    //     RequestInfo: request.body.RequestInfo,
        const CampaignDetails ={
            tenantId: tenantId,
            ids: [id]
        }
    // }
    // const req: any = replicateRequest(request, searchBody)
    const searchResponse: any = await searchProjectTypeCampaignService(CampaignDetails)
    if (Array.isArray(searchResponse?.CampaignDetails)) {
        if (searchResponse?.CampaignDetails?.length > 0) {
            logger.debug(`CampaignDetails : ${getFormattedStringForDebug(searchResponse?.CampaignDetails)}`);
            request.body.ExistingCampaignDetails = searchResponse?.CampaignDetails[0];
            if (action != "changeDates") {
                if (!(request.body.ExistingCampaignDetails?.status == campaignStatuses?.drafted || request.body.ExistingCampaignDetails?.status == campaignStatuses?.failed)) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", `Campaign can only be updated in drafted or failed state. Change action to changeDates if you want to just update date.`);
                }
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
        const searchResponse: any = await httpRequest(config.host.mdmsV2 + config?.paths?.mdms_v1_search, searchBody, params);
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

function isObjectOrArray(value: any) {
    return typeof value === 'object' || Array.isArray(value);
}

async function validateChangeDatesRequest(request: any) {
    var ExistingCampaignDetails = request?.body?.ExistingCampaignDetails;
    const { startDate: exsistingStartDate, endDate: exsistingEndDate } = ExistingCampaignDetails;
    var newCampaignDetails = request?.body?.CampaignDetails;
    const { startDate: newStartDate, endDate: newEndDate } = newCampaignDetails;

    for (const key in newCampaignDetails) {
        if (!isObjectOrArray(newCampaignDetails[key])) {
            // If the value is not an object or array, compare it with the corresponding value in ExistingCampaignDetails
            if (!(key == "startDate" || key == "endDate" || key == "action") && newCampaignDetails[key] !== ExistingCampaignDetails[key]) {
                // Handle the validation failure (for example, throw an error or log a message)
                throwError("COMMON", 400, "VALIDATION_ERROR", `${key} value in request campaign is not matching with existing campaign`);
            }
        }
    }
    const today: any = Date.now();
    if (exsistingStartDate <= today) {
        if (exsistingStartDate != newStartDate) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "StartDate cannot be updated for ongoing or completed campaign.");
        }
    }
    if (exsistingEndDate < today) {
        if (exsistingEndDate != newEndDate) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "EndDate cannot be updated as campaign is completed.");
        }
    }
    request.body.CampaignDetails = ExistingCampaignDetails;
    request.body.CampaignDetails.action = "changeDates";
    request.body.CampaignDetails.startDate = newStartDate;
    request.body.CampaignDetails.endDate = newEndDate;
}

async function validateCampaignBody(request: any, CampaignDetails: any, actionInUrl: any) {
    const { hierarchyType, action, tenantId, resources, projectType } = CampaignDetails;
    if (action == "changeDates") {
        await validateChangeDatesRequest(request);
    }
    else if (action == "create") {
        validateProjectCampaignMissingFields(CampaignDetails);
        await validateParent(request, actionInUrl);
        await validateMissingBoundaryFromParent(request?.body);
        validateProjectDatesForCampaign(request, CampaignDetails);
        await validateCampaignName(request, actionInUrl);
        if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is not matching with userInfo");
        }
        await validateHierarchyType(request, hierarchyType, tenantId);
        await validateProjectType(request, projectType, tenantId);
        await validateProjectCampaignBoundaries(request?.body?.boundariesCombined, hierarchyType, tenantId, request);
        await validateProjectCampaignResources(resources, request);
        await validateProductVariant(request);
    }
    else {
        validateDraftProjectCampaignMissingFields(CampaignDetails);
        await validateParent(request, actionInUrl);
        await validateMissingBoundaryFromParent(request?.body);
        validateProjectDatesForCampaign(request, CampaignDetails);
        await validateCampaignName(request, actionInUrl);
        await validateHierarchyType(request, hierarchyType, tenantId);
        await validateProjectType(request, projectType, tenantId);
    }
}

function validateProjectDatesForCampaign(request: any, CampaignDetails: any) {
    if (!request?.body?.parentCampaign) {
        const { startDate, endDate } = CampaignDetails;
        if (startDate && endDate && (new Date(endDate).getTime() - new Date(startDate).getTime()) < (24 * 60 * 60 * 1000)) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "endDate must be at least one day after startDate");
        }
        const today: any = Date.now();
        if (startDate <= today) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "startDate cannot be today or past date");
        }
    }
}

async function validateProjectCampaignRequest(request: any, actionInUrl: any) {
    const CampaignDetails = request.body.CampaignDetails;
    const { id, action } = CampaignDetails;
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
    if (!(action == "create" || action == "draft" || action == "changeDates" || action == "retry")) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "action can only be create, draft, retry or changeDates");
    }
    if (actionInUrl == "retry") {
        await validateForRetry(request);
    }
    if (actionInUrl == "update") {
        await validateById(request);
        await validateIsActive(request);
    }
    if (actionInUrl == "create") {
        if (!request?.body?.CampaignDetails?.isActive) {
            request.body.CampaignDetails.isActive = true;
        }
    }
    if (action == "changeDates" && actionInUrl == "create") {
        throwError("COMMON", 400, "VALIDATION_ERROR", "changeDates is not allowed during create");
    }
    await validateCampaignBody(request, CampaignDetails, actionInUrl);
}

async function validateForRetry(request: any) {
    if (!request.body || !request.body.CampaignDetails) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "CampaignDetails are missing in the request body");
    }
    const { id, tenantId } = request.body.CampaignDetails;
    if (!id) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "id is required");
    }
    if (!tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required");
    }
    // const searchBody = {
    //     RequestInfo: request.body.RequestInfo,
        const CampaignDetails= {
            tenantId: tenantId,
            ids: [id]
        }
    // }
    // const req: any = replicateRequest(request, searchBody)
    const searchResponse: any = await searchProjectTypeCampaignService(CampaignDetails)
    if (Array.isArray(searchResponse?.CampaignDetails)) {
        if (searchResponse?.CampaignDetails?.length > 0) {
            logger.debug(`CampaignDetails : ${getFormattedStringForDebug(searchResponse?.CampaignDetails)}`);
            request.body.ExistingCampaignDetails = searchResponse?.CampaignDetails[0];
            if (request.body.ExistingCampaignDetails?.status != campaignStatuses?.failed) {
                throwError("COMMON", 400, "VALIDATION_ERROR", `Campaign can only be retried in failed state.`);
            }
            request.body.CampaignDetails.status = campaignStatuses?.drafted;
            var updatedInnerCampaignDetails = {}
            enrichInnerCampaignDetails(request, updatedInnerCampaignDetails)
            request.body.CampaignDetails.campaignDetails = updatedInnerCampaignDetails;
            const producerMessage: any = {
                CampaignDetails: request?.body?.CampaignDetails
            }
            await produceModifiedMessages(producerMessage, config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC);

            if (!request.body.CampaignDetails.additionalDetails.retryCycle) {
                // If not present, initialize it as an empty array
                request.body.CampaignDetails.additionalDetails.retryCycle = [];
            }

            // Step 2: Push new data to the `retryCycle` array
            request.body.CampaignDetails.additionalDetails.retryCycle.push({
                error: request.body.CampaignDetails.additionalDetails.error,
                retriedAt: Date.now(),
                failedAt: request.body.CampaignDetails.auditDetails.lastModifiedTime
            });
        }
        else {
            throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND");
        }
    }
    else {
        throwError("CAMPAIGN", 500, "CAMPAIGN_SEARCH_ERROR");
    }
}

async function validateProductVariant(request: any) {
    const deliveryRules = request?.body?.CampaignDetails?.deliveryRules;

    if (!Array.isArray(deliveryRules)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "deliveryRules must be an array");
    }

    deliveryRules.forEach((rule: any, index: number) => {
        const productVariants = rule?.resources;
        if (!Array.isArray(productVariants) || productVariants.length === 0) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `deliveryRules[${index}].resources must be a non-empty array`);
        }
    });
    const pvarIds= getPvarIds(request?.body);
    await validatePvarIds(pvarIds as string[]);
    logger.info("Validated product variants successfully");
}

async function validatePvarIds(pvarIds: string[]) {
    // Validate that pvarIds is not null, undefined, or empty, and that no element is null or undefined
    if (!pvarIds?.length || pvarIds.some((id:any) => !id)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "productVariantId is required in every delivery rule's resources");
    }

    // Fetch product variants using the fetchProductVariants function
    const allProductVariants = await fetchProductVariants(pvarIds);

    // Extract the ids of the fetched product variants
    const fetchedIds = new Set(allProductVariants.map((pvar: any) => pvar?.id));

    // Identify missing or invalid product variants
    const missingPvarIds = pvarIds.filter((id: any) => !fetchedIds.has(id));

    if (missingPvarIds.length) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Invalid product variant ${missingPvarIds.length === 1 ? 'id' : 'ids'}: ${missingPvarIds.join(", ")}`);
    }
}


async function validateIsActive(request: any) {
    if (!request?.body?.CampaignDetails.isActive) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Can't update isActive")
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
    const hierarchy = await getHierarchy(request?.query?.tenantId, request?.query?.hierarchyType);
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

async function immediateValidationForTargetSheet(request: any, dataFromSheet: any, differentTabsBasedOnLevel: any, localizationMap: any) {
    logger.info("validating all district tabs present started")
    validateAllDistrictTabsPresentOrNot(request, dataFromSheet, differentTabsBasedOnLevel, localizationMap);
    await validateExtraBoundariesForMicroplan(request, dataFromSheet, localizationMap);
    logger.info("validation of all district tabs present completed")
    for (const key in dataFromSheet) {
        if (key !== getLocalizedName(getBoundaryTabName(), localizationMap) && key !== getLocalizedName(config?.values?.readMeTab, localizationMap)) {
            if (Object.prototype.hasOwnProperty.call(dataFromSheet, key)) {
                const dataArray = (dataFromSheet as { [key: string]: any[] })[key];
                if (dataArray.length === 0) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", `The Target Sheet ${key} you have uploaded is empty`)
                }
                const root = getLocalizedName(differentTabsBasedOnLevel, localizationMap);
                for (const boundaryRow of dataArray) {
                    for (const columns in boundaryRow) {
                        if (columns.startsWith('__EMPTY')) {
                            throwError("COMMON", 400, "VALIDATION_ERROR", `Invalid column has some random data in Target Sheet ${key} at row number ${boundaryRow['!row#number!']}`);
                        }
                        if (!request?.body?.parentCampaignObject && columns.endsWith('(OLD)')) {
                            throwError("COMMON", 400, "VALIDATION_ERROR", "Target template downloaded from update campaign flow has been uploaded in create campaign flow")
                        }
                    }
                    if (!boundaryRow[root]) {
                        throwError("COMMON", 400, "VALIDATION_ERROR", ` ${root} column is empty in Target Sheet ${key} at row number ${boundaryRow['!row#number!']}. Please upload from downloaded template only.`);
                    }
                }
            }
        }
    }
}


function validateAllDistrictTabsPresentOrNot(request: any, dataFromSheet: any, differentTabsBasedOnLevel: any, localizationMap?: any) {
    let tabsIndex = 2;
    logger.info("target sheet getting validated for different districts");
    const tabsOfDistrict = getDifferentDistrictTabs(dataFromSheet[getLocalizedName(config?.boundary?.boundaryTab, localizationMap)], differentTabsBasedOnLevel);
    logger.info("found " + tabsOfDistrict?.length + " districts");
    logger.debug("actual districts in boundary data sheet : " + getFormattedStringForDebug(tabsOfDistrict));
    const tabsFromTargetSheet = Object.keys(dataFromSheet);
    logger.info("districts present in user filled sheet : " + (tabsFromTargetSheet?.length - tabsIndex));
    logger.debug("districts present in user filled sheet (exclude first two tabs): " + getFormattedStringForDebug(tabsFromTargetSheet));

    if (tabsFromTargetSheet.length - tabsIndex !== tabsOfDistrict.length) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `${differentTabsBasedOnLevel} tabs uploaded by user is either less or more than the ${differentTabsBasedOnLevel} in the boundary system. Please upload from downloaded template only.`);
    } else {
        for (let index = tabsIndex; index < tabsFromTargetSheet.length; index++) {
            const tab = tabsFromTargetSheet[index]; // Get the current tab
            if (!tabsOfDistrict.includes(tab)) {
                throwError("COMMON", 400, "VALIDATION_ERROR", `${differentTabsBasedOnLevel} ${tab} not present in selected boundaries.`);
            }
        }
        const MissingDistricts: any = [];
        const campaignBoundaries = request?.body?.campaignBoundaries;
        if (campaignBoundaries && campaignBoundaries?.length > 0) {
            const districtsLocalised = campaignBoundaries
                .filter((data: any) => getLocalizedName(`${request?.body?.ResourceDetails?.hierarchyType}_${data.type}`.toUpperCase(), localizationMap).toLocaleLowerCase() == differentTabsBasedOnLevel.toLowerCase())
                .map((data: any) => getLocalizedName(data?.code, localizationMap)) || [];

            districtsLocalised?.forEach((tab: any) => {
                if (!tabsOfDistrict?.includes(tab)) {
                    MissingDistricts.push(tab);
                }
            });
        }

        if (MissingDistricts.length > 0) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `District${MissingDistricts?.length > 1 ? 's' : ''} ${MissingDistricts.join(', ')} not present in the Target Sheet Uploaded`);
        }
    }

}

function validateSearchProcessTracksRequest(request: any) {
    if (!request?.query?.campaignId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "CampaignId is required in params");
    }
}

async function validateMicroplanRequest(request: any) {
    const { tenantId, campaignId, planConfigurationId } = request.body.MicroplanDetails;
    if (!tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required");
    }
    if (!campaignId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "campignId is required");
    }
    if (!planConfigurationId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "planConfigurationId is required");
    }
    logger.info("All required fields are present");

    await validateCampaignFromId(request);
    await validatePlanFacility(request);
}

async function validatePlanFacility(request: any) {
    const planConfigSearchResponse = await planConfigSearch(request);
    const planFacilitySearchResponse = await planFacilitySearch(request);

    if (planFacilitySearchResponse.PlanFacility.length === 0) {
        throwError("COMMAN", 400, "Plan facilities not found");
    }

    request.body.PlanFacility = planFacilitySearchResponse.PlanFacility;
    request.body.planConfig = planConfigSearchResponse.PlanConfiguration[0];
}

async function validateCampaignFromId(request: any) {
    const { tenantId, campaignId } = request.body.MicroplanDetails;

    // const searchBody = {
    //     RequestInfo: request.body.RequestInfo,
    const campaignDetails = {
        tenantId: tenantId,
        ids: [campaignId]
    }
    // }

    // const req: any = replicateRequest(request, searchBody)
    const searchResponse: any = await searchProjectTypeCampaignService(campaignDetails);

    if (searchResponse?.CampaignDetails?.length == 0) {
        throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND");
    }

    logger.info("Campaign Found");
    request.body.CampaignDetails = searchResponse?.CampaignDetails[0];
}


function validateBoundarySheetDataInCreateFlow(boundarySheetData: any, localizedHeadersOfBoundarySheet: any) {
    const firstColumnValues = new Set();
    const firstColumn = localizedHeadersOfBoundarySheet[0];

    boundarySheetData.forEach((obj: any, index: number) => {
        let firstEmptyFound = false;
        // Collect value from the first column
        if (obj[firstColumn]) {
            firstColumnValues.add(obj[firstColumn]);
        }
        if (firstColumnValues.size > 1) {
            throwError("BOUNDARY", 400, "BOUNDARY_SHEET_FIRST_COLUMN_INVALID_ERROR",
                `Data is invalid: The "${firstColumn}" column must contain only one unique value across all rows.`);
        }

        for (const header of localizedHeadersOfBoundarySheet) {
            const value = obj[header];

            if (!value) {
                // Mark that an empty value has been found for the first time
                firstEmptyFound = true;
            } else if (firstEmptyFound) {
                // If a non-empty value is found after an empty value in the expected order, throw an error
                throwError("BOUNDARY", 400, "BOUNDARY_SHEET_UPLOADED_INVALID_ERROR",
                    `Data is invalid in object at index ${index + 2}: Non-empty value for key "${header}" found after an empty value in the left.`);
            }
        }
    });
}

export function validateEmptyActive(data: any, type: string, localizationMap?: { [key: string]: string }) {
    let isActiveRowsZero = true;
    const activeColumnName = createAndSearch?.[type]?.activeColumnName ? getLocalizedName(createAndSearch?.[type]?.activeColumnName, localizationMap) : null;
    if(Array.isArray(data)){
        data.forEach((item: any) => {
            const active = activeColumnName ? item[activeColumnName] : usageColumnStatus.active;
            if (active == usageColumnStatus.active) {
                isActiveRowsZero = false;
                return;
            }
        });
    }
    else{
        // Data is not coming from a single sheet so no require for this active check
        isActiveRowsZero = false;
    }
    if(isActiveRowsZero){
        throwError("COMMON", 400, "VALIDATION_ERROR_ACTIVE_ROW");
    }
}

export async function validateResourceDetails(ResourceDetails : ResourceDetails) {
    logger.info("validating resource details");
    const type = ResourceDetails?.type;
    const hierarchyType = ResourceDetails?.hierarchyType;
    const campaignId = ResourceDetails?.campaignId;
    const tenantId = ResourceDetails?.tenantId;
    const fileStoreId = ResourceDetails?.fileStoreId;
    validateTypeForProcess(type);
    await validateHierarchyDefination(hierarchyType,tenantId);
    await validateCampaignViaId(campaignId,tenantId);
    try {
        const fileResponse = await fetchFileFromFilestore(fileStoreId, tenantId);
        if(!fileResponse){
            throwError("CAMPAIGN", 400, "VALIDATION_ERROR", `file not found, check fileStoreId`);
        }
    } catch (error) {
        throwError("CAMPAIGN", 400, "VALIDATION_ERROR", `file not found, check fileStoreId`);
    }
    logger.info("resource details validated");
};

async function validateHierarchyDefination(hierarchyType : string,tenantId : string) {
    const response = await searchBoundaryRelationshipDefinition({
        BoundaryTypeHierarchySearchCriteria: {
            tenantId: tenantId,
            hierarchyType: hierarchyType
        }
    });

    if (response?.BoundaryHierarchy?.[0]?.boundaryHierarchy?.length > 0) {
        logger.info(`hierarchyType : ${hierarchyType} :: got validated`);
    }
    else {
        throwError(`CAMPAIGN`, 400, "VALIDATION_ERROR", `hierarchyType ${hierarchyType} not found or invalid`);
    }
}

async function validateCampaignViaId(campaignId : string,tenantId : string) {
    const response = await searchProjectTypeCampaignService({
        tenantId: tenantId,
        ids: [campaignId]
    });
    if (response?.CampaignDetails?.length > 0) {
        logger.info(`campaignId got validated`);
    }
    else {
        throwError(`CAMPAIGN`, 400, "VALIDATION_ERROR", `campaignId not found or invalid`);
    }
}

function validateTypeForProcess(type : string){
    const config = JSON.parse(JSON.stringify(processTemplateConfigs));
    const types = Object.keys(config);
    if(!types.includes(type)){
        throwError("CAMPAIGN", 400, "VALIDATION_ERROR", `type ${type} not found or invalid`);
    }
}

export async function validateGenerateQuery(generateTemplateQuery : GenerateTemplateQuery){
    const config = JSON.parse(JSON.stringify(generationtTemplateConfigs));
    const types = Object.keys(config);
    if(!types.includes(generateTemplateQuery.type)){
        throwError("CAMPAIGN", 400, "VALIDATION_ERROR", `type ${generateTemplateQuery.type} not found or invalid`);
    }
    const tenantId = generateTemplateQuery.tenantId;
    await validateHierarchyDefination(generateTemplateQuery.hierarchyType, tenantId);
    await validateCampaignViaId(generateTemplateQuery.campaignId, tenantId);
}

export function validateDatasWithSchema(
        sheetDatas: any[],
        schema: any,
        errors: any[],
        localizationMap?: Record<string, string>
    ) {
        const ajv = new Ajv({ allErrors: true, strict: false });
        addErrors(ajv); // Enables custom error messages
        const validate = ajv.compile(schema);

        const uniqueFields = schema.unique || []; // Expecting schema to have `unique: ["field1", "field2"]`
        const uniquenessMap: Record<string, Set<any>> = {};

        // Initialize sets for each unique field
        uniqueFields.forEach((field : any) => {
            uniquenessMap[field] = new Set();
        });

        for (let i = 0; i < sheetDatas.length; i++) {
            const item = sheetDatas[i];
            const isValid = validate(item);

            if (!isValid && validate.errors) {
                for (const error of validate.errors) {
                    let key = error.instancePath || "";
                    let message = error.message || "";

                    if (key.startsWith("/")) key = key.slice(1);

                    if (error.keyword === "required") {
                        key = error.params?.missingProperty || "";
                        message = `Data in column '${getLocalizedName(key, localizationMap)}' is required`;
                    }
                    else{
                        message = `Data in column '${getLocalizedName(key, localizationMap)}' ${message}`;
                    }

                    errors.push({
                        row: i + 3,
                        message,
                    });
                }
            }

            // Uniqueness validation
            for (const field of uniqueFields) {
                const value = item[field];
                if (value !== undefined && value !== null) {
                    if (uniquenessMap[field].has(value)) {
                        errors.push({
                            row: i + 3,
                            message: `Duplicate value "${value}" found in column '${getLocalizedName(field, localizationMap)}'`,
                        });
                    } else {
                        uniquenessMap[field].add(value);
                    }
                }
            }
        }
    }

export function validateActiveFieldMinima(datas:any[], activeKey : string, errors : any[]){
    let activeCount = 0;
    for(const data of datas){
        if(data[activeKey] == usageColumnStatus.active){
            activeCount++;
        }
    }
    if(activeCount < 1){
        errors.push({
            row: 3,
            message: "At least one active entry is required. All entries are inactive in this sheet.",
        });
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
    validateBoundarySheetData,
    validateDownloadRequest,
    validateTargetSheetData,
    immediateValidationForTargetSheet,
    validateBoundaryOfResouces,
    validateSearchProcessTracksRequest,
    validateParent,
    validateForRetry,
    validateBoundarySheetDataInCreateFlow,
    validateMicroplanRequest
}
