import { defaultRequestInfo } from "../api/coreApis";
import config from "../config";
import { campaignProcessStatus, mappingStatus, mappingTypes, processNamesConstantsInOrder } from "../config/constants";
import { produceModifiedMessages } from "../kafka/Producer";
import { executeQuery } from "./db";
import { logger } from "./logger";
import { checkIfProcessIsCompleted, checkifProcessIsFailed, markProcessStatus } from "./processTrackUtils";
import { httpRequest } from "./request";
import { v4 as uuidv4 } from 'uuid';

async function persistForStaffMapping(formatedCampaignMappingsForStaff: any[], campaignNumber: string, tenantId: string, userUuid: string) {
    for (let i = 0; i < formatedCampaignMappingsForStaff.length; i += 100) {
        const chunk = formatedCampaignMappingsForStaff.slice(i, i + 100);
        const produceMessage: any = {
            processName: processNamesConstantsInOrder.mapping,
            data: {
                tenantId: tenantId,
                userUuid: userUuid,
                staffMappingArray: chunk
            },
            campaignNumber: campaignNumber
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SUB_PROCESS_HANDLER_TOPIC
        );
    }

}

export async function modifyAndPushInKafkaForStaffMapping(campaignMappingsForStaff: any[], campaignEmployees: any[], boundaryCodeAndProjectIdMapping: any, campaignNumber: string, tenantId: string, userUuid: string) {
    const mobileNumberAndUserUuidMapping = campaignEmployees?.reduce((acc: any, curr: any) => {
        acc[curr?.mobileNumber] = curr?.userServiceUuid;
        return acc;
    }, {})
    const formatedCampaignMappingsForStaff = campaignMappingsForStaff?.map((mapping: any) => ({
        projectId: boundaryCodeAndProjectIdMapping?.[mapping?.boundaryCode],
        status: mapping?.status,
        userServiceUuid: mobileNumberAndUserUuidMapping[mapping?.mappingIdentifier],
        campaignMappingId: mapping?.id
    }))
    await persistForStaffMapping(formatedCampaignMappingsForStaff, campaignNumber, tenantId, userUuid);
}

async function persistForFacilityMapping(formatedCampaignMappingsForFacility: any[], campaignNumber: string, tenantId: string, userUuid: string) {
    for (let i = 0; i < formatedCampaignMappingsForFacility.length; i += 100) {
        const chunk = formatedCampaignMappingsForFacility.slice(i, i + 100);
        const produceMessage: any = {
            processName: processNamesConstantsInOrder.mapping,
            data: {
                tenantId: tenantId,
                userUuid: userUuid,
                facilityMappingArray: chunk
            },
            campaignNumber: campaignNumber
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SUB_PROCESS_HANDLER_TOPIC
        );
    }

}

export async function modifyAndPushInKafkaForFacilityMapping(campaignMappingsForFacility: any[], campaignFacilities: any[], boundaryCodeAndProjectIdMapping: any, campaignNumber: string, tenantId: string, userUuid: string) {
    const facilityNameAndFacilityIdMapping = campaignFacilities?.reduce((acc: any, curr: any) => {
        const key = `N${campaignNumber}!#!${curr?.name}N`;
        acc[key] = curr?.facilityId;
        return acc;
    }, {})
    const formatedCampaignMappingsForFacility = campaignMappingsForFacility?.map((mapping: any) => ({
        projectId: boundaryCodeAndProjectIdMapping?.[mapping?.boundaryCode],
        status: mapping?.status,
        facilityId: facilityNameAndFacilityIdMapping[`N${mapping?.mappingIdentifier}N`],
        campaignMappingId: mapping?.id
    }))
    await persistForFacilityMapping(formatedCampaignMappingsForFacility, campaignNumber, tenantId, userUuid);
}

