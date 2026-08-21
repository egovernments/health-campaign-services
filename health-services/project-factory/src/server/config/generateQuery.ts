export const generateQuerySchema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "hierarchyType": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        },
        "tenantId": {
            "type": "string",
            "maxLength": 64,
            "minLength": 1
        },
        "campaignName": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        },
        "action": {
            "type": "string",
            "enum": ["create", "draft"],
            "maxLength": 64,
            "minLength": 1
        },
        "startDate": {
            "type": "integer"
        },
        "endDate": {
            "type": "integer"
        },
        "boundaries": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "code": {
                        "type": "string",
                        "maxLength": 64,
                        "minLength": 1
                    },
                    "type": {
                        "type": "string",
                        "maxLength": 128,
                        "minLength": 1
                    },
                    "isRoot": {
                        "type": "boolean"
                    },
                    "includeAllChildren": {
                        "type": "boolean"
                    }
                },
                "required": ["code", "type"]
            }
        },
        "resources": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "filestoreId": {
                        "type": "string",
                        "maxLength": 128,
                        "minLength": 1
                    },
                    "type": {
                        "type": "string",
                        "maxLength": 128,
                        "minLength": 1
                    },
                    "filename": {
                        "type": "string",
                        "maxLength": 128,
                        "minLength": 1
                    }
                },
                "required": ["filestoreId", "type"]
            }
        },
        "projectType": {
            "type": "string",
            "maxLength": 128,
            "minLength": 1
        },
        "deliveryRules": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "startDate": {
                        "type": "integer"
                    },
                    "endDate": {
                        "type": "integer"
                    },
                    "cycleNumber": {
                        "type": "integer"
                    },
                    "deliveryNumber": {
                        "type": "integer"
                    },
                    "deliveryRuleNumber": {
                        "type": "integer"
                    },
                    "products": {
                        "type": "array",
                        "items": {
                            "type": "string",
                            "maxLength": 128,
                            "minLength": 1
                        }
                    },
                    "conditions": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "attribute": {
                                    "type": "string",
                                    "maxLength": 128,
                                    "minLength": 1
                                },
                                "operator": {
                                    "type": "string",
                                    "maxLength": 128,
                                    "minLength": 1
                                },
                                "value": {
                                    "type": "integer"
                                }
                            },
                            "required": ["attribute", "operator", "value"]
                        }
                    }
                },
                "required": ["startDate", "endDate", "cycleNumber", "deliveryNumber", "deliveryRuleNumber", "products", "conditions"]
            }
        },
        "additionalDetails": {
            "type": "object"
        }
    },
    "required": ["hierarchyType", "tenantId", "campaignName", "action", "startDate", "endDate", "projectType", "deliveryRules", "additionalDetails"]
};
