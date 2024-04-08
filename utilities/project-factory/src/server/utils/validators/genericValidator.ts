import * as express from "express";
import { logger } from "../logger";
import Ajv from "ajv";
import config from "../../config/index";
import { httpRequest } from "../request";
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

async function getTransformAndParsingTemplates(APIResource: any, request: any, response: any) {
    if (!APIResource.mdms || Object.keys(APIResource.mdms).length === 0) {
        const errorMessage = "Invalid APIResourceType Type";
        logger.error(errorMessage);
        throw new Error(errorMessage);
    }

    const transformTemplate = APIResource?.mdms?.[0]?.data?.transformTemplateName;
    const parsingTemplate = APIResource?.mdms?.[0]?.data?.parsingTemplateName;

    return { transformTemplate, parsingTemplate };
}


function validateBoundaries(requestBody: any) {
    const { boundaryCode } = requestBody?.Campaign;
    if (!boundaryCode) {
        throw new Error("Enter BoundaryCode In Campaign")
    }
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        const { boundaryCode: campaignBoundaryCode, parentBoundaryCode } = campaignDetails;
        if (!parentBoundaryCode && boundaryCode != campaignBoundaryCode) {
            throw new Error("Enter ParentBoundaryCode In CampaignDetails")
        }
        if (!campaignBoundaryCode) {
            throw new Error("Enter BoundaryCode In CampaignDetails")
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
        throw new Error("Invalid resourceId for resource type staff with id " + resourceId);
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
        throw new Error("Invalid resourceId for resource type resource with id " + resourceId);
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
        throw new Error("Invalid resourceId for resource type facility with id " + resourceId);
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
        throw new Error("Invalid resource type " + type)
    }
}
async function validateProjectResource(requestBody: any) {
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        for (const resource of campaignDetails?.resources) {
            const type = resource?.type;
            for (const resourceId of resource?.resourceIds) {
                if (!type) {
                    throw new Error("Enter Type In Resources")
                }
                if (!resourceId) {
                    throw new Error("Enter ResourceId In Resources")
                }
                await validateResourceId(type, resourceId, requestBody)
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
            throw new Error("Start date or end date is not a valid epoch timestamp");
        }
    }
    await validateProjectResource(requestBody)
}
async function validateCampaignRequest(requestBody: any) {
    if (requestBody?.Campaign) {
        if (!requestBody?.Campaign?.tenantId) {
            throw new Error("Enter TenantId")
        }
        validateBoundaries(requestBody)
        const { projectType } = requestBody?.Campaign
        if (!projectType) {
            throw new Error("Enter ProjectType")
        }
        await validateCampaign(requestBody)
    }
    else {
        throw new Error("Campaign is required")
    }

}


function validatedProjectResponseAndUpdateId(projectResponse: any, projectBody: any, campaignDetails: any) {
    if (projectBody?.Projects?.length != projectResponse?.Project?.length) {
        throw new Error("Project creation failed. Check Logs")
    }
    else {
        for (const project of projectResponse?.Project) {
            if (!project?.id) {
                throw new Error("Project creation failed. Check Logs")
            }
            else {
                campaignDetails.projectId = project.id;
            }
        }
    }
}
function validateStaffResponse(staffResponse: any) {
    if (!staffResponse?.ProjectStaff?.id) {
        throw new Error("Project staff creation failed. Check Logs")
    }
}
function validateProjectResourceResponse(projectResouceResponse: any) {
    if (!projectResouceResponse?.ProjectResource?.id) {
        throw new Error("Project Resource creation failed. Check Logs")
    }
}
function validateProjectFacilityResponse(projectFacilityResponse: any) {
    if (!projectFacilityResponse?.ProjectFacility?.id) {
        throw new Error("Project Facility creation failed. Check Logs")
    }
}

function validateGenerateRequest(request: express.Request) {
    const { tenantId, type } = request.query;
    if (!tenantId) {
        throw new Error("tenantId is required");
    }
    if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
        throw new Error("tenantId in userInfo and query should be same");
    }
    if (!["facility", "user", "boundary", "facilityWithBoundary"].includes(String(type))) {
        throw new Error("Type should be facility, user, boundary or facilityWithBoundary");
    }
}

export {
    validateDataWithSchema,
    processValidationWithSchema,
    getTransformAndParsingTemplates,
    validateCampaignRequest,
    validatedProjectResponseAndUpdateId,
    validateStaffResponse,
    validateProjectFacilityResponse,
    validateProjectResourceResponse,
    validateGenerateRequest
};