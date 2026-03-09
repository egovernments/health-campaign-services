import { defaultRequestInfo } from "../api/coreApis";
import { mappingStatuses } from "../config/constants";
import { getMappingDataRelatedToCampaign, getRelatedDataWithCampaign } from "./genericUtils";
import { logger } from "./logger";
import { createProjectResource } from "../api/genericApis";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";

export async function startResourceMapping(campaignDetails : any, useruuid : string) {
    const allCurrentMappingsToDo = await getMappingDataRelatedToCampaign("resource", campaignDetails.campaignNumber, campaignDetails.tenantId, mappingStatuses.toBeMapped);
    if(allCurrentMappingsToDo.length <= 0){
        return;
    }
    const getProjectsDataRelatedToCampaign = await getRelatedDataWithCampaign("boundary", campaignDetails.campaignNumber, campaignDetails.tenantId);
    const boundaryToProjectIdMapping : any = {};
    for(let i = 0; i < getProjectsDataRelatedToCampaign.length; i++){
        boundaryToProjectIdMapping[getProjectsDataRelatedToCampaign[i]?.uniqueIdentifier] = getProjectsDataRelatedToCampaign[i]?.uniqueIdAfterProcess;
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
            const ProjectResource = {
                tenantId: campaignDetails.tenantId,
                projectId,
                resource: {
                    productVariantId: mapping?.uniqueIdentifierForData,
                    type: "DRUG",
                    isBaseUnitVariant: false,
                },
                startDate,
                endDate,
            };
            const newResourceBody = { RequestInfo, ProjectResource };
            const projectResourceResponse = await createProjectResource(newResourceBody);
            mapping.status = mappingStatuses.mapped;
            if (projectResourceResponse?.ProjectResource?.id) {
                mapping.mappingId = projectResourceResponse?.ProjectResource?.id;
            } else {
                throw new Error("Failed to create project resource for resourceId " + mapping?.uniqueIdentifierForData);
            }
            await produceModifiedMessages({ datas: [mapping] }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, campaignDetails.tenantId);
        }));
        const failures = results.filter((r): r is PromiseRejectedResult => r.status === "rejected");
        if (failures.length > 0) {
            for (const f of failures) {
                logger.error(`Resource mapping failed in batch: `, f.reason);
            }
            throw failures[0].reason;
        }
    }
}