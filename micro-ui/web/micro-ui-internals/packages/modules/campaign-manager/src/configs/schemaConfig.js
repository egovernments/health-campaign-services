export const schemaConfig = 
  {
    facilityWithBoundary: {
      "$schema": "http://json-schema.org/draft-07/schema#",
      "title": "FacilityTemplateSchema",
      "type": "object",
      "properties": {
          "Facility Name": {
              "type": "string",
              "maxLength": 2000,
              "minLength": 1
          },
          "Facility Type": {
              "type": "string",
              "enum": ["Warehouse", "Health Facility"]
          },
          "Facility Status": {
              "type": "string",
              "enum": ["Temporary", "Permanent"]
          },
          "Capacity": {
              "type": "integer",
              "minimum": 0,
              "maximum": 9223372036854775807,
              "multipleOf": 1
          }
      },
      "required": [
          "Facility Name",
          "Facility Type",
          "Facility Status",
          "Capacity"
      ]
  }
  };

