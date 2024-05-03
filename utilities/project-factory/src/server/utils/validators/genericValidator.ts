// Importing necessary modules
import * as express from "express";
import { logger } from "../logger";
import Ajv from "ajv";
import config from "../../config/index";
import { httpRequest } from "../request";
import { getBoundaryRelationshipData, throwError } from "../genericUtils";
import { validateFilters } from "./campaignValidators";
import { generateRequestSchema } from "../../config/models/generateRequestSchema";

// Function to validate data against a JSON schema
function validateDataWithSchema(data: any, schema: any): { isValid: boolean; error: Ajv.ErrorObject[] | null | undefined } {
    const ajv = new Ajv();
    const validate = ajv.compile(schema);
    const isValid: any = validate(data);
    if (!isValid) {
        logger.error(JSON.stringify(validate.errors));
    }
    return { isValid, error: validate.errors };
}

function validateBodyViaSchema(schema: any, objectData: any) {
    const properties: any = { jsonPointers: true, allowUnknownAttributes: true }
    const ajv = new Ajv(properties);
    const validate = ajv.compile(schema);
    const isValid = validate(objectData);
    if (!isValid) {
        const formattedError = validate?.errors?.map((error: any) => {
            let formattedErrorMessage = "";
            if (error?.dataPath) {
                formattedErrorMessage = `${error.dataPath}: ${error.message}`;
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
            return formattedErrorMessage;
        }).join("; ");
        console.error(formattedError);
        throwError("COMMON", 400, "VALIDATION_ERROR", formattedError);
    }
}


// Function to validate boundaries in the request body
function validateBoundaries(requestBody: any) {
    const { boundaryCode } = requestBody?.Campaign;
    if (!boundaryCode) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Enter BoundaryCode In Campaign");
    }
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        const { boundaryCode: campaignBoundaryCode, parentBoundaryCode } = campaignDetails;
        if (!parentBoundaryCode && boundaryCode != campaignBoundaryCode) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Enter ParentBoundaryCode In CampaignDetails");
        }
        if (!campaignBoundaryCode) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Enter BoundaryCode In CampaignDetails");
        }
    }
}

// Function to validate the user ID
async function validateUserId(resourceId: any, requestBody: any) {
    // Constructing the search body for user validation
    const userSearchBody = {
        RequestInfo: requestBody?.RequestInfo,
        tenantId: requestBody?.Campaign?.tenantId.split('.')?.[0],
        uuid: [resourceId]
    }

    // Logging user search URL and request body
    logger.info("User search url : " + config.host.userHost + config.paths.userSearch);
    logger.info("userSearchBody : " + JSON.stringify(userSearchBody));

    // Performing the HTTP request to validate the user ID
    const response = await httpRequest(config.host.userHost + config.paths.userSearch, userSearchBody);

    // Handling response errors if user ID is invalid
    if (!response?.user?.[0]?.uuid) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid resourceId for resource type staff with id " + resourceId);
    }
}

// Function to validate the product variant ID
async function validateProductVariantId(resourceId: any, requestBody: any) {
    // Constructing the search body for product variant validation
    const productVariantSearchBody = {
        RequestInfo: requestBody?.RequestInfo,
        ProductVariant: { id: [resourceId] }
    }
    const productVariantSearchParams = {
        limit: 10,
        offset: 0,
        tenantId: requestBody?.Campaign?.tenantId.split('.')?.[0]
    }

    // Logging product variant search URL and request body
    logger.info("ProductVariant search url : " + config.host.productHost + config.paths.productVariantSearch);
    logger.info("productVariantSearchBody : " + JSON.stringify(productVariantSearchBody));
    logger.info("productVariantSearchParams : " + JSON.stringify(productVariantSearchParams));

    // Performing the HTTP request to validate the product variant ID
    const response = await httpRequest(config.host.productHost + config.paths.productVariantSearch, productVariantSearchBody, productVariantSearchParams);

    // Handling response errors if product variant ID is invalid
    if (!response?.ProductVariant?.[0]?.id) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid resourceId for resource type resource with id " + resourceId);
    }
}

// Function to validate the project facility ID
async function validateProjectFacilityId(resourceId: any, requestBody: any) {
    // Constructing the search body for project facility validation
    const facilitySearchBody = {
        RequestInfo: requestBody?.RequestInfo,
        Facility: {
            id: [resourceId]
        }
    }
    const facilitySearchParams = {
        limit: 10,
        offset: 0,
        tenantId: requestBody?.Campaign?.tenantId?.split('.')?.[0]
    }

    // Logging facility search URL and request body
    logger.info("Facility search url : " + config.host.facilityHost + config.paths.facilitySearch);
    logger.info("facilitySearchBody : " + JSON.stringify(facilitySearchBody));
    logger.info("facilitySearchParams : " + JSON.stringify(facilitySearchParams));

    // Performing the HTTP request to validate the project facility ID
    const response = await httpRequest(config.host.facilityHost + config.paths.facilitySearch, facilitySearchBody, facilitySearchParams);

    // Handling response errors if project facility ID is invalid
    if (!response?.Facilities?.[0]?.id) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid resourceId for resource type facility with id " + resourceId);
    }
}

