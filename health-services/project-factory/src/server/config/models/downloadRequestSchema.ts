export const downloadRequestSchema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "tenantId": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1,
        },
        "type": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1,
            "enum": [
                "facility",
                "user",
                "boundary",
                "facilityWithBoundary",
                "userWithBoundary"
            ]
        },
        "hierarchyType": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1,
        },
        "id": {
            "type": "string",
            "maxlength": 128,
            "minLength": 1,
        }
    },
    "required": ["tenantId", "type", "hierarchyType"],
    "additionalProperties": false
}