import config from ".";
const createAndSearch: any = {
    "facility": {
        requiresToSearchFromSheet: [
            {
                sheetColumnName: "HCM_ADMIN_CONSOLE_FACILITY_CODE",
                searchPath: "Facility.id"
            }
        ],
        boundaryValidation: {
            column: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY",
        },
        sheetSchema: {
            "$schema": "http://json-schema.org/draft-07/schema#",
            "title": "FacilityTemplateSchema",
            "type": "object",
            "properties": {
                "Facility Name": {
                    "type": "string",
                    "maxLength": 2000,
                    "minLength": 1
                },
                "Facility Type": {
                    // "type": "string",
                    "enum": ["Warehouse", "Health Facility"]
                },
                "Facility Status": {
                    // "type": "string",
                    "enum": ["Temporary", "Permanent"]
                },
                "Capacity": {
                    "type": "number",
                    "minimum": 1,
                    "maximum": 100000000
                }
            },
            "required": [
                "Facility Name",
                "Facility Type",
                "Facility Status",
                "Capacity"
            ],
            "unique": [
                "Facility Name"
            ]
        },
        uniqueIdentifier: "id",
        uniqueIdentifierColumn: "A",
        activeColumn: "G",
        activeColumnName: "HCM_ADMIN_CONSOLE_FACILITY_USAGE",
        uniqueIdentifierColumnName: "HCM_ADMIN_CONSOLE_FACILITY_CODE",
        matchEachKey: true,
        parseArrayConfig: {
            sheetName: "HCM_ADMIN_CONSOLE_FACILITIES",
            parseLogic: [
                {
                    sheetColumn: "A",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_FACILITY_CODE",
                    resultantPath: "id",
                    type: "string"
                },
                {
                    sheetColumn: "B",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_FACILITY_NAME",
                    resultantPath: "name",
                    type: "string"
                },
                {
                    sheetColumn: "C",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_FACILITY_TYPE",
                    resultantPath: "usage",
                    type: "string"
                },
                {
                    sheetColumn: "D",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_FACILITY_STATUS",
                    resultantPath: "isPermanent",
                    type: "boolean",
                    conversionCondition: {
                        "Permanent": "true",
                        "Temporary": ""
                    }
                },
                {
                    sheetColumn: "E",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_FACILITY_CAPACITY",
                    resultantPath: "storageCapacity",
                    type: "number"
                },
                {
                    sheetColumn: "F",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY",
                    resultantPath: "address.locality.code",
                    type: "string"
                }
            ],
            tenantId: {
                getValueViaPath: "ResourceDetails.tenantId",
                resultantPath: "tenantId"
            }
        },
        createDetails: {
            url: config.host.facilityHost + config.paths.facilityCreate,
            createPath: "Facility"
        },
        searchDetails: {
            searchElements: [
                {
                    keyPath: "tenantId",
                    getValueViaPath: "ResourceDetails.tenantId",
                    isInParams: true,
                    isInBody: false,
                },
                {
                    keyPath: "Facility",
                    isInParams: false,
                    isInBody: true,
                }
            ],
            searchLimit: {
                keyPath: "limit",
                value: "200",
                isInParams: true,
                isInBody: false,
            },
            searchOffset: {
                keyPath: "offset",
                value: "0",
                isInParams: true,
                isInBody: false,
            },
            url: config.host.facilityHost + "facility/v1/_search",
            searchPath: "Facilities"
        }
    },
    "boundary": {
        parseArrayConfig: {
            sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE",
        }
    },
    "user": {
        requiresToSearchFromSheet: [
            {
                sheetColumnName: "UserService Uuids",
                searchPath: "user.mobileNumber"
            }],
        boundaryValidation: {
            column: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY",
        },
        sheetSchema: {
            "$schema": "http://json-schema.org/draft-07/schema#",
            "title": "UserTemplateSchema",
            "type": "object",
            "properties": {
                "Name of the Person (Mandatory)": {
                    "type": "string",
                    "maxLength": 128,
                    "minLength": 1
                },
                "Phone Number (Mandatory)": {
                    "type": "integer",
                    "minimum": 100000000,
                    "maximum": 9999999999
                },
                "Role (Mandatory)": {
                    "type": "string",
                    "enum": ["Registrar", "Distributor", "Supervisor", "Help Desk", "Monitor Local", "Logistical officer"]
                },
                "Employment Type (Mandatory)": {
                    "enum": ["Temporary", "Permanent"]
                },
                "Payee Phone Number": {
                    "type": "string",
                    "maxLength": 20
                },
                "Payment Provider": {
                    "type": "string",
                    "maxLength": 128
                },
                "Payee Name": {
                    "type": "string",
                    "maxLength": 128
                },
                "Bank Account": {
                    "type": "string",
                    "maxLength": 64
                },
                "Bank Code": {
                    "type": "string",
                    "maxLength": 64
                }
            },
            "required": [
                "Name of the Person (Mandatory)",
                "Phone Number (Mandatory)",
                "Role (Mandatory)",
                "Employment Type (Mandatory)"
            ],
            "unique": [
                "Phone Number (Mandatory)"
            ]
        },
        parseArrayConfig: {
            sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
            parseLogic: [
                {
                    sheetColumn: "A",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_USER_NAME",
                    resultantPath: "user.name",
                    type: "string"
                },
                {
                    sheetColumn: "B",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER",
                    resultantPath: "user.mobileNumber",
                    type: "string"
                },
                {
                    sheetColumn: "C",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_USER_ROLE",
                    resultantPath: "user.roles",
                    type: "string"
                },
                {
                    sheetColumn: "D",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_USER_EMPLOYMENT_TYPE",
                    resultantPath: "employeeType",
                    conversionCondition: {
                        "Permanent": "PERMANENT",
                        "Temporary": "TEMPORARY"
                    }
                },
                {
                    sheetColumn: "E",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY",
                    resultantPath: "jurisdictions",
                    type: "string"
                },
                {
                    sheetColumn: "F",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_USER_PAYEE_PHONE_NUMBER",
                    resultantPath: "payeePhoneNumber",
                    type: "string"
                },
                {
                    sheetColumn: "G",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_USER_PAYMENT_PROVIDER",
                    resultantPath: "paymentProvider",
                    type: "string"
                },
                {
                    sheetColumn: "H",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_USER_PAYEE_NAME",
                    resultantPath: "payeeName",
                    type: "string"
                },
                {
                    sheetColumn: "I",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_USER_BANK_ACCOUNT",
                    resultantPath: "bankAccount",
                    type: "string"
                },
                {
                    sheetColumn: "J",
                    sheetColumnName: "HCM_ADMIN_CONSOLE_USER_BANK_CODE",
                    resultantPath: "bankCode",
                    type: "string"
                },
                {
                    sheetColumn: "Q",
                    sheetColumnName: "UserName",
                    resultantPath: "user.userName",
                    type: "string"
                },
                {
                    sheetColumn: "R",
                    sheetColumnName: "Password",
                    resultantPath: "user.password",
                    type: "string"
                }
            ],
            tenantId: {
                getValueViaPath: "ResourceDetails.tenantId",
                resultantPath: "tenantId"
            }
        },
        uniqueIdentifier: "user.userServiceUuid",
        uniqueIdentifierColumn: "U",
        uniqueIdentifierColumnName: "UserService Uuids",
        activeColumn: "O",
        activeColumnName: "HCM_ADMIN_CONSOLE_USER_USAGE",
        createBulkDetails: {
            limit: 50,
            createPath: "Employees",
            url: config.host.hrmsHost + config.paths.hrmsEmployeeCreate
        },
        searchDetails: {
            searchElements: [
                {
                    keyPath: "tenantId",
                    getValueViaPath: "ResourceDetails.tenantId",
                    isInParams: true,
                    isInBody: false,
                }
            ],
            searchLimit: {
                keyPath: "limit",
                value: "50",
                isInParams: true,
                isInBody: false,
            },
            searchOffset: {
                keyPath: "offset",
                value: "0",
                isInParams: true,
                isInBody: false,
            },
            url: config.host.hrmsHost + config.paths.hrmsEmployeeSearch,
            searchPath: "Employees"
        }
    },
    "boundaryWithTarget": {
        parseArrayConfig: {
            sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
        },
        boundaryValidation: {
            column: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE"
        }
    }
}

export default createAndSearch;
