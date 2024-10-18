export const generateRequestSchema = {
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
        "boundary",
        "boundaryManagement",
        "facilityWithBoundary",
        "userWithBoundary",
        "boundaryGeometryManagement"
      ]
    },
    "hierarchyType": {
      "type": "string",
      "maxLength": 128,
      "minLength": 1,
    },
    "forceUpdate": {
      "type": "string",
      "enum": ["true", "false"]
    },
    "campaignId": {
      "type": "string"
    },
    "source": {
      "type": "string",
    }
  },
  "required": ["tenantId", "type", "hierarchyType", "campaignId"],
  "additionalProperties": false
}