async function persistForResourceMapping(formatedArrayForResourceMapping: any[], campaignNumber: string, tenantId: string, userUuid: string) {
    for (let i = 0; i < formatedArrayForResourceMapping.length; i += 100) {
        const chunk = formatedArrayForResourceMapping.slice(i, i + 100);
        const produceMessage: any = {
            processName: processNamesConstantsInOrder.mapping,
            data: {
                tenantId: tenantId,
                userUuid: userUuid,
                resourceMappingArray: chunk
            },
            campaignNumber: campaignNumber
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SUB_PROCESS_HANDLER_TOPIC
        );
    }

}

export async function modifyAndPushInKafkaForResourceMapping(campaignMappingsForResources: any[], boundaryCodeAndProjectIdMapping: any[], campaignNumber: string, tenantId: string, userUuid: string) {
    const formatedArrayForResourceMapping = [];
    for (const mapping of campaignMappingsForResources) {
        if(mapping.status == mappingStatus.toBeMapped)
            formatedArrayForResourceMapping.push({
                pvarId: mapping?.mappingIdentifier,
                projectId: boundaryCodeAndProjectIdMapping?.[mapping?.boundaryCode],
                campaignMappingId : mapping?.id
            })
    }
    await persistForResourceMapping(formatedArrayForResourceMapping, campaignNumber, tenantId, userUuid);
}

export async function processSubMappingFromConsumer(data: any, campaignNumber: string) {
    const { tenantId, userUuid } = data;
    if (data?.facilityMappingArray && Array.isArray(data?.facilityMappingArray) && data?.facilityMappingArray?.length > 0) await doFacilityMappingOrDetaching(data?.facilityMappingArray, tenantId, userUuid);
    if (data?.resourceMappingArray && Array.isArray(data?.resourceMappingArray) && data?.resourceMappingArray?.length > 0) await doResourceMapping(data?.resourceMappingArray, tenantId, userUuid);
    if (data?.staffMappingArray && Array.isArray(data?.staffMappingArray) && data?.staffMappingArray?.length > 0) await doStaffMappingOrDetaching(data?.staffMappingArray, campaignNumber, tenantId, userUuid);
}

async function doStaffMappingOrDetaching(staffMappingArray: any[], campaignNumber: string, tenantId: string, userUuid: string) {
    await doStaffMapping(staffMappingArray, tenantId, userUuid);
    await doStaffDetaching(staffMappingArray, tenantId, userUuid);
}

async function doStaffMapping(
    staffMappingArray: any[],
    tenantId: string,
    userUuid: string
) {
    const staffMappingToBeMapped = staffMappingArray.filter(
        (mapping: any) => mapping?.status === mappingStatus.toBeMapped
    );

    await Promise.all(
        staffMappingToBeMapped.map(async (mapping) => {
            const { projectId, userServiceUuid, campaignMappingId } = mapping;
            const projectStaffCreateUrl = `${config.host.projectHost}${config.paths.staffCreate}`;
            const projectStaffCreateBody = {
                RequestInfo: defaultRequestInfo?.RequestInfo,
                ProjectStaff: {
                    tenantId,
                    projectId,
                    userId: userServiceUuid
                }
            };

            logger.info("Project Staff Creation : API : " + config.paths.staffCreate);

            const projectStaffResponse = await httpRequest(
                projectStaffCreateUrl,
                projectStaffCreateBody,
                undefined,
                "post",
                undefined,
                undefined,
                undefined,
                false,
                true
            );

            if (projectStaffResponse?.Errors?.[0]?.message.includes("errorCode=DUPLICATE_ENTITY")) {
                logger.info("Project Staff already exists");
            } else if (projectStaffResponse?.Errors?.length > 0) {
                logger.error("Project Staff creation failed");
                throw new Error(projectStaffResponse?.Errors?.[0]?.message);
            } else {
                logger.info("Project Staff created successfully");
            }

            const currentTime = Date.now();
            const produceMessage: any = {
                campaignMappings: [
                    {
                        id: campaignMappingId,
                        mappingCode: projectStaffResponse?.ProjectStaff?.id,
                        status: mappingStatus.mapped,
                        additionalDetails: {},
                        lastModifiedBy: userUuid,
                        lastModifiedTime: currentTime
                    }
                ]
            };

            await produceModifiedMessages(
                produceMessage,
                config?.kafka?.KAFKA_UPDATE_CAMPAIGN_MAPPINGS_TOPIC
            );
        })
    );
}

