export const campaignDetailsDraftSchema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "hierarchyType": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        },
        "tenantId": {
            "type": "string",
            "maxLength": 64,
            "minLength": 1
        },
        "campaignName": {
            "type": "string",
            "maxLength": 250,
            "minLength": 2
        },
        "action": {
            "type": "string",
            "enum": ["create", "draft", "retry"],
            "maxLength": 64,
            "minLength": 1
        },
        "startDate": {
            "type": "integer"
        },
        "endDate": {
            "type": "integer"
        },
        "boundaries": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "code": {
                        "type": "string",
                        "maxLength": 64,
                        "minLength": 1
                    },
                    "type": {
                        "type": "string",
                        "maxLength": 128,
                        "minLength": 1
                    },
                    "isRoot": {
                        "type": "boolean"
                    },
                    "includeAllChildren": {
                        "type": "boolean"
                    }
                }
            }
        },
        "resources": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "filestoreId": {
                        "type": "string",
                        "maxLength": 128,
                        "minLength": 1
                    },
                    "type": {
                        "type": "string",
                        "maxLength": 128,
                        "minLength": 1
                    },
                    "filename": {
                        "type": "string",
                        "maxLength": 128,
                        "minLength": 1,
                        "pattern": "^.+\\.(xlsx|xls)$"
                    },
                    "resourceId": {
                        "type": "string",
                        "maxLength": 128,
                        "minLength": 1
                    }
                },
            }
        },
        "projectType": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        },
        "deliveryRules": {
            "type": "array"
        },
        "additionalDetails": {
            "type": "object"
        },
        "isActive":{
            "type":"boolean"
        }
    },
    "required": ["tenantId", "campaignName", "hierarchyType"]
};
