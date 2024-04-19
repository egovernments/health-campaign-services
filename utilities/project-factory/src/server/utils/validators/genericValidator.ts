import * as express from "express";
import { logger } from "../logger";
import Ajv from "ajv";
import config from "../../config/index";
import { httpRequest } from "../request";
import { throwError } from "../genericUtils";
// import RequestCampaignDetails from "../config/interfaces/requestCampaignDetails.interface";


function validateDataWithSchema(data: any, schema: any): { isValid: boolean; error: Ajv.ErrorObject[] | null | undefined } {
    const ajv = new Ajv();
    const validate = ajv.compile(schema);
    const isValid: any = validate(data);
    if (!isValid) {
        logger.error(JSON.stringify(validate.errors));
    }
    return { isValid, error: validate.errors };
}

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
async function validateUserId(resourceId: any, requestBody: any) {
    const userSearchBody = {
        RequestInfo: requestBody?.RequestInfo,
        tenantId: requestBody?.Campaign?.tenantId.split('.')?.[0],
        uuid: [resourceId]
    }
    logger.info("User search url : " + config.host.userHost + config.paths.userSearch);
    logger.info("userSearchBody : " + JSON.stringify(userSearchBody));
    const response = await httpRequest(config.host.userHost + config.paths.userSearch, userSearchBody);
    if (!response?.user?.[0]?.uuid) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid resourceId for resource type staff with id " + resourceId);
    }
}
async function validateProductVariantId(resourceId: any, requestBody: any) {
    const productVariantSearchBody = {
        RequestInfo: requestBody?.RequestInfo,
        ProductVariant: { id: [resourceId] }
    }
    const productVariantSearchParams = {
        limit: 10,
        offset: 0,
        tenantId: requestBody?.Campaign?.tenantId.split('.')?.[0]
    }
    logger.info("ProductVariant search url : " + config.host.productHost + config.paths.productVariantSearch);
    logger.info("productVariantSearchBody : " + JSON.stringify(productVariantSearchBody));
    logger.info("productVariantSearchParams : " + JSON.stringify(productVariantSearchParams));
    const response = await httpRequest(config.host.productHost + config.paths.productVariantSearch, productVariantSearchBody, productVariantSearchParams);
    if (!response?.ProductVariant?.[0]?.id) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid resourceId for resource type resource with id " + resourceId);
    }
}
async function validateProjectFacilityId(resourceId: any, requestBody: any) {
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
    logger.info("Facility search url : " + config.host.facilityHost + config.paths.facilitySearch);
    logger.info("facilitySearchBody : " + JSON.stringify(facilitySearchBody));
    logger.info("facilitySearchParams : " + JSON.stringify(facilitySearchParams));
    const response = await httpRequest(config.host.facilityHost + config.paths.facilitySearch, facilitySearchBody, facilitySearchParams);
    if (!response?.Facilities?.[0]?.id) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid resourceId for resource type facility with id " + resourceId);
    }
}
async function validateResourceId(type: any, resourceId: any, requestBody: any) {
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
async function validateProjectResource(requestBody: any) {
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        for (const resource of campaignDetails?.resources) {
            const type = resource?.type;
            for (const resourceId of resource?.resourceIds) {
                if (!type) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", "Enter Type In Resources");
                }
                if (!resourceId) {
                    throwError("COMMON", 400, "VALIDATION_ERROR", "Enter ResourceId In Resources");
                }
                await validateResourceId(type, resourceId, requestBody);
            }
        }
    }
}


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


function validateStaffResponse(staffResponse: any) {
    if (!staffResponse?.ProjectStaff?.id) {
        throwError("CAMPAIGN", 500, "RESOURCE_CREATION_ERROR", "Project staff creation failed.");
    }
}

function validateProjectResourceResponse(projectResouceResponse: any) {
    if (!projectResouceResponse?.ProjectResource?.id) {
        throwError("CAMPAIGN", 500, "RESOURCE_CREATION_ERROR", "Project Resource creation failed.");
    }
}

function validateProjectFacilityResponse(projectFacilityResponse: any) {
    if (!projectFacilityResponse?.ProjectFacility?.id) {
        throwError("CAMPAIGN", 500, "RESOURCE_CREATION_ERROR", "Project Facility creation failed.");
    }
}
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

async function validateGenerateRequest(request: express.Request) {
    const { tenantId, type, hierarchyType, forceUpdate } = request.query;
    if (!tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is required");
    }
    if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId in userInfo and query should be the same");
    }
    if (!type) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "type is required");
    }
    if (!hierarchyType) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "hierarchyType is required");
    }
    if (forceUpdate) {
        if (forceUpdate !== 'true' && forceUpdate !== 'false') {
            throwError("COMMON", 400, "VALIDATION_ERROR", "forceUpdate should be either 'true' or 'false'");
        }
    }
    else {
        request.query.forceUpdate = "false";
    }
    if (!["facility", "user", "boundary", "facilityWithBoundary"].includes(String(type))) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "Type should be facility, user, boundary, or facilityWithBoundary");
    }
    await validateHierarchyType(request, hierarchyType, tenantId);
}

export {
    validateDataWithSchema,
    validateCampaignRequest,
    validatedProjectResponseAndUpdateId,
    validateStaffResponse,
    validateProjectFacilityResponse,
    validateProjectResourceResponse,
    validateGenerateRequest,
    validateHierarchyType
};