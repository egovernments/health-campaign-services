import { RequestInfo } from "../config/models/requestInfoSchema";
import { mappingStatuses } from "../config/constants";
import { getMappingDataRelatedToCampaign, getRelatedDataWithCampaign, throwError } from "./genericUtils";
import { logger } from "./logger";
import { createStaff } from "../api/genericApis";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";
import { httpRequest } from "./request";

export async function startUserMappingAndDemapping(campaignDetails: any, useruuid: string, requestInfo: RequestInfo) {
    await startUserMapping(campaignDetails, useruuid, requestInfo);
    await startUserDemapping(campaignDetails, useruuid, requestInfo);
}

export async function startUserMapping(campaignDetails: any, useruuid: string, requestInfo: RequestInfo) {
    const allCurrentMappingsToDo = await getMappingDataRelatedToCampaign("user", campaignDetails.campaignNumber, campaignDetails.tenantId, mappingStatuses.toBeMapped);
    if (allCurrentMappingsToDo.length <= 0) {
        return;
    }
    const getProjectsDataRelatedToCampaign = await getRelatedDataWithCampaign("boundary", campaignDetails.campaignNumber, campaignDetails.tenantId);
    const boundaryToProjectIdMapping: any = {};
    for (let i = 0; i < getProjectsDataRelatedToCampaign.length; i++) {
        boundaryToProjectIdMapping[getProjectsDataRelatedToCampaign[i]?.uniqueIdentifier] = getProjectsDataRelatedToCampaign[i]?.uniqueIdAfterProcess;
    }
    const getUsersRelatedToCampaign = await getRelatedDataWithCampaign("user", campaignDetails.campaignNumber, campaignDetails.tenantId);
    const phoneToUserIdMapping: any = {};
    for (let i = 0; i < getUsersRelatedToCampaign.length; i++) {
        phoneToUserIdMapping[getUsersRelatedToCampaign[i]?.uniqueIdentifier] = getUsersRelatedToCampaign[i]?.uniqueIdAfterProcess;
    }
    const startDate = campaignDetails.startDate;
    const endDate = campaignDetails.endDate;
    const RequestInfo = requestInfo;
    for (let i = 0; i < allCurrentMappingsToDo.length; i++) {
        try {
            const projectId = boundaryToProjectIdMapping[allCurrentMappingsToDo[i]?.boundaryCode];
            const userId = phoneToUserIdMapping[allCurrentMappingsToDo[i]?.uniqueIdentifierForData];

            // No user means HRMS creation failed or the row was sheet-invalid.
            // Skip the staff API call and mark the mapping as skipped — does
            // not fail the campaign.
            if (!userId) {
                allCurrentMappingsToDo[i].status = mappingStatuses.skipped;
                await produceModifiedMessages({ datas: [allCurrentMappingsToDo[i]] }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, campaignDetails.tenantId);
                logger.info(`Skipping user mapping for ${allCurrentMappingsToDo[i]?.uniqueIdentifierForData} — user not created`);
                continue;
            }

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
            await produceModifiedMessages({ datas: [allCurrentMappingsToDo[i]] }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, campaignDetails.tenantId);
        }
        catch (error) {
            // Genuine staff API error — mark this mapping as failed and continue
            // with the rest. User-level mapping failures are non-blocking.
            logger.error(`Failed to create project staff for user with phone ${allCurrentMappingsToDo[i]?.uniqueIdentifierForData}:`, error);
            allCurrentMappingsToDo[i].status = mappingStatuses.failed;
            try {
                await produceModifiedMessages({ datas: [allCurrentMappingsToDo[i]] }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, campaignDetails.tenantId);
            } catch (persistError) {
                logger.error(`Failed to persist failed user mapping status for ${allCurrentMappingsToDo[i]?.uniqueIdentifierForData}:`, persistError);
            }
        }
    }
}

export async function startUserDemapping(campaignDetails: any, useruuid: string, requestInfo: RequestInfo) {
    const allCurrentMappingsToDeMap = await getMappingDataRelatedToCampaign("user", campaignDetails.campaignNumber, campaignDetails.tenantId, mappingStatuses.toBeDeMapped);
    if (allCurrentMappingsToDeMap.length <= 0) {
        return;
    }
    const getProjectsDataRelatedToCampaign = await getRelatedDataWithCampaign("boundary", campaignDetails.campaignNumber, campaignDetails.tenantId);
    const boundaryToProjectIdMapping: any = {};
    for (let i = 0; i < getProjectsDataRelatedToCampaign.length; i++) {
        boundaryToProjectIdMapping[getProjectsDataRelatedToCampaign[i]?.uniqueIdentifier] = getProjectsDataRelatedToCampaign[i]?.uniqueIdAfterProcess;
    }
    const getUsersRelatedToCampaign = await getRelatedDataWithCampaign("user", campaignDetails.campaignNumber, campaignDetails.tenantId);
    const phoneToUserIdMapping: any = {};
    for (let i = 0; i < getUsersRelatedToCampaign.length; i++) {
        phoneToUserIdMapping[getUsersRelatedToCampaign[i]?.uniqueIdentifier] = getUsersRelatedToCampaign[i]?.uniqueIdAfterProcess;
    }
    const RequestInfo = requestInfo;
    for (let i = 0; i < allCurrentMappingsToDeMap.length; i++) {
        try {
            const projectId = boundaryToProjectIdMapping[allCurrentMappingsToDeMap[i]?.boundaryCode];
            const userId = phoneToUserIdMapping[allCurrentMappingsToDeMap[i]?.uniqueIdentifierForData];
            if(!userId || !projectId || !allCurrentMappingsToDeMap[i]?.mappingId){
                // No server-side state to undo — drop the local mapping row.
                await produceModifiedMessages({ datas: [allCurrentMappingsToDeMap[i]] }, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC, campaignDetails.tenantId);
                continue;
            }
            await fetchProjectStaffWsearchProjectStaff(RequestInfo, campaignDetails.tenantId, projectId, userId);
            await produceModifiedMessages({ datas: [allCurrentMappingsToDeMap[i]] }, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC, campaignDetails.tenantId);
        }
        catch (error) {
            // Genuine demap API error — mark failed (non-blocking) and continue.
            logger.error(`Failed to demap project staff for user with phone ${allCurrentMappingsToDeMap[i]?.uniqueIdentifierForData}:`, error);
            allCurrentMappingsToDeMap[i].status = mappingStatuses.failed;
            try {
                await produceModifiedMessages({ datas: [allCurrentMappingsToDeMap[i]] }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, campaignDetails.tenantId);
            } catch (persistError) {
                logger.error(`Failed to persist failed user demap status for ${allCurrentMappingsToDeMap[i]?.uniqueIdentifierForData}:`, persistError);
            }
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