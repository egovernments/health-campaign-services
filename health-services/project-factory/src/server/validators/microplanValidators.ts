import { getBoundaryColumnName, getBoundaryTabName } from "../utils/boundaryUtils";
import createAndSearch from "../config/createAndSearch";
import { getLocalizedName } from "../utils/campaignUtils";
import { resourceDataStatuses, usageColumnStatus } from "../config/constants";
import config from "../config";
import { isMicroplanRequest } from "../utils/microplanUtils";
import { throwError } from "../utils/genericUtils";
export function validatePhoneNumberSheetWise(datas: any[], localizationMap: any, rowMapping: any) {
    for (const data of datas) {
        const phoneColumn = getLocalizedName("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER_MICROPLAN", localizationMap);
        if (data[phoneColumn]) {
            let phoneNumber = data[phoneColumn].toString();

            // Check if the phone number is numeric and has exactly 10 digits
            const isNumeric = /^\d+$/.test(phoneNumber);
            if (phoneNumber.length !== 10 || !isNumeric) {
                const row = data["!row#number!"];
                if (!rowMapping[row]) {
                    rowMapping[row] = [];
                }
                rowMapping[row].push("The ‘Contact number’ entered is invalid, it should be a 10-digit number and contain only digits. Please update and re-upload.");
            }
        } else {
            const row = data["!row#number!"];
            if (!rowMapping[row]) {
                rowMapping[row] = [];
            }
            rowMapping[row].push("The ‘Contact number’ is a mandatory field in the file. Please update and re-upload.");
        }
    }
}


export function validateEmailSheetWise(datas: any[], localizationMap: any, rowMapping: any) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/; // Simple email regex pattern

    for (const data of datas) {
        const emailColumn = getLocalizedName("HCM_ADMIN_CONSOLE_USER_EMAIL_MICROPLAN", localizationMap);
        if (data[emailColumn]) {
            let email = data[emailColumn].toString();

            if (!emailRegex.test(email)) { // Validate email format with regex
                const row = data["!row#number!"];
                if (!rowMapping[row]) {
                    rowMapping[row] = [];
                }
                rowMapping[row].push("The ‘Email’ entered is invalid. Please provide a valid email address and re-upload.");
            }
        }
    }
}

export function validateNameSheetWise(datas: any[], localizationMap: any, rowMapping: any) {
    for (const data of datas) {
        const nameColumn = getLocalizedName("HCM_ADMIN_CONSOLE_USER_NAME_MICROPLAN", localizationMap);
        if (data[nameColumn]) {
            var name = data[nameColumn];
            name = name.toString();
            const row = data["!row#number!"];

            // Check name length
            if (name.length > 128 || name.length < 2) {
                if (!rowMapping[row]) {
                    rowMapping[row] = [];
                }
                rowMapping[row].push("The ‘Name’ should be between 2 to 128 characters. Please update and re-upload");
            }
            else {
                // Check if name contains at least one alphabetic character
                const hasAlphabetic = /[a-zA-Z]/.test(name);
                if (!hasAlphabetic) {
                    if (!rowMapping[row]) {
                        rowMapping[row] = [];
                    }
                    rowMapping[row].push("The ‘Name’ should contain at least one alphabetic character. Please update and re-upload");
                }
            }

        } else {
            const row = data["!row#number!"];
            if (!rowMapping[row]) {
                rowMapping[row] = [];
            }
            rowMapping[row].push("The ‘Name’ is a mandatory field in the file. Please update and re-upload");
        }
    }
}


export function validateUserForMicroplan(data: any, sheetName: any, request: any, errorMap: any, newSchema: any, rowMapping: any, localizationMap: any) {
    if (data?.length > 0) {
        validatePhoneNumberSheetWise(data, localizationMap, rowMapping);
        validateEmailSheetWise(data, localizationMap, rowMapping);
        validateNameSheetWise(data, localizationMap, rowMapping);
        validateUniqueSheetWise(newSchema, data, request, rowMapping, localizationMap);
    }
    else {
        errorMap[sheetName] = { 2: ["Data rows cannot be empty"] };
    }
}

