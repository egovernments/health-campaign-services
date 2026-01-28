export const createRequestSchema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "type": {
            "type": "string",
            "enum": ["boundary", "facility", "user", "boundaryWithTarget", "boundaryManagement", "boundaryGeometryManagement"]
        },
        "tenantId": {
            "type": "string",
            "minLength": 1,
            "maxLength": 128
        },
        "fileStoreId": {
            "type": "string",
            "minLength": 1,
            "maxLength": 128
        },
        "action": {
            "type": "string",
            "enum": ["create", "validate"]
        },
        "hierarchyType": {
            "type": "string",
            "minLength": 1,
            "maxLength": 128
        },
        "campaignId": {
            "type": "string",
            "minLength": 1,
            "maxLength": 128
        },
        "additionalDetails": {
            "type": "object"
        },
        "isActive":{
            "type":"boolean"
        }
    },
    "required": ["type", "tenantId", "fileStoreId", "action", "hierarchyType"],
    "additionalProperties": false
}