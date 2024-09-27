import createAndSearch from "../config/createAndSearch";
import { getLocalizedName } from "../utils/campaignUtils";

export function validatePhoneNumberSheetWise(datas: any[], localizationMap: any, rowMapping: any) {
    for (const data of datas) {
        const phoneColumn = getLocalizedName("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER_MICROPLAN", localizationMap);
        if (data[phoneColumn]) {
            var phoneNumber = data[phoneColumn];
            phoneNumber = phoneNumber.toString()
            if (phoneNumber.length != 10) {
                const row = data["!row#number!"];
                if (!rowMapping[row]) {
                    rowMapping[row] = [];
                }
                rowMapping[row].push("The ‘Contact number’ entered is invalid,it should be of 10 digit, please update and re-upload");
            }
        }
        else {
            const row = data["!row#number!"];
            if (!rowMapping[row]) {
                rowMapping[row] = [];
            }
            rowMapping[row].push("The ‘Contact number’ is a mandatory field in the file, please update and re-upload");
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
            name = name.toString()
            if (name.length > 128 || name.length < 2) {
                const row = data["!row#number!"];
                if (!rowMapping[row]) {
                    rowMapping[row] = [];
                }
                rowMapping[row].push("The ‘Name’ should be between 2 to 128 characters. Please update and re-upload");
            }
        }
        else {
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
                if (!localizedUniqueIdentifierColumnName || !item[localizedUniqueIdentifierColumnName]) {
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