async function doStaffDetaching(staffMappingArray: any[], tenantId: string, userUuid: string) {
    const staffMappingToBeDetached = staffMappingArray.filter(
        (mapping: any) => mapping?.status === mappingStatus.toBeDetached
    );

    await Promise.all(
        staffMappingToBeDetached.map(async (staffMap) => {
            const { projectId, userServiceUuid, campaignMappingId } = staffMap;
            const projectStaffSearchBody = {
                RequestInfo: defaultRequestInfo?.RequestInfo,
                ProjectStaff: {
                    projectId: [projectId],
                    userId: [userServiceUuid],
                    tenantId: tenantId
                }
            };
            const params = { limit: 1, offset: 0, tenantId: tenantId };
            const searchUrl = `${config.host.projectHost}${config.paths.projectStaffSearch}`;

            const projectStaffSearchResponse = await httpRequest(
                searchUrl,
                projectStaffSearchBody,
                params
            );

            const projectStaffId = projectStaffSearchResponse?.ProjectStaff?.[0]?.id;

            if (projectStaffId) {
                const projectStaffDeleteUrl = `${config.host.projectHost}${config.paths.projectStaffDelete}`;
                const projectStaffDeleteBody = {
                    RequestInfo: defaultRequestInfo?.RequestInfo,
                    ProjectStaff: projectStaffSearchResponse?.ProjectStaff?.[0]
                };

                await httpRequest(projectStaffDeleteUrl, projectStaffDeleteBody);

                logger.info(`Project Staff deleted successfully for userServiceUuid ${userServiceUuid} and projectId ${projectId}`);

                const currentTime = Date.now();
                const produceMessage: any = {
                    campaignMappings: [
                        {
                            id: campaignMappingId,
                            mappingCode: projectStaffId,
                            status: mappingStatus.detached,
                            additionalDetails: {},
                            lastModifiedBy: userUuid,
                            lastModifiedTime: currentTime
                        }
                    ]
                };

                await produceModifiedMessages(
                    produceMessage,
                    config.kafka.KAFKA_UPDATE_CAMPAIGN_MAPPINGS_TOPIC
                );
            } else {
                logger.warn(`Project Staff not found for detaching for userServiceUuid ${userServiceUuid} and projectId ${projectId}`);
            }
        })
    );
}


async function doResourceMapping(resourceMappingArray: any[], tenantId: string, userUuid: string) {
    await Promise.all(
        resourceMappingArray.map(async (resourceMap) => {
            const { projectId, pvarId, campaignMappingId } = resourceMap;
            const projectResourceCreateBody = {
                RequestInfo: defaultRequestInfo?.RequestInfo,
                ProjectResource:
                {
                    tenantId,
                    projectId,
                    resource: {
                        productVariantId: pvarId,
                        type: "DRUG",
                        isBaseUnitVariant: false,
                    }
                }
            };
            const projectResourceCreateUrl =
                `${config.host.projectHost}` + `${config.paths.projectResourceCreate}`;
            logger.info("Project Resource Creation : API : " + config.paths.projectResourceCreate);

            const projectResourceResponse = await httpRequest(
                projectResourceCreateUrl,
                projectResourceCreateBody,
                undefined,
                "post",
                undefined,
                undefined,
                undefined,
                false,
                true
            );

            if (projectResourceResponse?.Errors?.[0]?.message.includes("errorCode=DUPLICATE_ENTITY")) {
                logger.info("Project Resource already exists");
            }
            else if (projectResourceResponse?.Errors?.length > 0) {
                logger.error("Project Resource creation failed");
                throw new Error(projectResourceResponse?.Errors?.[0]?.message);
            }
            else {
                logger.info("Project Resource created successfully");
            }

            const currentTime = Date.now();
            const produceMessage: any = {
                campaignMappings: [
                    {
                        id: campaignMappingId,
                        mappingCode: projectResourceResponse?.ProjectResource?.id,
                        status: mappingStatus.mapped,
                        additionalDetails: {},
                        lastModifiedBy: userUuid,
                        lastModifiedTime: currentTime
                    }
                ]
            };

            await produceModifiedMessages(
                produceMessage,
                config.kafka.KAFKA_UPDATE_CAMPAIGN_MAPPINGS_TOPIC
            );
        })
    );
}

