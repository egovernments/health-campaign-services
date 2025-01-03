export const schemaConfig = {
  Boundary: {
    $schema: "http://json-schema.org/draft-07/schema#",
    title: "BoundaryTemplateSchema",
    type: "object",
    properties: {
      "Boundary Code": {
        type: "string",
        minLength: 1,
      },
      "Target at the Selected Boundary level": {
        type: "integer",
        minimum: 1,
        maximum: 100000000,
      },
    },
    required: ["Boundary Code"],
  },
  facilityWithBoundary: {
    $schema: "http://json-schema.org/draft-07/schema#",
    title: "FacilityTemplateSchema",
    type: "object",
    properties: {
      "Facility Name": {
        type: "string",
        maxLength: 2000,
        minLength: 1,
      },
      "Facility Type": {
        type: "string",
        enum: ["Warehouse", "Health Facility"],
      },
      "Facility Status": {
        type: "string",
        enum: ["Temporary", "Permanent"],
      },
      Capacity: {
        type: "integer",
        minimum: 1,
        maximum: 100000000,
        multipleOf: 1,
      },
      "Boundary Code": {
        type: "string",
        minLength: 1,
      },
    },
    required: ["Facility Name", "Facility Type", "Facility Status", "Capacity", "Boundary Code"],
  },
  User: {
    $schema: "http://json-schema.org/draft-07/schema#",
    title: "UserTemplateSchema",
    type: "object",
    properties: {
      "Name of the Person (Mandatory)": {
        type: "string",
        maxLength: 128,
        minLength: 1,
      },
      "Phone Number": {
        type: "integer",
        minimum: 100000000,
        maximum: 9999999999,
      },
      "Role (Mandatory)": {
        type: "string",
      },
      "Employment Type (Mandatory)": {
        type: "string",
        enum: ["Temporary", "Permanent"],
      },
      "Capacity": {
        "type": "number",
        "minimum": 1,
        "maximum": 100000000
      }
    },
    required: ["Name of the Person (Mandatory)", "Phone Number (Mandatory)", "Role (Mandatory)", "Employment Type (Mandatory)"],
  },
};
