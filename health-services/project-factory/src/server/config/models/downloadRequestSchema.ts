export const downloadRequestSchema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "tenantId": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
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
                "userWithBoundary",
                "boundaryManagement",
                "boundaryGeometryManagement",
                "userCredential"
            ]
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
        "campaignId": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        }
    },
    "required": ["tenantId", "type", "hierarchyType"],
    "additionalProperties": false
}  