async function doFacilityMappingOrDetaching(facilityMappingArray: any[], tenantId: string, userUuid: string) {
    await doFacilityMapping(facilityMappingArray, tenantId, userUuid);
    await doFacilityDetaching(facilityMappingArray, tenantId, userUuid);
}

async function doFacilityMapping(
    facilityMappingArray: any[],
    tenantId: string,
    userUuid: string
) {
    const facilitiesMappingToBeMapped = facilityMappingArray.filter(
        (facilityMap) => facilityMap?.status === mappingStatus.toBeMapped
    );

    await Promise.all(
        facilitiesMappingToBeMapped.map(async (facilityMap) => {
            const { projectId, facilityId, campaignMappingId } = facilityMap;

            // Create project facilities
            const projectFacilityCreateUrl =
                `${config.host.projectHost}${config.paths.projectFacilityCreate}`;
            const projectFacilityCreateBody = {
                RequestInfo: defaultRequestInfo?.RequestInfo,
                ProjectFacility: {
                    tenantId: tenantId,
                    projectId: projectId,
                    facilityId: facilityId
                }
            };
            logger.info("Project Facility Creation  : API :" + config.paths.projectFacilityCreate);

            const projectFacilityResponse = await httpRequest(
                projectFacilityCreateUrl,
                projectFacilityCreateBody,
                undefined,
                "post",
                undefined,
                undefined,
                undefined,
                false,
                true
            );

            if (projectFacilityResponse?.Errors?.[0]?.message.includes("errorCode=DUPLICATE_ENTITY")) {
                logger.info("Project Facility already exists");
            } else if (projectFacilityResponse?.Errors?.length > 0) {
                logger.error("Project Facility creation failed");
                throw new Error(projectFacilityResponse?.Errors?.[0]?.message);
            } else {
                logger.info("Project Facility created successfully");
            }

            const currentTime = Date.now();
            const produceMessage: any = {
                campaignMappings: [
                    {
                        id: campaignMappingId,
                        mappingCode: projectFacilityResponse?.ProjectFacility?.id,
                        status: mappingStatus.mapped,
                        additionalDetails: {},
                        lastModifiedBy: userUuid,
                        lastModifiedTime: currentTime
                    }
                ]
            };

            await produceModifiedMessages(
                produceMessage,
                config.kafka.KAFKA_UPDATE_CAMPAIGN_MAPPINGS_TOPIC
            );
        })
    );
}