export function validateUniqueSheetWise(schema: any, data: any[], request: any, rowMapping: any, localizationMap: any) {
    if (schema?.unique) {
        const uniqueElements = schema.unique;

        for (const element of uniqueElements) {
            const uniqueMap = new Map();

            // Iterate over each data object and check uniqueness
            for (const item of data) {
                const uniqueIdentifierColumnName = createAndSearch?.[request?.body?.ResourceDetails?.type]?.uniqueIdentifierColumnName;
                const localizedUniqueIdentifierColumnName = getLocalizedName(uniqueIdentifierColumnName, localizationMap);
                const value = item[element];
                const rowNum = item['!row#number!'];
                if (!localizedUniqueIdentifierColumnName || !item[localizedUniqueIdentifierColumnName] && value != undefined) {
                    if (uniqueMap.has(value)) {
                        if (!rowMapping[rowNum]) {
                            rowMapping[rowNum] = [];
                        }
                        rowMapping[rowNum].push(`Duplicate value '${value}' found for '${element}'`);
                    }
                    // Add the value to the map
                    uniqueMap.set(value, rowNum);
                }
            }
        }
    }
}

export function validateRequiredTargetsForMicroplanCampaigns(data: any, errors: any, localizedTargetColumnNames: any, localizationMap?: { [key: string]: string }) {
    for (const key in data) {
        if (key !== getLocalizedName(getBoundaryTabName(), localizationMap) && key !== getLocalizedName(config?.values?.readMeTab, localizationMap)) {
            if (Array.isArray(data[key])) {
                const boundaryData = data[key];
                boundaryData.forEach((obj: any, index: number) => {
                    var totalTarget = 0, totalTargetSumFromColumns = 0;
                    for (let i = 0; i < localizedTargetColumnNames.length; i++) {
                        const targetColumn = localizedTargetColumnNames[i];
                        const target = obj[targetColumn];
                        if (target !== 0 && !target) {
                            errors.push({
                                status: "INVALID",
                                rowNumber: obj["!row#number!"],
                                errorDetails: `Data in column '${targetColumn}' can’t be empty, please update the data and re-upload`,
                                sheetName: key
                            });
                        } else if (typeof target !== 'number') {
                            errors.push({
                                status: "INVALID",
                                rowNumber: obj["!row#number!"],
                                errorDetails: `Data in column '${targetColumn}' must be a whole number from 1 to 100000000. Please update the data and re-upload.`,
                                sheetName: key
                            });
                        } else if (target < 1 || target > 100000000) {
                            errors.push({
                                status: "INVALID",
                                rowNumber: obj["!row#number!"],
                                errorDetails: `Data in column '${targetColumn}' must be a whole number from 1 to 100000000. Please update the data and re-upload.`,
                                sheetName: key
                            });
                        } else if (!Number.isInteger(target)) {
                            errors.push({
                                status: "INVALID",
                                rowNumber: obj["!row#number!"],
                                errorDetails: `Data in column '${targetColumn}' must be a whole number from 1 to 100000000. Please update the data and re-upload.`,
                                sheetName: key
                            });
                        }
                        if (i == 0) {
                            totalTarget = target;
                        }
                        else {
                            totalTargetSumFromColumns += target;
                        }
                    }
                    if (totalTargetSumFromColumns > totalTarget) {
                        errors.push({
                            status: "INVALID",
                            rowNumber: obj["!row#number!"],
                            errorDetails: `Data in other target columns must be less than or equal to '${localizedTargetColumnNames[0]}'`,
                            sheetName: key
                        });
                    }
                });
            }
        }
    }
}

