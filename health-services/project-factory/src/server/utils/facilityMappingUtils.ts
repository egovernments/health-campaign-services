import { defaultRequestInfo } from "../api/coreApis";
import { produceModifiedMessages } from "../kafka/Producer";
import { getMappingDataRelatedToCampaign, getRelatedDataWithCampaign } from "./genericUtils";
import { mappingStatuses } from "../config/constants";
import { logger } from "./logger";
import { createProjectFacility } from "../api/genericApis";
import config from "../config";
import { httpRequest } from "./request";

export async function startFacilityMappingAndDemapping(campaignDetails: any, useruuid: string) {
    await startFacilityMapping(campaignDetails, useruuid);
    await startFacilityDemapping(campaignDetails, useruuid);
}

export async function startFacilityMapping(campaignDetails: any, useruuid: string) {
    const facilitiesToMap = await getMappingDataRelatedToCampaign("facility", campaignDetails.campaignNumber, campaignDetails?.tenantId, mappingStatuses.toBeMapped);
    if (facilitiesToMap.length === 0) return;

    const boundaryData = await getRelatedDataWithCampaign("boundary", campaignDetails.campaignNumber, campaignDetails.tenantId);
    const boundaryToProjectId: Record<string, string> = {};
    boundaryData.forEach(row => {
        boundaryToProjectId[row?.uniqueIdentifier] = row?.uniqueIdAfterProcess;
    });

    const facilityData = await getRelatedDataWithCampaign("facility", campaignDetails.campaignNumber, campaignDetails.tenantId);
    const facilityMap: Record<string, string> = {};
    facilityData.forEach(row => {
        facilityMap[row?.uniqueIdentifier] = row?.uniqueIdAfterProcess;
    });

    const startDate = campaignDetails.startDate;
    const endDate = campaignDetails.endDate;
    const RequestInfo = {
        ...JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo)),
        userInfo: { uuid: useruuid || campaignDetails?.auditDetails?.createdBy }
    };

    const BATCH_SIZE = config?.batchConfig?.mappingBatchSize || 10;
    for (let batchStart = 0; batchStart < facilitiesToMap.length; batchStart += BATCH_SIZE) {
        const batch = facilitiesToMap.slice(batchStart, batchStart + BATCH_SIZE);
        const results = await Promise.allSettled(batch.map(async (row: any) => {
            const projectId = boundaryToProjectId[row?.boundaryCode];
            const facilityId = facilityMap[row?.uniqueIdentifierForData];

            if (!projectId || !facilityId) throw new Error("Missing project/facility ID");

            const ProjectFacility = {
                tenantId: campaignDetails.tenantId.split(".")?.[0],
                projectId,
                facilityId,
                startDate,
                endDate
            };

            const newBody = { RequestInfo, ProjectFacility };
            const response = await createProjectFacility(newBody);

            row.status = mappingStatuses.mapped;
            if (response?.ProjectFacility?.id) {
                row.mappingId = response.ProjectFacility.id;
            }

            await produceModifiedMessages({ datas: [row] }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, campaignDetails?.tenantId);
        }));
        const failures = results.filter((r): r is PromiseRejectedResult => r.status === "rejected");
        if (failures.length > 0) {
            for (const f of failures) {
                logger.error(`Facility mapping failed in batch: `, f.reason);
            }
            throw failures[0].reason;
        }
    }
}


export async function startFacilityDemapping(campaignDetails: any, useruuid: string) {
    const facilitiesToDeMap = await getMappingDataRelatedToCampaign("facility", campaignDetails.campaignNumber, campaignDetails?.tenantId, mappingStatuses.toBeDeMapped);
    if (facilitiesToDeMap.length === 0) return;

    const boundaryData = await getRelatedDataWithCampaign("boundary", campaignDetails.campaignNumber, campaignDetails.tenantId);
    const boundaryToProjectId: Record<string, string> = {};
    boundaryData.forEach(row => {
        boundaryToProjectId[row?.uniqueIdentifier] = row?.uniqueIdAfterProcess;
    });

    const facilityData = await getRelatedDataWithCampaign("facility", campaignDetails.campaignNumber, campaignDetails.tenantId);
    const facilityMap: Record<string, string> = {};
    facilityData.forEach(row => {
        facilityMap[row?.uniqueIdentifier] = row?.uniqueIdAfterProcess;
    });

    const RequestInfo = {
        ...JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo)),
        userInfo: { uuid: useruuid || campaignDetails?.auditDetails?.createdBy }
    };

    const BATCH_SIZE = config?.batchConfig?.mappingBatchSize || 10;
    for (let batchStart = 0; batchStart < facilitiesToDeMap.length; batchStart += BATCH_SIZE) {
        const batch = facilitiesToDeMap.slice(batchStart, batchStart + BATCH_SIZE);
        const results = await Promise.allSettled(batch.map(async (row: any) => {
            const projectId = boundaryToProjectId[row?.boundaryCode];
            const facilityId = facilityMap[row?.uniqueIdentifierForData];
            const mappingId = row?.mappingId;

            if (!projectId || !facilityId || !mappingId) {
                await produceModifiedMessages({ datas: [row] }, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC, campaignDetails?.tenantId);
                return;
            }

            await fetchAndDeleteProjectFacility(RequestInfo, campaignDetails.tenantId, projectId, facilityId);
            await produceModifiedMessages({ datas: [row] }, config.kafka.KAFKA_DELETE_MAPPING_DATA_TOPIC, campaignDetails?.tenantId);
        }));
        const failures = results.filter((r): r is PromiseRejectedResult => r.status === "rejected");
        if (failures.length > 0) {
            for (const f of failures) {
                logger.error(`Facility demapping failed in batch: `, f.reason);
            }
            throw failures[0].reason;
        }
    }
}


async function fetchAndDeleteProjectFacility(RequestInfo: any, tenantId: string, projectId: string, facilityId: string) {
    const searchBody = {
        RequestInfo,
        ProjectFacility: {
            projectId: [projectId],
            facilityId: [facilityId]
        }
    };

    const searchParams = {
        tenantId,
        offset: 0,
        limit: 1
    };

    const response = await httpRequest(
        config?.host?.projectHost + config?.paths?.projectFacilitySearch,
        searchBody,
        searchParams
    );

    if (response?.ProjectFacilities?.length > 0) {
        await deleteProjectFacilityMapping(RequestInfo, response);
    }
}

async function deleteProjectFacilityMapping(RequestInfo: any, response: any) {
    const deleteBody = {
        RequestInfo,
        ProjectFacilities: [response?.ProjectFacilities[0]]
    };

    await httpRequest(config?.host?.projectHost + config?.paths?.projectFacilityDelete, deleteBody);
}
