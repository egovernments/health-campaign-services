import { defaultRequestInfo } from "../api/coreApis";
import { mappingStatuses } from "../config/constants";
import { getMappingDataRelatedToCampaign, getRelatedDataWithCampaign } from "./genericUtils";
import { logger } from "./logger";
import { createProjectResource } from "../api/genericApis";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";

export async function startResourceMapping(campaignDetails : any, useruuid : string) {
    const allCurrentMappingsToDo = await getMappingDataRelatedToCampaign("resource", campaignDetails.campaignNumber, mappingStatuses.toBeMapped);
    if(allCurrentMappingsToDo.length <= 0){
        return;
    }
    const getProjectsDataRelatedToCampaign = await getRelatedDataWithCampaign("boundary", campaignDetails.campaignNumber);
    const boundaryToProjectIdMapping : any = {};
    for(let i = 0; i < getProjectsDataRelatedToCampaign.length; i++){
        boundaryToProjectIdMapping[getProjectsDataRelatedToCampaign[i]?.uniqueIdentifier] = getProjectsDataRelatedToCampaign[i]?.uniqueIdAfterProcess;
    }
    const startDate = campaignDetails.startDate;
    const endDate = campaignDetails.endDate;
    const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
    RequestInfo.userInfo.uuid = useruuid || campaignDetails?.auditDetails?.createdBy;
    for(let i = 0; i < allCurrentMappingsToDo.length; i++){
        try {
            const projectId = boundaryToProjectIdMapping[allCurrentMappingsToDo[i]?.boundaryCode];
            const ProjectResource = {
              tenantId: campaignDetails.tenantId,
              projectId,
              resource: {
                productVariantId: allCurrentMappingsToDo[i]?.uniqueIdentifierForData,
                type: "DRUG",
                isBaseUnitVariant: false,
              },
              startDate,
              endDate,
            };
            const newResourceBody = { RequestInfo, ProjectResource };
            const projectResourceResponse = await createProjectResource(newResourceBody);
            allCurrentMappingsToDo[i].status = mappingStatuses.mapped;
            if(projectResourceResponse?.ProjectResource?.id){
                allCurrentMappingsToDo[i].mappingId = projectResourceResponse?.ProjectResource?.id;
            }
            else{
                throw new Error("Failed to create project resource for resourceId " + allCurrentMappingsToDo[i]?.uniqueIdentifierForData);
            }
            await produceModifiedMessages({ datas: [allCurrentMappingsToDo[i]] }, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC);
          }
          catch (error) {
            // Log the error if the API call fails
            logger.error(`Failed to create project resource for resourceId ${allCurrentMappingsToDo[i]?.uniqueIdentifierForData}:`, error);
            throw error; // Rethrow the error to propagate it
          }
    }
}