export function validateLatLongForMicroplanCampaigns(data: any, errors: any, localizationMap?: { [key: string]: string }) {
    for (const key in data) {
        if (key !== getLocalizedName(getBoundaryTabName(), localizationMap) && key !== getLocalizedName(config?.values?.readMeTab, localizationMap)) {
            const latLongUnlocalisedColumns = config.values.latLongColumns?.split(',') || [];
            const latLongColumns = latLongUnlocalisedColumns?.map((column: string) => getLocalizedName(column, localizationMap));
            const latLongColumnsSet = new Set(latLongColumns);
            if (Array.isArray(data[key])) {
                const boundaryData = data[key];
                boundaryData.forEach((obj: any, index: number) => {
                    for (const column of Object.keys(obj)) {
                        if (latLongColumnsSet.has(column)) {
                            const value = obj[column];
                            if (typeof value !== 'number') {
                                errors.push({
                                    status: "INVALID",
                                    rowNumber: obj["!row#number!"],
                                    errorDetails: `Data in column '${column}' must comply with the guideline structure, please update the data and re-upload`,
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


function validateLatLongForFacility(data: any, errors: any, localizationMap?: { [key: string]: string }) {
    const latLongUnlocalisedColumns = config.values.latLongColumns?.split(',') || [];
    const latLongColumns = latLongUnlocalisedColumns?.map((column: string) => getLocalizedName(column, localizationMap));
    const latLongColumnsSet = new Set(latLongColumns);
    for (const column of Object.keys(data)) {
        if (latLongColumnsSet.has(column)) {
            const value = data[column];
            if (typeof value !== 'number') {
                errors.push({
                    status: "INVALID",
                    rowNumber: data["!row#number!"],
                    errorDetails: `Data in column '${column}' must comply with the guideline structure, please update the data and re-upload`
                });
            }
        }
    }
};

export function validateMicroplanFacility(request: any, data: any, localizationMap: any) {
    const uniqueIdentifierColumnName = getLocalizedName(createAndSearch?.[request?.body?.ResourceDetails?.type]?.uniqueIdentifierColumnName, localizationMap);
    const activeColumnName = createAndSearch?.[request?.body?.ResourceDetails?.type]?.activeColumnName ? getLocalizedName(createAndSearch?.[request?.body?.ResourceDetails?.type]?.activeColumnName, localizationMap) : null;
    var errors: any = []
    data.forEach((item: any) => {
        if (activeColumnName) {
            if (!item?.[activeColumnName]) {
                errors.push({ status: "INVALID", rowNumber: item?.["!row#number!"], errorDetails: `Data in ${activeColumnName} column can’t be empty, please update the data and re-upload` });
            }
            else if (item?.[activeColumnName] != usageColumnStatus.active && item?.[activeColumnName] != usageColumnStatus.inactive) {
                errors.push({ status: "INVALID", rowNumber: item?.["!row#number!"], errorDetails: `Data in ${activeColumnName} column must be equal to one of the allowed values. Allowed values are ${usageColumnStatus.active}, ${usageColumnStatus.inactive}.` });
            }
        }
        const active = activeColumnName ? item[activeColumnName] : usageColumnStatus.active;
        if (active == usageColumnStatus.active || !item?.[uniqueIdentifierColumnName]) {
            enrichErrorForFcailityMicroplan(request, item, errors, localizationMap);
            validateLatLongForFacility(item, errors, localizationMap);
        }
    });
    request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
    if (request?.body?.sheetErrorDetails && Array.isArray(request?.body?.sheetErrorDetails) && request?.body?.sheetErrorDetails?.length > 0) {
        request.body.ResourceDetails.status = resourceDataStatuses.invalid;
    }
}

function enrichErrorForFcailityMicroplan(request: any, item: any, errors: any = [], localizationMap?: { [key: string]: string }) {
    const projectType = request?.body?.projectTypeCode;
    const nameColumn = getLocalizedName("HCM_ADMIN_CONSOLE_FACILITY_NAME_MICROPLAN", localizationMap);
    if (!item?.[nameColumn]) {
        errors.push({ status: "INVALID", rowNumber: item?.["!row#number!"], errorDetails: `Data in ${nameColumn} column can’t be empty, please update the data and re-upload` })
    }
    const facilityTypeColumn = getLocalizedName("HCM_ADMIN_CONSOLE_FACILITY_TYPE_MICROPLAN", localizationMap);
    if (!item?.[facilityTypeColumn]) {
        errors.push({ status: "INVALID", rowNumber: item?.["!row#number!"], errorDetails: `Data in ${facilityTypeColumn} column can’t be empty, please update the data and re-upload` })
    }
    const faciltyStatusColumn = getLocalizedName("HCM_ADMIN_CONSOLE_FACILITY_STATUS_MICROPLAN", localizationMap);
    if (!item?.[faciltyStatusColumn]) {
        errors.push({ status: "INVALID", rowNumber: item?.["!row#number!"], errorDetails: `Data in ${faciltyStatusColumn} column can’t be empty, please update the data and re-upload` })
    }
    const facilityCapacityColumn = getLocalizedName(`HCM_ADMIN_CONSOLE_FACILITY_CAPACITY_MICROPLAN_${projectType}`, localizationMap);
    if (!item?.[facilityCapacityColumn]) {
        errors.push({ status: "INVALID", rowNumber: item?.["!row#number!"], errorDetails: `Data in ${facilityCapacityColumn} column can’t be empty or zero, please update the data and re-upload` })
    }
    else if (typeof (item?.[facilityCapacityColumn]) != "number") {
        errors.push({ status: "INVALID", rowNumber: item?.["!row#number!"], errorDetails: `Data in ${facilityCapacityColumn} column must be a number from 0 to 100000000` })
    }
    else if (item?.[facilityCapacityColumn] < 0 || item?.[facilityCapacityColumn] > 100000000) {
        errors.push({ status: "INVALID", rowNumber: item?.["!row#number!"], errorDetails: `Data in ${facilityCapacityColumn} column must be a number from 0 to 100000000` })
    }
    const fixedPostColumn = getLocalizedName("HCM_ADMIN_CONSOLE_FACILITY_FIXED_POST_MICROPLAN", localizationMap);
    if (request?.body?.showFixedPost && !item?.[fixedPostColumn]) {
        errors.push({ status: "INVALID", rowNumber: item?.["!row#number!"], errorDetails: `Data in ${fixedPostColumn} column can’t be empty, please update the data and re-upload` })
    }
    const boundaryColumn = getLocalizedName("HCM_ADMIN_CONSOLE_RESIDING_BOUNDARY_CODE_MICROPLAN", localizationMap);
    if (!item?.[boundaryColumn]) {
        errors.push({ status: "INVALID", rowNumber: item?.["!row#number!"], errorDetails: `Data in ${boundaryColumn} column can’t be empty, please update the data and re-upload` })
    }
}

export function validateFacilityBoundaryForLowestLevel(request: any, boundaries: any, rowData: any, errors: any = [], localizationMap?: { [key: string]: string }) {
    if (request?.body?.ResourceDetails?.type == "facility" && request?.body?.ResourceDetails?.additionalDetails?.source == "microplan") {
        const hierarchy = request?.body?.hierarchyType?.boundaryHierarchy
        const lastLevel = hierarchy?.[hierarchy.length - 1]?.boundaryType
        for (const data of rowData?.boundaryCodes) {
            const boundaryFromBoundariesType = boundaries.find((boundary: any) => boundary.code == data)?.type
            if (boundaryFromBoundariesType != lastLevel) {
                errors.push({ status: "INVALID", rowNumber: rowData?.rowNumber, errorDetails: `${data} is not a ${lastLevel} level boundary` })
            }
        }
    }
}



export async function validateExtraBoundariesForMicroplan(request: any, dataFromSheet: any, localizationMap: any) {
    if (await isMicroplanRequest(request)) {
        const campaignBoundariesSet = new Set(request?.body?.campaignBoundaries?.map((boundary: any) => boundary.code));
        for (const key in dataFromSheet) {
            if (key !== getLocalizedName(getBoundaryTabName(), localizationMap) && key !== getLocalizedName(config?.values?.readMeTab, localizationMap)) {
                if (Object.prototype.hasOwnProperty.call(dataFromSheet, key)) {
                    const dataArray = (dataFromSheet as { [key: string]: any[] })[key];
                    for (const boundaryRow of dataArray) {
                        const boundaryCode = boundaryRow[getLocalizedName(getBoundaryColumnName(), localizationMap)];
                        if (!campaignBoundariesSet.has(boundaryCode)) {
                            throwError("COMMON", 400, "VALIDATION_ERROR", `Some boundaries in uploaded sheet are not present in campaign boundaries. Please upload from downloaded template only.`);
                        }
                    }
                }
            }
        }
    }
}