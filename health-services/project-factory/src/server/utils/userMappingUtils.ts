import { defaultRequestInfo } from "../api/coreApis";
import { mappingStatuses } from "../config/constants";
import { getMappingDataRelatedToCampaign, getRelatedDataWithCampaign, throwError } from "./genericUtils";
import { logger } from "./logger";
import { createStaff } from "../api/genericApis";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";
import { httpRequest } from "./request";

export async function startUserMappingAndDemapping(campaignDetails: any, useruuid: string) {
    await startUserMapping(campaignDetails, useruuid );
    await startUserDemapping(campaignDetails, useruuid );
}

export async function startUserMapping(campaignDetails: any, useruuid: string) {
    const allCurrentMappingsToDo = await getMappingDataRelatedToCampaign("user", campaignDetails.campaignNumber, mappingStatuses.toBeMapped);
    if (allCurrentMappingsToDo.length <= 0) {
        return;
    }
    const getProjectsDataRelatedToCampaign = await getRelatedDataWithCampaign("boundary", campaignDetails.campaignNumber);
    const boundaryToProjectIdMapping: any = {};
    for (let i = 0; i < getProjectsDataRelatedToCampaign.length; i++) {
        boundaryToProjectIdMapping[getProjectsDataRelatedToCampaign[i]?.uniqueIdentifier] = getProjectsDataRelatedToCampaign[i]?.uniqueIdAfterProcess;
    }
    const getUsersRelatedToCampaign = await getRelatedDataWithCampaign("user", campaignDetails.campaignNumber);
    const phoneToUserIdMapping: any = {};
    for (let i = 0; i < getUsersRelatedToCampaign.length; i++) {
        phoneToUserIdMapping[getUsersRelatedToCampaign[i]?.uniqueIdentifier] = getUsersRelatedToCampaign[i]?.uniqueIdAfterProcess;
    }
    const startDate = campaignDetails.startDate;
    const endDate = campaignDetails.endDate;
    const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
    RequestInfo.userInfo.uuid = useruuid || campaignDetails?.auditDetails?.createdBy;
    for (let i = 0; i < allCurrentMappingsToDo.length; i++) {
        try {
            const projectId = boundaryToProjectIdMapping[allCurrentMappingsToDo[i]?.boundaryCode];
            const userId = phoneToUserIdMapping[allCurrentMappingsToDo[i]?.uniqueIdentifierForData];
            const ProjectStaff = {
                tenantId: campaignDetails.tenantId,
                projectId,
                userId ,
                startDate,
                endDate,
            };
            const newResourceBody = { RequestInfo, ProjectStaff };
            const projectStaffResponse = await createStaff(newResourceBody);
            allCurrentMappingsToDo[i].status = mappingStatuses.mapped;
            if (projectStaffResponse?.ProjectStaff?.id) {
                allCurrentMappingsToDo[i].mappingId = projectStaffResponse?.ProjectStaff?.id;
            }
            else {
                throw new Error("Failed to create project staff for user with phone " + allCurrentMappingsToDo[i]?.uniqueIdentifierForData);
            }
            await produceModifiedMessages({ datas: [allCurrentMappingsToDo[i]] }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC);
        }
        catch (error) {
            // Log the error if the API call fails
            logger.error(`Failed to create project staff for user with phone ${allCurrentMappingsToDo[i]?.uniqueIdentifierForData}:`, error);
            throw error; // Rethrow the error to propagate it
        }
    }
}

export async function startUserDemapping(campaignDetails: any, useruuid: string) {
    const allCurrentMappingsToDeMap = await getMappingDataRelatedToCampaign("user", campaignDetails.campaignNumber, mappingStatuses.toBeDeMapped);
    if (allCurrentMappingsToDeMap.length <= 0) {
        return;
    }
    const getProjectsDataRelatedToCampaign = await getRelatedDataWithCampaign("boundary", campaignDetails.campaignNumber);
    const boundaryToProjectIdMapping: any = {};
    for (let i = 0; i < getProjectsDataRelatedToCampaign.length; i++) {
        boundaryToProjectIdMapping[getProjectsDataRelatedToCampaign[i]?.uniqueIdentifier] = getProjectsDataRelatedToCampaign[i]?.uniqueIdAfterProcess;
    }
    const getUsersRelatedToCampaign = await getRelatedDataWithCampaign("user", campaignDetails.campaignNumber);
    const phoneToUserIdMapping: any = {};
    for (let i = 0; i < getUsersRelatedToCampaign.length; i++) {
        phoneToUserIdMapping[getUsersRelatedToCampaign[i]?.uniqueIdentifier] = getUsersRelatedToCampaign[i]?.uniqueIdAfterProcess;
    }
    const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
    RequestInfo.userInfo.uuid = useruuid || campaignDetails?.auditDetails?.createdBy;
    for (let i = 0; i < allCurrentMappingsToDeMap.length; i++) {
        try {
            const projectId = boundaryToProjectIdMapping[allCurrentMappingsToDeMap[i]?.boundaryCode];
            const userId = phoneToUserIdMapping[allCurrentMappingsToDeMap[i]?.uniqueIdentifierForData];
            if(!userId || !projectId || !allCurrentMappingsToDeMap[i]?.mappingId){
                await produceModifiedMessages({ datas: [allCurrentMappingsToDeMap[i]] }, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC);
            }
            await fetchProjectStaffWsearchProjectStaff(RequestInfo, campaignDetails.tenantId, projectId, userId);
            await produceModifiedMessages({ datas: [allCurrentMappingsToDeMap[i]] }, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC);
        }
        catch (error) {
            // Log the error if the API call fails
            logger.error(`Failed to demap project staff for user with phone ${allCurrentMappingsToDeMap[i]?.uniqueIdentifierForData}:`, error);
            throw error; // Rethrow the error to propagate it
        }
    }
}

async function fetchProjectStaffWsearchProjectStaff(RequestInfo: any, tenantId: any, projectId: any, staffId: any) {
    const projectSearchBody = {
        RequestInfo,
        ProjectStaff: {
            projectId: [
                projectId
            ],
            staffId: [
                staffId
            ]
        }
    }

    const projectSearchParams = {
        tenantId: tenantId,
        offset: 0,
        limit: 1
    }
    logger.info("Project search params " + JSON.stringify(projectSearchParams))
    const projectStaffSearchResponse = await httpRequest(config?.host?.projectHost + config?.paths?.projectStaffSearch, projectSearchBody, projectSearchParams);
    if (projectStaffSearchResponse?.ProjectStaff && Array.isArray(projectStaffSearchResponse?.ProjectStaff) && projectStaffSearchResponse?.ProjectStaff?.length > 0) {
        await demapProjectStaff(RequestInfo, projectStaffSearchResponse);
    }
}

async function demapProjectStaff(RequestInfo: any,projectStaffResponse: any) {
    const projectStaffDeleteBody = {
        RequestInfo,
        ProjectStaff: [
            projectStaffResponse?.ProjectStaff[0]
        ]
    }
    try {
        await httpRequest(config?.host?.projectHost + config?.paths?.projectStaffDelete, projectStaffDeleteBody);
    }
    catch (error: any) {
        throwError("PROJECT", 500, "PROJECT_STAFF_DELETE_ERROR")
    }
  }