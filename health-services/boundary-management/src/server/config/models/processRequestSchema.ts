export const processRequestSchema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
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
        "referenceId": {
            "type": "string",
            "minLength": 1,
            "maxLength": 128,
         },
        "additionalDetails": {
            "type": "object"
        },
        "isActive":{
            "type":"boolean"
        }
    },
    "required": [ "tenantId", "fileStoreId", "action", "hierarchyType"],
    "additionalProperties": false
}