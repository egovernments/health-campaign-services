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

function processValidationWithSchema(processResult: any, validationErrors: any, validatedData: any, schemaDef: any) {
    if (schemaDef) {
        processResult.updatedDatas.forEach((data: any) => {
            const validationResult = validateDataWithSchema(data, schemaDef);
            if (!validationResult.isValid) {
                validationErrors.push({ data, error: validationResult.error });
            }
            else {
                validatedData.push(data)
            }
        });
    }
    else {
        logger.info("Skipping Validation of Data as Schema is not defined");
        validationErrors.push("NO_VALIDATION_SCHEMA_FOUND");
        processResult.updatedDatas.forEach((data: any) => {
            validatedData.push(data)
        });
    }
}

function validateBoundaries(requestBody: any) {
    const { boundaryCode } = requestBody?.Campaign;
    if (!boundaryCode) {
        throwError("Enter BoundaryCode In Campaign", 400, "VALIDATION_ERROR");
    }
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        const { boundaryCode: campaignBoundaryCode, parentBoundaryCode } = campaignDetails;
        if (!parentBoundaryCode && boundaryCode != campaignBoundaryCode) {
            throwError("Enter ParentBoundaryCode In CampaignDetails", 400, "VALIDATION_ERROR");
        }
        if (!campaignBoundaryCode) {
            throwError("Enter BoundaryCode In CampaignDetails", 400, "VALIDATION_ERROR");
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
        throwError("Invalid resourceId for resource type staff with id " + resourceId, 400, "VALIDATION_ERROR");
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
        throwError("Invalid resourceId for resource type resource with id " + resourceId, 400, "VALIDATION_ERROR");
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
        throwError("Invalid resourceId for resource type facility with id " + resourceId, 400, "VALIDATION_ERROR");
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
        throwError("Invalid resource type " + type, 400, "VALIDATION_ERROR");
    }
}
async function validateProjectResource(requestBody: any) {
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        for (const resource of campaignDetails?.resources) {
            const type = resource?.type;
            for (const resourceId of resource?.resourceIds) {
                if (!type) {
                    throwError("Enter Type In Resources", 400, "VALIDATION_ERROR");
                }
                if (!resourceId) {
                    throwError("Enter ResourceId In Resources", 400, "VALIDATION_ERROR");
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
            throwError("Start date or end date is not a valid epoch timestamp", 400, "VALIDATION_ERROR");
        }
    }
    await validateProjectResource(requestBody)
}
async function validateCampaignRequest(requestBody: any) {
    if (requestBody?.Campaign) {
        if (!requestBody?.Campaign?.tenantId) {
            throwError("Enter TenantId", 400, "VALIDATION_ERROR");
        }
        validateBoundaries(requestBody);
        const { projectType } = requestBody?.Campaign;
        if (!projectType) {
            throwError("Enter ProjectType", 400, "VALIDATION_ERROR");
        }
        await validateCampaign(requestBody);
    }
    else {
        throwError("Campaign is required", 400, "VALIDATION_ERROR");
    }
}


function validatedProjectResponseAndUpdateId(projectResponse: any, projectBody: any, campaignDetails: any) {
    if (projectBody?.Projects?.length != projectResponse?.Project?.length) {
        throwError("Project creation failed. Check Logs", 500, "PROJECT_CREATION_ERROR");
    } else {
        for (const project of projectResponse?.Project) {
            if (!project?.id) {
                throwError("Project creation failed. Check Logs", 500, "PROJECT_CREATION_ERROR");
            } else {
                campaignDetails.projectId = project.id;
            }
        }
    }
}


function validateStaffResponse(staffResponse: any) {
    if (!staffResponse?.ProjectStaff?.id) {
        throwError("Project staff creation failed. Check Logs", 500, "RESOURCE_CREATION_ERROR");
    }
}

function validateProjectResourceResponse(projectResouceResponse: any) {
    if (!projectResouceResponse?.ProjectResource?.id) {
        throwError("Project Resource creation failed. Check Logs", 500, "RESOURCE_CREATION_ERROR");
    }
}

function validateProjectFacilityResponse(projectFacilityResponse: any) {
    if (!projectFacilityResponse?.ProjectFacility?.id) {
        throwError("Project Facility creation failed. Check Logs", 500, "RESOURCE_CREATION_ERROR");
    }
}
async function validateHierarchyType(request: any, hierarchyType: any) {
    const searchBody = {
        RequestInfo: request?.body?.RequestInfo,
        BoundaryTypeHierarchySearchCriteria: {
            "tenantId": request?.query?.tenantId,
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
        throwError(`hierarchyType ${hierarchyType} not found`, 400, "HIERARCHYTYPE_VALIDATION_ERROR");
    }
}
async function validateGenerateRequest(request: express.Request) {
    const { tenantId, type, hierarchyType, forceUpdate } = request.query;
    if (!tenantId) {
        throwError("tenantId is required", 400, "VALIDATION_ERROR");
    }
    if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
        throwError("tenantId in userInfo and query should be the same", 400, "VALIDATION_ERROR");
    }
    if (!type) {
        throwError("type is required", 400, "VALIDATION_ERROR");
    }
    if (!hierarchyType) {
        throwError("hierarchyType is required", 400, "VALIDATION_ERROR");
    }
    if (forceUpdate) {
        if (forceUpdate !== 'true' && forceUpdate !== 'false') {
            throwError("forceUpdate should be either 'true' or 'false'", 400, "VALIDATION_ERROR");
        }
    }
    else {
        throwError("forceUpdate is required", 400, "VALIDATION_ERROR");
    }
    if (!["facility", "user", "boundary", "facilityWithBoundary"].includes(String(type))) {
        throwError("Type should be facility, user, boundary, or facilityWithBoundary", 400, "VALIDATION_ERROR");
    }
    await validateHierarchyType(request, hierarchyType);
}


export {
    validateDataWithSchema,
    processValidationWithSchema,
    validateCampaignRequest,
    validatedProjectResponseAndUpdateId,
    validateStaffResponse,
    validateProjectFacilityResponse,
    validateProjectResourceResponse,
    validateGenerateRequest
};