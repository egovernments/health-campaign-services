// Importing necessary modules
import * as express from "express";
import { logger } from "../utils/logger";
import Ajv from "ajv";
import { getBoundaryRelationshipData, throwError } from "../utils/genericUtils";
import { validateFilters } from "./campaignValidators";
import { generateRequestSchema } from "../config/models/generateRequestSchema";
import { persistTrack } from "../utils/processTrackUtils";
import { processTrackTypes, processTrackStatuses, campaignStatuses } from "../config/constants";
import { validateMappingId } from "../utils/campaignMappingUtils";
import { searchBoundaryRelationshipDefinition } from "../api/coreApis";
import { BoundaryModels } from "../models";

// Function to validate data against a JSON schema
function validateDataWithSchema(data: any, schema: any): { isValid: boolean; error: any | null | undefined } {
    const ajv = new Ajv({ strict: false });
    const validate = ajv.compile(schema);
    const isValid: any = validate(data);
    if (!isValid) {
        logger.error(JSON.stringify(validate.errors));
    }
    return { isValid, error: validate.errors };
}
function validateCampaignBodyViaSchema(schema: any, objectData: any) {
    const ajv = new Ajv({ strict: false });
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

// Function to validate the resources associated with a campaign
async function validateProjectResource(requestBody: any) {
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        for (const resource of campaignDetails?.resources) {
            const type = resource?.type;
            for (const resourceId of resource?.resourceIds) {
                // Check if resource type and ID are provided
                if (!type) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", "Enter Type In Resources");
                }
                if (!resourceId) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", "Enter ResourceId In Resources of type " + type);
                }
                // Validate the resource ID based on its type
                // await validateResourceId(type, resourceId, requestBody);
            }
        }
    }
}

// Function to validate the campaign details including resource validation
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

// Function to validate the entire campaign request
async function validateCampaignRequest(requestBody: any) {
    await persistTrack(requestBody?.Campaign?.id, processTrackTypes.validateMappingResource, processTrackStatuses.inprogress);
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
        await persistTrack(requestBody?.Campaign?.id, processTrackTypes.validateMappingResource, processTrackStatuses.failed, { error: String((error?.message + (error?.description ? ` : ${error?.description}` : '')) || error) });
        throw new Error(error)
    }
    await persistTrack(requestBody?.Campaign?.id, processTrackTypes.validateMappingResource, processTrackStatuses.completed);
}

// Function to validate and update project response and its ID
function validatedProjectResponseAndUpdateId(projectResponse: any, projectBody: any, campaignDetails: any) {
    if (projectBody?.Projects?.length != projectResponse?.Project?.length) {
        throwError("PROJECT", 500, "PROJECT_CREATION_ERROR");
    } else {
        for (const project of projectResponse?.Project) {
            if (!project?.id) {
                throwError("PROJECT", 500, "PROJECT_CREATION_ERROR");
            } else {
                campaignDetails.projectId = project.id;
            }
        }
    }
}

// Function to validate project staff response
// function validateStaffResponse(staffResponse: any) {
//     if (!staffResponse?.ProjectStaff?.id) {
//         throwError("CAMPAIGN", 500, "RESOURCE_CREATION_ERROR", "Project staff creation failed.");
//     }
// }

// Function to validate project resource response
// function validateProjectResourceResponse(projectResouceResponse: any) {
//     if (!projectResouceResponse?.ProjectResource?.id) {
//         throwError("CAMPAIGN", 500, "RESOURCE_CREATION_ERROR", "Project Resource creation failed.");
//     }
// }

// Function to validate project facility response
// function validateProjectFacilityResponse(projectFacilityResponse: any) {
//     if (!projectFacilityResponse?.ProjectFacility?.id) {
//         throwError("CAMPAIGN", 500, "RESOURCE_CREATION_ERROR", "Project Facility creation failed.");
//     }
// }

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

export async function validateFiltersInRequestBody(request: any) {
    if (request?.body?.Filters === undefined) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "For type boundary Filters Object should be present in request body")
    }
    const params = {
        ...request?.query,
        includeChildren: true
    };
    const boundaryData = await getBoundaryRelationshipData(request, params);
    if (boundaryData && request?.body?.Filters != null) {
        await validateFilters(request, boundaryData);
    }
}

export {
    validateDataWithSchema,
    validateBodyViaSchema,
    validateCampaignRequest,
    validatedProjectResponseAndUpdateId,
    // validateStaffResponse,
    // validateProjectFacilityResponse,
    // validateProjectResourceResponse,
    validateGenerateRequest,
    validateHierarchyType,
    validateCampaignBodyViaSchema
};