// Function to validate the resource ID based on its type
async function validateResourceId(type: any, resourceId: any, requestBody: any) {
    // Dispatching validation based on resource type
    if (type == "staff") {
        await validateUserId(resourceId, requestBody)
    }
    else if (type == "resource") {
        await validateProductVariantId(resourceId, requestBody)
    }
    else if (type == "facility") {
        await validateProjectFacilityId(resourceId, requestBody)
    }
    else {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid resource type " + type);
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
                    throwError("COMMON", 400, "VALIDATION_ERROR", "Enter ResourceId In Resources");
                }
                // Validate the resource ID based on its type
                await validateResourceId(type, resourceId, requestBody);
            }
        }
    }
}

// Function to validate the campaign details including resource validation
async function validateCampaign(requestBody: any) {
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        var { startDate, endDate } = campaignDetails;
        startDate = parseInt(startDate);
        endDate = parseInt(endDate);

        // Check if startDate and endDate are valid integers
        if (isNaN(startDate) || isNaN(endDate)) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Start date or end date is not a valid epoch timestamp");
        }
    }
    await validateProjectResource(requestBody)
}

// Function to validate the entire campaign request
async function validateCampaignRequest(requestBody: any) {
    if (requestBody?.Campaign) {
        if (!requestBody?.Campaign?.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Enter TenantId");
        }
        validateBoundaries(requestBody);
        const { projectType } = requestBody?.Campaign;
        if (!projectType) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "Enter ProjectType");
        }
        await validateCampaign(requestBody);
    }
    else {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Campaign is required");
    }
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
function validateStaffResponse(staffResponse: any) {
    if (!staffResponse?.ProjectStaff?.id) {
        throwError("CAMPAIGN", 500, "RESOURCE_CREATION_ERROR", "Project staff creation failed.");
    }
}

// Function to validate project resource response
function validateProjectResourceResponse(projectResouceResponse: any) {
    if (!projectResouceResponse?.ProjectResource?.id) {
        throwError("CAMPAIGN", 500, "RESOURCE_CREATION_ERROR", "Project Resource creation failed.");
    }
}

// Function to validate project facility response
function validateProjectFacilityResponse(projectFacilityResponse: any) {
    if (!projectFacilityResponse?.ProjectFacility?.id) {
        throwError("CAMPAIGN", 500, "RESOURCE_CREATION_ERROR", "Project Facility creation failed.");
    }
}

// Function to validate the hierarchy type
async function validateHierarchyType(request: any, hierarchyType: any, tenantId: any) {
    const searchBody = {
        RequestInfo: request?.body?.RequestInfo,
        BoundaryTypeHierarchySearchCriteria: {
            "tenantId": tenantId,
            "limit": 5,
            "offset": 0,
            "hierarchyType": hierarchyType
        }
    }
    logger.info("Hierarchy Search Url : " + config.host.boundaryHost + config.paths.boundaryHierarchy);
    logger.info("SearchBody : " + JSON.stringify(searchBody))
    const response = await httpRequest(config.host.boundaryHost + config.paths.boundaryHierarchy, searchBody);
    if (response?.BoundaryHierarchy && Array.isArray(response?.BoundaryHierarchy) && response?.BoundaryHierarchy?.length > 0) {
        logger.info("Hierarchy Search Response : " + JSON.stringify(response?.BoundaryHierarchy))
    }
    else {
        throwError(`CAMPAIGN`, 400, "VALIDATION_ERROR", `hierarchyType ${hierarchyType} not found`);
    }
}

// Function to validate the generation request
async function validateGenerateRequest(request: express.Request) {
    const { tenantId, type, hierarchyType, forceUpdate } = request.query;
    validateBodyViaSchema(generateRequestSchema, request.query);
    if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId in userInfo and query should be the same");
    }
    if (!forceUpdate) {
        request.query.forceUpdate = "false";
    }
    await validateHierarchyType(request, hierarchyType, tenantId);
    if (type == 'boundary') {
        await validateFiltersInRequestBody(request);
    }
}

async function validateFiltersInRequestBody(request: any) {
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
    validateStaffResponse,
    validateProjectFacilityResponse,
    validateProjectResourceResponse,
    validateGenerateRequest,
    validateHierarchyType
};