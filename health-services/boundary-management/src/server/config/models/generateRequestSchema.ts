export const generateRequestSchema = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "tenantId": {
      "type": "string",
      "maxLength": 128,
      "minLength": 1,
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
    "source": {
      "type": "string",
    },
    "referenceId": {
      "type": "string",
      "minLength": 1,
      "maxLength": 128,
    },
  },
  "required": ["tenantId", "hierarchyType"],
  "additionalProperties": false
}