import Ajv from "ajv";
import * as express from "express";
import { throwError } from "../utils/genericUtils";
import { BoundaryModels } from "../models";
import { logger } from "../utils/logger";
import { searchBoundaryRelationshipDefinition } from "../api/coreApis";
import { generateRequestSchema } from "../config/models/generateRequestSchema";

/**
 * Validates a given object against a provided schema.
 * The object must conform to the schema, otherwise an error is thrown.
 * @param schema The schema to validate against.
 * @param objectData The object to be validated.
 * @throws {Error} If the object does not conform to the schema.
 */
function validateBodyViaSchema(schema: any, objectData: any) {
    const properties: any = { jsonPointers: true, allowUnknownAttributes: true, strict: false }
    const ajv = new Ajv(properties);
    const validate = ajv.compile(schema);
    const isValid = validate(objectData);
    if (!isValid) {
        const formattedError = validate?.errors?.map((error: any) => {
            let formattedErrorMessage = "";
            if (error?.dataPath) {
                // Replace slash with dot and remove leading dot if present
                const dataPath = error.dataPath.replace(/\//g, '.').replace(/^\./, '');
                formattedErrorMessage = `${dataPath} ${error.message}`;
            }
            else if (error?.instancePath) {
                // Replace slash with dot and remove leading dot if present
                const dataPath = error.instancePath.replace(/\//g, '.').replace(/^\./, '');
                formattedErrorMessage = `${dataPath} ${error.message}`;
            }
            else {
                formattedErrorMessage = `${error.message}`
            }
            if (error.keyword === 'enum' && error.params && error.params.allowedValues) {
                formattedErrorMessage += `. Allowed values are: ${error.params.allowedValues.join(', ')}`;
            }
            if (error.keyword === 'additionalProperties' && error.params && error.params.additionalProperty) {
                formattedErrorMessage += `, Additional property '${error.params.additionalProperty}' found.`;
            }
            // Capitalize the first letter of the error message
            formattedErrorMessage = formattedErrorMessage.charAt(0).toUpperCase() + formattedErrorMessage.slice(1);
            return formattedErrorMessage;
        }).join("; ");
        console.error(formattedError);
        throwError("COMMON", 400, "VALIDATION_ERROR", formattedError);
    }
}

// Function to validate the hierarchy type
async function validateHierarchyType(request: any, hierarchyType: any, tenantId: any) {

    const BoundaryTypeHierarchySearchCriteria: BoundaryModels.BoundaryHierarchyDefinitionSearchCriteria={
        BoundaryTypeHierarchySearchCriteria:{
            tenantId,
            hierarchyType
        }
    }; 
    const response:BoundaryModels.BoundaryHierarchyDefinitionResponse  =await searchBoundaryRelationshipDefinition(BoundaryTypeHierarchySearchCriteria);

    if (response?.BoundaryHierarchy && Array.isArray(response?.BoundaryHierarchy) && response?.BoundaryHierarchy?.length > 0) {
        logger.info(`hierarchyType : ${hierarchyType} :: got validated`);
        request.body.hierarchyType = response?.BoundaryHierarchy?.[0];        
    }
    else {
        throwError(`CAMPAIGN`, 400, "VALIDATION_ERROR", `hierarchyType ${hierarchyType} not found`);
    }
}

// Function to validate the generation request
async function validateGenerateRequest(request: express.Request) {
    const { tenantId, hierarchyType, forceUpdate } = request.query;
    validateBodyViaSchema(generateRequestSchema, request.query);
    if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId in userInfo and query should be the same");
    }
    if (!forceUpdate) {
        request.query.forceUpdate = "false";
    }
    await validateHierarchyType(request, hierarchyType, tenantId);
}

export { validateBodyViaSchema ,validateHierarchyType,validateGenerateRequest};
