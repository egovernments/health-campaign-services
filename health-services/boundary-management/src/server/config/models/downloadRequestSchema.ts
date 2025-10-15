export const downloadRequestSchema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "tenantId": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        },
        "hierarchyType": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        },
        "id": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        },
        "status": {
            "type": "string",
            "maxLength": 500,
            "minLength": 1
        },
        "referenceId": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        },
        "forceUpdate": {
            "type": "string"
        }
    },
    "required": ["tenantId", "hierarchyType"],
    "additionalProperties": false
}  