import * as express from "express";
import { logger } from "../utils/logger";
import Ajv from "ajv";
import { throwError } from "../utils/genericUtils";
import { generateRequestSchema } from "../config/models/generateRequestSchema";
import { campaignStatuses } from "../config/constants";
import { validateMappingId } from "../utils/campaignMappingUtils";
import { searchBoundaryRelationshipDefinition } from "../api/coreApis";
import { BoundaryModels } from "../models";

/** AJV-validates a campaign body against schema and throws a formatted VALIDATION_ERROR on failure. */
function validateCampaignBodyViaSchema(schema: any, objectData: any) {
    const ajv = new Ajv({ strict: false });
    const validate = ajv.compile(schema);
    const isValid = validate(objectData);
    if (!isValid) {
        const formattedError = validate?.errors?.map((error: any) => {
            let formattedErrorMessage = "";
            if (error?.dataPath) {
                const dataPath = error.dataPath.replace(/\//g, '.').replace(/^\./, '');
                formattedErrorMessage = `${dataPath} ${error.message}`;
            }
            else if (error?.instancePath) {
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
            formattedErrorMessage = formattedErrorMessage.charAt(0).toUpperCase() + formattedErrorMessage.slice(1);
            return formattedErrorMessage;
        }).join("; ");
        console.error(formattedError);
        throwError("COMMON", 400, "VALIDATION_ERROR", formattedError);
    }
}

/** AJV-validates objectData against schema (lenient mode) and throws a formatted VALIDATION_ERROR on failure. */
function validateBodyViaSchema(schema: any, objectData: any) {
    const properties: any = { jsonPointers: true, allowUnknownAttributes: true, strict: false }
    const ajv = new Ajv(properties);
    const validate = ajv.compile(schema);
    const isValid = validate(objectData);
    if (!isValid) {
        const formattedError = validate?.errors?.map((error: any) => {
            let formattedErrorMessage = "";
            if (error?.dataPath) {
                const dataPath = error.dataPath.replace(/\//g, '.').replace(/^\./, '');
                formattedErrorMessage = `${dataPath} ${error.message}`;
            }
            else if (error?.instancePath) {
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
            formattedErrorMessage = formattedErrorMessage.charAt(0).toUpperCase() + formattedErrorMessage.slice(1);
            return formattedErrorMessage;
        }).join("; ");
        console.error(formattedError);
        throwError("COMMON", 400, "VALIDATION_ERROR", formattedError);
    }
}

async function validateProjectResource(requestBody: any) {
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        for (const resource of campaignDetails?.resources) {
            const type = resource?.type;
            for (const resourceId of resource?.resourceIds) {
                if (!type) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", "Enter Type In Resources");
                }
                if (!resourceId) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", "Enter ResourceId In Resources of type " + type);
                }
            }
        }
    }
}

async function validateCampaign(requestBody: any) {
    const id = requestBody?.Campaign?.id
    if (!id) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Enter id of campaign for mapping");
    }
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        var { startDate, endDate } = campaignDetails;
        startDate = parseInt(startDate);
        endDate = parseInt(endDate);
    }
    await validateProjectResource(requestBody)
}

/** Validates a campaign mapping request and rejects it if the campaign is already in-progress/mapped. */
async function validateCampaignRequest(requestBody: any) {
    try {
        if (requestBody?.Campaign) {
            if (!requestBody?.Campaign?.tenantId) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "Enter TenantId");
            }
            await validateCampaign(requestBody);
            const id = requestBody?.Campaign?.id;
            const campaignDetails = await validateMappingId(requestBody, id);
            if (campaignDetails?.status == campaignStatuses.inprogress) {
                logger.error("Campaign Already In Progress and Mapped");
                throwError("CAMPAIGN", 400, "CAMPAIGN_ALREADY_MAPPED");
            }
        }
        else {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Campaign object is missing");
        }
        if (requestBody?.CampaignDetails) {
            if (!requestBody?.CampaignDetails?.tenantId) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "Enter TenantId");
            }
            if (!requestBody?.CampaignDetails?.id) {
                throwError("COMMON", 400, "VALIDATION_ERROR", "Enter id in CampaignDetails");
            }
        }
        else {
            throwError("COMMON", 400, "VALIDATION_ERROR", "CampaignDetails is missing");
        }
    } catch (error: any) {
        console.log(error)
        throw new Error(error)
    }
}

/** Confirms the hierarchyType exists for the tenant and replaces request.body.hierarchyType with the resolved definition. */
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

/** Validates template-generation query params, enforces tenant match with userInfo, and defaults forceUpdate. */
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

export {
    validateBodyViaSchema,
    validateCampaignRequest,
    validateGenerateRequest,
    validateHierarchyType,
    validateCampaignBodyViaSchema
};
