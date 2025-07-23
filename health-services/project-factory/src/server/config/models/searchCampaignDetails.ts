export const searchCampaignDetailsSchema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "ids": {
            "type": "array",
            "minItems": 1,
            "items": {
                "type": "string"
            }
        },
        "isActive":{
            "type":"boolean"
        },
        "tenantId": {
            "type": "string",
            "minLength": 1
        },
        "startDate": {
            "type": "integer",
            "minimum": 0
        },
        "endDate": {
            "type": "integer",
            "minimum": 0
        },
        "campaignsIncludeDates": {
            "type": "boolean"
        },
        "isLikeSearch": {
            "type": "boolean"
        },
        "isOverrideDatesFromProject": {
            "type": "boolean"
        },
        "projectType": {
            "type": "string"
        },
        "campaignName": {
            "type": "string",
            "minLength": 1
        },
        "status": {
            "oneOf": [
                { "type": "string" },
                {
                    "type": "array",
                    "items": { "type": "string" },
                    "minItems": 1
                }
            ]
        },
        "createdBy": {
            "type": "string"
        },
        "campaignNumber": {
            "type": "string"
        },
        "pagination": {
            "type": "object",
            "properties": {
                "sortBy": {
                    "type": "string"
                },
                "sortOrder": {
                    "type": "string",
                    "enum": ["asc", "desc"]
                },
                "limit": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 100
                },
                "offset": {
                    "type": "integer",
                    "minimum": 0
                }
            }
        }
    },
    "required": ["tenantId"],
    "additionalProperties": false
}