async function doFacilityDetaching(
    facilityMappingArray: any[],
    tenantId: string,
    userUuid: string
) {
    const facilitiesMappingToBeDetached = facilityMappingArray.filter(
        (facilityMap) => facilityMap?.status === mappingStatus.toBeDetached
    );

    await Promise.all(
        facilitiesMappingToBeDetached.map(async (facilityMap) => {
            const { projectId, facilityId, campaignMappingId } = facilityMap;
            const projectFacilitySearchBody = {
                RequestInfo: defaultRequestInfo?.RequestInfo,
                ProjectFacility: {
                    projectId: [projectId],
                    facilityId: [facilityId],
                    tenantId: tenantId
                }
            };
            const params = { limit : 1, offset : 0 , tenantId: tenantId };

            const searchUrl = `${config.host.projectHost}${config.paths.projectFacilitySearch}`;

            const projectFacilitySearchResponse = await httpRequest(
                searchUrl,
                projectFacilitySearchBody,
                params
            );

            const projectFacilityId = projectFacilitySearchResponse?.ProjectFacilities?.[0]?.id;

            if (projectFacilityId) {
                const projectFacilityDeleteUrl = `${config.host.projectHost}${config.paths.projectFacilityDelete}/${projectFacilityId}`;
                const projectFacilityDeleteBody = {
                    RequestInfo: defaultRequestInfo?.RequestInfo,
                    ProjectFacility: projectFacilitySearchResponse?.ProjectFacilities?.[0]
                };

                await httpRequest(projectFacilityDeleteUrl, projectFacilityDeleteBody);

                logger.info(`Project Facility deleted successfully for facilityId ${facilityId} and projectId ${projectId}`);

                const currentTime = Date.now();
                const produceMessage: any = {
                    campaignMappings: [
                        {
                            id: campaignMappingId,
                            mappingCode: projectFacilityId,
                            status: mappingStatus.detached,
                            additionalDetails: {},
                            lastModifiedBy: userUuid,
                            lastModifiedTime: currentTime
                        }
                    ]
                };

                await produceModifiedMessages(
                    produceMessage,
                    config.kafka.KAFKA_UPDATE_CAMPAIGN_MAPPINGS_TOPIC
                );
            } else {
                logger.warn(`Project Facility not found for detaching for facilityId ${facilityId} and projectId ${projectId}`);
            }
        })
    );
}

export async function createCampaignMappings(campaignDetailsAndRequestInfo: any) {
    try {
        const { CampaignDetails } = campaignDetailsAndRequestInfo;
        const isProcessAlreadyCompleted = await checkIfProcessIsCompleted(
            CampaignDetails?.campaignNumber,
            processNamesConstantsInOrder.mapping
        )
        if (isProcessAlreadyCompleted) {
            logger.info("Mapping process already completed");
            return;
        }
        
        const produceMessage: any = {
            processName: processNamesConstantsInOrder.mapping,
            campaignDetailsAndRequestInfo: campaignDetailsAndRequestInfo
        }
        await produceModifiedMessages(produceMessage, config.kafka.KAFKA_PROCESS_HANDLER_TOPIC);

        // TODO : Remove confirmation from here
        // Function to delay execution for a specified time
        let mappingCreationStatusConfirmed = false;
        let status = "";
        const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
        const processConfirmationAttempts = parseInt(config?.values?.processConfirmationAttempts || "75") || 75;

        // Check process completion with retries
        for (let attempt = 0; attempt < processConfirmationAttempts; attempt++) {
            const isCompleted = await checkIfProcessIsCompleted(
                CampaignDetails?.campaignNumber,
                processNamesConstantsInOrder.mapping
            );
            const isFailed = await checkifProcessIsFailed(
                CampaignDetails?.campaignNumber,
                processNamesConstantsInOrder.mapping
            )

            const isEveryMappingMapped = await checkIfEveryMappingMapped(CampaignDetails?.campaignNumber);

            if (isCompleted || isFailed) {
                logger.info(`Mapping confirmed successfully. Attempt ${attempt + 1} out of ${processConfirmationAttempts}`);
                mappingCreationStatusConfirmed = true;
                status = isCompleted ? campaignProcessStatus.completed : campaignProcessStatus.failed;
                break;  // Exit successfully if process is completed
            }
            else if(isEveryMappingMapped){
                logger.info(`Every mapping mapped successfully. Attempt ${attempt + 1} out of ${processConfirmationAttempts}`);
                mappingCreationStatusConfirmed = true;
                status = campaignProcessStatus.completed;
                markProcessStatus(CampaignDetails?.campaignNumber, processNamesConstantsInOrder.mapping, campaignProcessStatus.completed);
                break;
            }
            else {
                logger.warn(`Mapping process not completed yet. Attempt ${attempt + 1} out of ${processConfirmationAttempts}.`);
            }
            await delay(20000);  // Wait for 2 seconds before retrying
        }
        if (!mappingCreationStatusConfirmed) {
            logger.error(`Mapping process did not complete after ${processConfirmationAttempts} attempts.`);
            throw new Error(`Mapping process did not complete after ${processConfirmationAttempts} attempts.`);
        }
        else {
            if (status == campaignProcessStatus.failed) {
                logger.error(`Mapping process failed.`);
                throw new Error(`Mapping process failed.`);
            }
            else{
                logger.info(`Mapping process completed successfully.`);
            }
        }
    } catch (error: any) {
        console.log(error);
        throw new Error(error);
    }
}

