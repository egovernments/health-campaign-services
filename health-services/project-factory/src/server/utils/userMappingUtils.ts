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
    const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
    RequestInfo.userInfo.uuid = useruuid || campaignDetails?.auditDetails?.createdBy;
    const BATCH_SIZE = config?.batchConfig?.mappingBatchSize || 10;
    for (let batchStart = 0; batchStart < allCurrentMappingsToDo.length; batchStart += BATCH_SIZE) {
        const batch = allCurrentMappingsToDo.slice(batchStart, batchStart + BATCH_SIZE);
        const results = await Promise.allSettled(batch.map(async (mapping: any) => {
            const projectId = boundaryToProjectIdMapping[mapping?.boundaryCode];
            const userId = phoneToUserIdMapping[mapping?.uniqueIdentifierForData];
            const ProjectStaff = {
                tenantId: campaignDetails.tenantId,
                projectId,
                userId,
                startDate,
                endDate,
            };
            const newResourceBody = { RequestInfo, ProjectStaff };
            const projectStaffResponse = await createStaff(newResourceBody);
            mapping.status = mappingStatuses.mapped;
            if (projectStaffResponse?.ProjectStaff?.id) {
                mapping.mappingId = projectStaffResponse?.ProjectStaff?.id;
            } else {
                throw new Error("Failed to create project staff for user with phone " + mapping?.uniqueIdentifierForData);
            }
            await produceModifiedMessages({ datas: [mapping] }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, campaignDetails.tenantId);
        }));
        const failures = results.filter((r): r is PromiseRejectedResult => r.status === "rejected");
        if (failures.length > 0) {
            for (const f of failures) {
                logger.error(`User mapping failed in batch: `, f.reason);
            }
            throw failures[0].reason;
        }
    }
}

export async function startUserDemapping(campaignDetails: any, useruuid: string) {
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
    const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
    RequestInfo.userInfo.uuid = useruuid || campaignDetails?.auditDetails?.createdBy;
    const BATCH_SIZE = config?.batchConfig?.mappingBatchSize || 10;
    for (let batchStart = 0; batchStart < allCurrentMappingsToDeMap.length; batchStart += BATCH_SIZE) {
        const batch = allCurrentMappingsToDeMap.slice(batchStart, batchStart + BATCH_SIZE);
        const results = await Promise.allSettled(batch.map(async (mapping: any) => {
            const projectId = boundaryToProjectIdMapping[mapping?.boundaryCode];
            const userId = phoneToUserIdMapping[mapping?.uniqueIdentifierForData];
            if (!userId || !projectId || !mapping?.mappingId) {
                await produceModifiedMessages({ datas: [mapping] }, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC, campaignDetails.tenantId);
                return;
            }
            await fetchProjectStaffWsearchProjectStaff(RequestInfo, campaignDetails.tenantId, projectId, userId);
            await produceModifiedMessages({ datas: [mapping] }, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC, campaignDetails.tenantId);
        }));
        const failures = results.filter((r): r is PromiseRejectedResult => r.status === "rejected");
        if (failures.length > 0) {
            for (const f of failures) {
                logger.error(`User demapping failed in batch: `, f.reason);
            }
            throw failures[0].reason;
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