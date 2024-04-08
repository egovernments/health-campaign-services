"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
var config_1 = __importDefault(require("../config"));
var createAndSearch = {
    "facility": {
        requiresToSearchFromSheet: [
            {
                sheetColumnName: "Facility Code",
                searchPath: "Facility.id"
            }
        ],
        boundaryValidation: {
            column: "Boundary Code"
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
                    "type": "string",
                    "enum": ["Storing Resource"]
                },
                "Facility Status": {
                    "type": "string",
                    "enum": ["Temp", "Perm"]
                },
                "Facility Capacity": {
                    "type": "number",
                    "minimum": 0,
                    "maximum": 9223372036854775807
                }
            },
            "required": [
                "Facility Name",
                "Facility Type",
                "Facility Status",
                "Facility Capacity"
            ]
        },
        uniqueIdentifier: "id",
        uniqueIdentifierColumn: "A",
        matchEachKey: true,
        parseArrayConfig: {
            sheetName: "List of Available Facilities",
            parseLogic: [
                {
                    sheetColumnName: "Facility Code",
                    resultantPath: "id",
                    type: "string"
                },
                {
                    sheetColumnName: "Facility Name",
                    resultantPath: "name",
                    type: "string"
                },
                {
                    sheetColumnName: "Facility Type",
                    resultantPath: "usage",
                    type: "string"
                },
                {
                    sheetColumnName: "Facility Status",
                    resultantPath: "isPermanent",
                    type: "boolean",
                    conversionCondition: {
                        "Perm": "true",
                        "Temp": ""
                    }
                },
                {
                    sheetColumnName: "Facility Capacity",
                    resultantPath: "storageCapacity",
                    type: "number"
                }
            ],
            tenantId: {
                getValueViaPath: "ResourceDetails.tenantId",
                resultantPath: "tenantId"
            }
        },
        createBulkDetails: {
            limit: 50,
            createPath: "Facilities",
            url: config_1.default.host.facilityHost + "facility/v1/bulk/_create"
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
            url: config_1.default.host.facilityHost + "facility/v1/_search",
            searchPath: "Facilities"
        }
    }
};
exports.default = createAndSearch;