async function checkIfEveryMappingMapped(campaignNumber: string): Promise<boolean> {
    const query = `
        SELECT 
            (COUNT(*) FILTER (WHERE status = $2) = 0) AS no_to_be_mapped,
            (COUNT(*) FILTER (WHERE status = $3 AND (mappingcode IS NULL OR mappingcode = '')) = 0) AS all_mapped_have_mappingcode,
            (COUNT(*) FILTER (WHERE status = $4) = 0) AS no_to_be_detached
        FROM ${config?.DB_CONFIG.DB_CAMPAIGN_MAPPINGS_TABLE_NAME}
        WHERE campaignnumber = $1;
    `;

    try {
        const result = await executeQuery(query, [
            campaignNumber,
            mappingStatus.toBeMapped,
            mappingStatus.mapped,
            mappingStatus.toBeDetached
        ]);

        if (result.rows.length > 0) {
            const { no_to_be_mapped, all_mapped_have_mappingcode, no_to_be_detached } = result.rows[0];
            return no_to_be_mapped && all_mapped_have_mappingcode && no_to_be_detached;
        }

        return false; // If no records exist for the campaign, return false
    } catch (error) {
        console.log(error);
        throw error;
    }
}


export async function persistNewActiveCampaignMappingForResources(
    campaignMappingsForResources: any[],
    allBoundaries: any[],
    campaignNumber: any,
    userUuid: any,
    pvarIds: any[]
) {
    const setOfPvarIdsBoundaryCodesForWhichResourceMappingDataAlreadyPersisted = new Set();
    campaignMappingsForResources.forEach((campaignMapping: any) => {
        setOfPvarIdsBoundaryCodesForWhichResourceMappingDataAlreadyPersisted.add(`N${campaignMapping?.mappingIdentifier}!#!${campaignMapping?.boundaryCode}N`);
    })
    const newResourceCampaignMappingsToBePersisted: any[] = [];
    const currentTime = new Date().getTime();
    for(const pvarId of pvarIds){
        allBoundaries.forEach((boundary: any) => {
            if (!setOfPvarIdsBoundaryCodesForWhichResourceMappingDataAlreadyPersisted.has(`N${pvarId}!#!${boundary?.code}N`)) {
                const newCampaignMapping = {
                    id: uuidv4(),
                    campaignNumber: campaignNumber,
                    mappingIdentifier: pvarId,
                    mappingType: mappingTypes.resource,
                    mappingCode: null,
                    status: mappingStatus.toBeMapped,
                    boundaryCode: boundary?.code,
                    additionalDetails: {},
                    createdBy: userUuid,
                    lastModifiedBy: userUuid,
                    createdTime: currentTime,
                    lastModifiedTime: currentTime
                }
                newResourceCampaignMappingsToBePersisted.push(newCampaignMapping);
            }
        })
    }
    for(let i = 0; i < newResourceCampaignMappingsToBePersisted.length; i+=100){
        const chunk = newResourceCampaignMappingsToBePersisted.slice(i, i + 100);
        const produceMessage: any = {
            campaignMappings : chunk
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SAVE_CAMPAIGN_MAPPINGS_TOPIC
        );
    }
}



