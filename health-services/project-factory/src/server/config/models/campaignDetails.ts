export const campaignDetailsSchema = {
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
            "enum": ["create", "draft", "changeDates"],
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
                },
                "required": ["code", "type"]
            }
        },
        "resources": {
            "type": "array",
            "maxItems": 3,
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
                "required": ["filestoreId", "type", "filename", "resourceId"]
            }
        },
        "projectType": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        },
        "deliveryRules": {
            "type": "array",
            "minItems": 1
        },
        "additionalDetails": {
            "type": "object"
        }
    },
    "required": ["hierarchyType", "tenantId", "campaignName", "action", "startDate", "endDate", "projectType", "deliveryRules", "additionalDetails"]
};
