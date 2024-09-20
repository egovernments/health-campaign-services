export const searchCriteriaSchema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "id": {
            "type": "array",
            "items": {
                "type": "string"
            }
        },
        "tenantId": {
            "type": "string",
            "minLength": 1
        },
        "type": {
            "type": "string"
        },
        "status": {
            "type": "string"
        },
        "source": {
            "type": "string"
        }
    },
    "required": ["tenantId"],
    "additionalProperties": false
}


