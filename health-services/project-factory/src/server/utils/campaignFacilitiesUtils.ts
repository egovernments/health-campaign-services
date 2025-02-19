import { campaignProcessStatus, mappingStatus, mappingTypes, processNamesConstantsInOrder, processTrackStatuses, processTrackTypes, usageColumnStatus } from "../config/constants";
import { checkIfProcessIsCompleted, checkifProcessIsFailed, markProcessStatus, persistTrack } from "../utils/processTrackUtils";
import { logger } from "../utils/logger";
import { checkIfNoNeedToProceedForResource, enrichProcessedFileAndPersist, getAllFormatedDataFromDataSheet, getLocalizedName, getResourceFileIdFromCampaignDetails, updateCreateResourceId } from "../utils/campaignUtils";
import config from "../config";
import { fillDataInProcessedFacilitySheet, getExcelWorkbookFromFileURL, getLocaleFromCampaignFiles } from "../utils/excelUtils";
import { getDataFromSheetFromNormalCampaign, getLocalizedMessagesHandlerViaLocale } from "../utils/genericUtils";
import createAndSearch from "../config/createAndSearch";
import { executeQuery } from "./db";
import { v4 as uuidv4 } from "uuid";
import { produceModifiedMessages } from "../kafka/Producer";
import { createFacilitiesAndPersistFacilityId } from "../api/campaignFacilityApi";
import { getCampaignMappings } from "./campaignMappingUtils";
import { createAndUploadFileWithLocaleAndCampaign } from "../api/genericApis";

export async function createCampaignFacilities(campaignDetailsAndRequestInfo: any) {
    try {
        const { CampaignDetails, RequestInfo } = campaignDetailsAndRequestInfo;
        const isProcessAlreadyCompleted = await checkIfProcessIsCompleted(
            CampaignDetails?.campaignNumber,
            processNamesConstantsInOrder.facilityCreation
        )
        if (isProcessAlreadyCompleted) {
            logger.info("Facility Creation process already completed");
            return;
        }
        const isNoNeedToProceed = await checkIfNoNeedToProceedForResource(
            "facility",
            processNamesConstantsInOrder.facilityCreation,
            CampaignDetails,
            RequestInfo
        )
        if (isNoNeedToProceed) {
            logger.info("Facility Creation process no need to proceed");
            return;
        }
        // TODO : Remove confirmation from here
        // Function to delay execution for a specified time
        let facilityCreationStatusConfirmed = false;
        let status = "";
        const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
        const processConfirmationAttempts = parseInt(config?.values?.processConfirmationAttempts || "75") || 75;

        // Check process completion with retries
        for (let attempt = 0; attempt < processConfirmationAttempts; attempt++) {
            const isCompleted = await checkIfProcessIsCompleted(
                CampaignDetails?.campaignNumber,
                processNamesConstantsInOrder.facilityCreation
            );
            const isFailed = await checkifProcessIsFailed(
                CampaignDetails?.campaignNumber,
                processNamesConstantsInOrder.facilityCreation
            )

            if (isCompleted || isFailed) {
                logger.info(`Facility Creation confirmed successfully. Attempt ${attempt + 1} out of ${processConfirmationAttempts}`);
                facilityCreationStatusConfirmed = true;
                status = isCompleted ? campaignProcessStatus.completed : campaignProcessStatus.failed;
                break;  // Exit successfully if process is completed
            }
            else {
                logger.warn(`Facility Creation process not completed yet. Attempt ${attempt + 1} out of ${processConfirmationAttempts}.`);
            }
            await delay(20000);  // Wait for 2 seconds before retrying
        }
        if (!facilityCreationStatusConfirmed) {
            logger.error(`Facility Creation process did not complete after ${processConfirmationAttempts} attempts.`);
            throw new Error(`Facility Creation process did not complete after ${processConfirmationAttempts} attempts.`);
        }
        else {
            if (status == campaignProcessStatus.failed) {
                logger.error(`Facility Creation process failed.`);
                throw new Error(`Facility Creation process failed.`);
            }
            else await enrichProcessedFileAndPersist(campaignDetailsAndRequestInfo, "facility");
        }
    } catch (error: any) {
        console.log(error);
        await persistTrack(
            campaignDetailsAndRequestInfo?.CampaignDetails?.id,
            processTrackTypes.facilityCreation,
            processTrackStatuses.failed,
            {
                error: String(
                    error?.message +
                    (error?.description ? ` : ${error?.description}` : "") || error
                ),
            }
        );
        throw new Error(error);
    }
}

export async function getFacilityListFromCampaignDetails(campaignDetails: any) {
    const tenantId = campaignDetails?.tenantId;
    const type = "facility";
    const facilityFileId = getResourceFileIdFromCampaignDetails(campaignDetails, type);
    const localeFromFacilityFile = await getLocaleFromCampaignFiles(facilityFileId, tenantId);
    const localizationMap = await getLocalizedMessagesHandlerViaLocale(localeFromFacilityFile, tenantId);
    const dataFromSheet: any = await getDataFromSheetFromNormalCampaign(type, facilityFileId, tenantId, createAndSearch[type], createAndSearch[type]?.parseArrayConfig?.sheetName, localizationMap);
    const allFacilitiesFromDataSheet = await getAllFormatedDataFromDataSheet(type, dataFromSheet, localizationMap);
    const boundaryKey = getLocalizedName(createAndSearch?.[type]?.boundaryValidation?.column, localizationMap);
    const nameKey = getLocalizedName(createAndSearch?.[type]?.uniqueNameColumnName, localizationMap);
    const facilityNameAndBoundaryCodeMapping = dataFromSheet?.reduce((acc: Record<string, string>, item: any) => {
        if (item?.[boundaryKey] && item?.[nameKey]) acc[item?.[nameKey]] = item?.[boundaryKey];
        return acc;
    }, {});
    allFacilitiesFromDataSheet.forEach((facility: any) => {
        facility.boundaries = facilityNameAndBoundaryCodeMapping[facility.name];
    });
    return allFacilitiesFromDataSheet;
}


export async function getCampaignFacilities(
    campaignNumber: string,
    searchActiveOnly: boolean,
    facilityNames: string[] = []
) {
    return fetchCampaignFacilitiesData(campaignNumber, searchActiveOnly, facilityNames, false);
}

export async function getCampaignFacilitiesCount(
    campaignNumber: string,
    searchActiveOnly: boolean,
    facilityNames: string[] = []
) {
    return fetchCampaignFacilitiesData(campaignNumber, searchActiveOnly, facilityNames, true);
}

export async function fetchCampaignFacilitiesData(
    campaignNumber: string,
    searchActiveOnly: boolean,
    facilityNames: string[] = [],
    fetchCountOnly: boolean = false
) {
    // Determine whether to fetch count or full data
    const selectClause = fetchCountOnly ? `SELECT COUNT(*)` : `SELECT *`;
    let query = `${selectClause} FROM ${config?.DB_CONFIG.DB_CAMPAIGN_FACILITIES_TABLE_NAME} WHERE campaignnumber = $1`;
    const values: any[] = [campaignNumber]; // Initialize an array to hold query parameters

    // Filter by active status if specified
    if (searchActiveOnly) {
        query += ` AND isactive = true`;
    }
    
    // If facilityNames are provided, adjust the query
    if (facilityNames?.length > 0) {
        query += ` AND name = ANY($${values.length + 1})`;
        values.push(facilityNames);
    }

    try {
        const queryResponse = await executeQuery(query, values);

        if (fetchCountOnly) {
            // Return the count as an integer if fetchCountOnly is true
            return parseInt(queryResponse.rows[0].count, 10);
        } else {
            // Map and return the facility objects if fetching full data
            return queryResponse.rows.map((row: any) => {
                const facility: any = {
                    id: row.id,
                    campaignNumber: row.campaignnumber,
                    name: row.name,
                    facilityId: row.facilityid,
                    facilityUsage: row.facilityusage,
                    isPermanent: row.ispermanent,
                    storageCapacity: parseInt(row.storagecapacity),
                    additionalDetails: row.additionaldetails,
                    isActive: row.isactive,
                    createdBy: row.createdby,
                    lastModifiedBy: row.lastmodifiedby,
                    createdTime: parseInt(row.createdtime),
                    lastModifiedTime: parseInt(row.lastmodifiedtime)
                };

                return facility;
            });
        }
    } catch (error) {
        console.error("Error fetching campaign facilities data:", error);
        throw error;
    }
}

// export async function getAllCampaignFacilitiesWithJustFacilityId(facilityIds: any[]) {
//     const query = `
//         SELECT DISTINCT ON (facilityid) * 
//         FROM ${config?.DB_CONFIG.DB_CAMPAIGN_FACILITIES_TABLE_NAME} 
//         WHERE facilityid = ANY($1)
//         ORDER BY facilityid, lastmodifiedtime DESC
//     `;
//     const values = [facilityIds]; // Use facilityIds as the parameter

//     try {
//         const queryResponse = await executeQuery(query, values);

//         // Map and return the facility objects
//         return queryResponse.rows.map((row: any) => ({
//             id: row.id,
//             campaignNumber: row.campaignnumber,
//             facilityId: row.facilityid,
//             name: row.name,
//             facilityUsage: row.facilityusage,
//             isPermanent: row.ispermanent,
//             storageCapacity: row.storagecapacity,
//             additionalDetails: row.additionaldetails,
//             isActive: row.isactive,
//             createdBy: row.createdby,
//             lastModifiedBy: row.lastmodifiedby,
//             createdTime: parseInt(row.createdtime, 10),
//             lastModifiedTime: parseInt(row.lastmodifiedtime, 10)
//         }));
//     } catch (error) {
//         console.error("Error fetching campaign facilities by facility ids:", error);
//         throw error;
//     }
// }

export async function persistForActiveFacilities(allFacilityList: any, campaignFacilities: any, campaignNumber: string, userUuid: string) {
    await persistNewActiveFacilitiesFromFacilityList(allFacilityList, campaignFacilities, campaignNumber, userUuid);
    await updateInactiveFacilitiesToActive(allFacilityList, campaignFacilities, userUuid);
}

export async function persistNewActiveFacilitiesFromFacilityList(allFacilityList: any, campaignFacilities: any, campaignNumber: string, userUuid: string) {
    const setOfFacilityNamesFromDBForCurrentCamoaign = new Set(
        campaignFacilities?.map((facility: any) => facility.name)
    );
    const activeFacilitiesToPersist = allFacilityList.filter((facility: any) => (facility["!isActive!"] == usageColumnStatus.active) && !setOfFacilityNamesFromDBForCurrentCamoaign.has(facility["name"]));
    const currentTime = new Date().getTime();
    const formatedActiveFacilitiesToPersist = activeFacilitiesToPersist.map((facility: any) => ({
        id : uuidv4(),
        campaignNumber,
        name : facility.name,
        facilityId : facility.id || null,
        facilityUsage : facility.usage,
        isPermanent : facility.isPermanent,
        storageCapacity : facility.storageCapacity,
        additionalDetails : {},
        isActive : true,
        createdBy : userUuid,
        lastModifiedBy : userUuid,
        createdTime : currentTime,
        lastModifiedTime : currentTime
    }))
    for(let i = 0; i < formatedActiveFacilitiesToPersist.length; i += 100) {
        const chunk = formatedActiveFacilitiesToPersist.slice(i, i + 100);
        const produceMessage: any = {
            campaignFacilities: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SAVE_CAMPAIGN_FACILITIES_TOPIC
        );
    }
}

async function updateInactiveFacilitiesToActive(allFacilityList: any[], campaignFacilities: any[], userUuid: string){
    const setOfActiveFacilityNamesFromSheet= new Set(
        allFacilityList?.filter((facility: any) => (facility["!isActive!"] == usageColumnStatus.active)).map((facility: any) => facility["name"])
    );
    const inactiveFacilitiesToBeUpdated = campaignFacilities?.filter((facility: any) => !facility.isActive && setOfActiveFacilityNamesFromSheet.has(facility.name));
    const currentTime = new Date().getTime();
    const formatedInactiveFacilitiesToBeUpdated = inactiveFacilitiesToBeUpdated.map((facility: any) => ({
        ...facility,
        isActive : true,
        lastModifiedBy : userUuid,
        lastModifiedTime : currentTime
    }))
    for(let i = 0; i < formatedInactiveFacilitiesToBeUpdated.length; i += 100) {
        const chunk = formatedInactiveFacilitiesToBeUpdated.slice(i, i + 100);
        const produceMessage: any = {
            campaignFacilities: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_UPDATE_CAMPAIGN_FACILITIES_TOPIC
        );
    }
}

export async function persistForInactiveFacilities( allFacilityList: any[], campaignFacilities: any[], campaignNumber: string, userUuid: string) {
    await updateActiveFacilitiesToInActive(allFacilityList, campaignFacilities, userUuid);
    await persistNewInactiveFacilities(allFacilityList, campaignFacilities, campaignNumber, userUuid);
}

async function updateActiveFacilitiesToInActive(allFacilityList: any[], campaignFacilities: any[], userUuid: string) {
    logger.info("Updating active facilities to inactive for current chunk");
    const setOfFacilityNamesToBeMadeInactiveFromSheet = new Set(
        allFacilityList?.filter((facility: any) => (facility["!isActive!"] == usageColumnStatus.inactive)).map((facility: any) => facility["name"])
    )
    const activeFacilitiesToBeUpdatedAsInactive = campaignFacilities?.filter((facility: any) => facility.isActive && setOfFacilityNamesToBeMadeInactiveFromSheet.has(facility.name));
    const currentTime = new Date().getTime();
    const formattedActiveFacilitiesToBeUpdatedAsInactive = activeFacilitiesToBeUpdatedAsInactive.map((facility: any) => ({
        ...facility,
        isActive: false,
        lastModifiedBy: userUuid,
        lastModifiedTime: currentTime
    }));
    for (let i = 0; i < formattedActiveFacilitiesToBeUpdatedAsInactive.length; i += 100) {
        const chunk = formattedActiveFacilitiesToBeUpdatedAsInactive.slice(i, i + 100);
        const produceMessage: any = {
            campaignFacilities: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_UPDATE_CAMPAIGN_FACILITIES_TOPIC
        );
    }
    logger.info("Updating active facilities to inactive completed for current chunk");
}

async function persistNewInactiveFacilities(allFacilityList: any[], campaignFacilities: any[], campaignNumber: string, userUuid: string) {
    logger.info("Persisting new inactive facilities for current chunk");
    const setOfFacilityNamesFromDBForCurrentCamoaign = new Set(
        campaignFacilities?.map((facility: any) => facility.name)
    )
    const newInactiveFacilitiesToBePersisted = []
    for(let facility of allFacilityList) {
        if(facility["!isActive!"] == usageColumnStatus.inactive && !setOfFacilityNamesFromDBForCurrentCamoaign.has(facility["name"])) {
            const currentTime = new Date().getTime();
            const formatedFacilityToPersist = {
                id: uuidv4(),
                campaignNumber,
                name: facility.name,
                facilityId: facility.id || null,
                facilityUsage: facility.usage,
                isPermanent: facility.isPermanent,
                storageCapacity: facility.storageCapacity,
                additionalDetails: {},
                isActive: false,
                createdBy: userUuid,
                lastModifiedBy: userUuid,
                createdTime: currentTime,
                lastModifiedTime: currentTime
            }
            newInactiveFacilitiesToBePersisted.push(formatedFacilityToPersist);
        }
    }
    for(let i = 0; i < newInactiveFacilitiesToBePersisted.length; i += 100) {
        const chunk = newInactiveFacilitiesToBePersisted.slice(i, i + 100);
        const produceMessage: any = {
            campaignFacilities: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SAVE_CAMPAIGN_FACILITIES_TOPIC
        );
    }
    logger.info("Persisting new inactive facilities completed for current chunk");
}

export async function persistForActiveBoundariesFromFacilityList(    
    allFacilityList: any[], 
    campaignMappings: any[], 
    campaignNumber: string, 
    userUuid: string
) {
    await persistNewMappingsFromFacilityList(allFacilityList, campaignMappings, campaignNumber, userUuid);
    await updateMappingsFromFacilityList(allFacilityList, campaignMappings, campaignNumber, userUuid);
}

export async function persistNewMappingsFromFacilityList(allFacilityList: any[], campaignMappings: any[], campaignNumber: string, userUuid: string) {
    const currentTime = new Date().getTime();
    const activeFacilityList = allFacilityList.filter((facility: any) => facility?.["!isActive!"] == usageColumnStatus.active);
    const setOfCombinationOfFacilityIdentifierAndBoundary = new Set(campaignMappings.map((mapping: any) => `${mapping?.mappingIdentifier}#${mapping?.boundaryCode}`));

    const campaignMappingsToBePersisted = activeFacilityList.flatMap((facility: any) => {
        const boundaries = facility?.boundaries;

        // Split the jurisdictions string into an array
        const boundariesArray = boundaries?.split(",").map((boundary: string) => boundary.trim());

        // Return an array of mapping objects for each jurisdiction
        return boundariesArray?.reduce((acc: any[], boundary: string) => {
            // Skip mapping if combination already exists
            if (!setOfCombinationOfFacilityIdentifierAndBoundary.has(`${campaignNumber}!#!${facility?.name}#${boundary}`)) {
                acc.push({
                    id: uuidv4(),
                    campaignNumber: campaignNumber,
                    mappingIdentifier: `${campaignNumber}!#!${facility?.name}`,
                    mappingType: mappingTypes.facility,
                    mappingCode: null,
                    status: mappingStatus.toBeMapped,
                    boundaryCode: boundary,
                    additionalDetails: {},
                    createdBy: userUuid,
                    lastModifiedBy: userUuid,
                    createdTime: currentTime,
                    lastModifiedTime: currentTime
                });
            }
            return acc;
        }, []);
    });
    for (let i = 0; i < campaignMappingsToBePersisted.length; i += 100) {
        const chunk = campaignMappingsToBePersisted.slice(i, i + 100);
        const produceMessage: any = {
            campaignMappings: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SAVE_CAMPAIGN_MAPPINGS_TOPIC
        );
    }
}

async function updateMappingsFromFacilityList(allFacilityList: any[], campaignMappings: any[], campaignNumber: string, userUuid: string) {
    const currentTime = new Date().getTime();
    const setOfCombinationOfFacilityIdentifierAndBoundaryFromSheet = new Set(
        allFacilityList
            .filter((facility: any) => facility?.["!isActive!"] == usageColumnStatus.active)
            .flatMap((facility: any) => {
                const boundaries = facility?.boundaries;

                // Split the boundaries string into an array
                const boundariesArray = boundaries?.split(",").map((boundary: string) => boundary.trim());

                // Return an array of mapping strings for each boundary
                return boundariesArray?.map((boundary: string) => `${campaignNumber}!#!${facility?.name}#${boundary}`);
            })
    );

    const notMappedStatus = [mappingStatus.detached, mappingStatus.toBeDetached];
    const campaignMappingsTobeUpdatedForMapping = [];

    // Check and prepare campaign mappings to update
    for (const mapping of campaignMappings) {
        if (setOfCombinationOfFacilityIdentifierAndBoundaryFromSheet.has(`${mapping?.mappingIdentifier}#${mapping?.boundaryCode}`) && notMappedStatus.includes(mapping?.status)) {
            const updatedMapping = {
                ...mapping,
                status: mappingStatus.toBeMapped,
                lastModifiedBy: userUuid,
                lastModifiedTime: currentTime
            };
            campaignMappingsTobeUpdatedForMapping.push(updatedMapping);
        }
    }

    // Update the campaign mappings in chunks of 100
    for (let i = 0; i < campaignMappingsTobeUpdatedForMapping.length; i += 100) {
        const chunk = campaignMappingsTobeUpdatedForMapping.slice(i, i + 100);
        const produceMessage: any = {
            campaignMappings: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_UPDATE_CAMPAIGN_MAPPINGS_TOPIC
        );
    }
}

export async function persistForInActiveBoundariesFromFacilityList(allFacilityList: any[], campaignMappings: any[], campaignNumber: string, userUuid: string) {
    await inactivateMappingsForInactiveFacilities(allFacilityList, campaignMappings, campaignNumber, userUuid);
    await removeMappingsForInactiveBoundariesForFacilityList(allFacilityList, campaignMappings,campaignNumber, userUuid);
}

async function inactivateMappingsForInactiveFacilities(allFacilityList: any[], campaignMappings: any[], campaignNumber: string, userUuid: string) {
    const setOfCombinationOfFacilityIdentifierAndBoundaryToBeMadeInactiveBecauseOfInactivityOfFacility = new Set(
        allFacilityList
            .filter((facility: any) => facility?.["!isActive!"] == usageColumnStatus.inactive)
            .flatMap((facility: any) => {
                const boundaries = facility?.boundaries;

                // Split the jurisdictions string into an array
                const boundariesArray = boundaries?.split(",").map((boundary: string) => boundary.trim());

                // Return an array of mapping strings for each jurisdiction
                return boundariesArray?.map((boundary: string) => `${campaignNumber}!#!${facility?.name}#${boundary}`);
            })
    );

    const campaignMappingsToBeUpdatedAsInactive = [];

    // Check and prepare campaign mappings to update
    for (const mapping of campaignMappings) {
        if (setOfCombinationOfFacilityIdentifierAndBoundaryToBeMadeInactiveBecauseOfInactivityOfFacility.has(`${mapping?.mappingIdentifier}#${mapping?.boundaryCode}`)) {
            const updatedMapping = {
                ...mapping,
                status: mappingStatus.toBeDetached,
                lastModifiedBy: userUuid,
                lastModifiedTime: new Date().getTime()
            };
            campaignMappingsToBeUpdatedAsInactive.push(updatedMapping);
        }
    }

    // Update the campaign mappings in chunks of 100
    for (let i = 0; i < campaignMappingsToBeUpdatedAsInactive.length; i += 100) {
        const chunk = campaignMappingsToBeUpdatedAsInactive.slice(i, i + 100);
        const produceMessage: any = {
            campaignMappings: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_UPDATE_CAMPAIGN_MAPPINGS_TOPIC
        );
    }
}

export async function removeMappingsForInactiveBoundariesForFacilityList(allFacilityList: any[], campaignMappings: any[], campaignNumber: string, userUuid: string) {
    const setOfAllActiveMobileNumbersAndJurisdictionsFromSheet = new Set(
        allFacilityList
            .filter((facility: any) => facility?.["!isActive!"] == usageColumnStatus.active)
            .flatMap((facility: any) => {
                const boundaries = facility?.boundaries;

                // Split the boundaries string into an array
                const boundariesArray = boundaries?.split(",").map((boundary: string) => boundary.trim());

                // Return an array of mapping strings for each jurisdiction
                return boundariesArray?.map((boundary: string) => `${campaignNumber}!#!${facility?.name}#${boundary}`);
            })
    );

    const campaignMappingsToBeUpdatedAsInactiveViaBoundaryRemoval = [];

    // Check and prepare campaign mappings to update    
    for (const mapping of campaignMappings) {
        if (!setOfAllActiveMobileNumbersAndJurisdictionsFromSheet.has(`${mapping?.mappingIdentifier}#${mapping?.boundaryCode}`)) {
            const updatedMapping = {
                ...mapping,
                status: mappingStatus.toBeDetached,
                lastModifiedBy: userUuid,
                lastModifiedTime: new Date().getTime()
            };
            campaignMappingsToBeUpdatedAsInactiveViaBoundaryRemoval.push(updatedMapping);
        }
    }

    // Update the campaign mappings in chunks of 100
    for (let i = 0; i < campaignMappingsToBeUpdatedAsInactiveViaBoundaryRemoval.length; i += 100) {
        const chunk = campaignMappingsToBeUpdatedAsInactiveViaBoundaryRemoval.slice(i, i + 100);
        const produceMessage: any = {
            campaignMappings: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_UPDATE_CAMPAIGN_MAPPINGS_TOPIC
        );
    }
}

export async function persistForFacilityCreationProcess(allFacilityList: any[], campaignNumber: string, tenantId: string, userUuid: string) {
    const facilityNames = allFacilityList
        .filter((facility: any) => facility["!isActive!"] == usageColumnStatus.active)
        .map((facility: any) => facility?.name);

    for (let i = 0; i < facilityNames.length; i += 100) {
        const chunk = facilityNames.slice(i, i + 100);
        const produceMessage: any = {
            processName: processNamesConstantsInOrder.facilityCreation,
            data: {
                tenantId: tenantId,
                userUuid: userUuid,
                facilityNames: chunk
            },
            campaignNumber: campaignNumber
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SUB_PROCESS_HANDLER_TOPIC
        );
    }
}

export async function processSubFacilityCreationFromConsumer(data: any, campaignNumber: string) {
    // wait for 5 second before processing the facility creation process for each chunk
    logger.info("Waiting for 5 seconds before starting the facility creation process for each chunk");
    await new Promise((resolve) => setTimeout(resolve, 5000));
    logger.info(`Processing facility creation process for campaign number: ${campaignNumber}`);
    const { facilityNames, tenantId, userUuid } = data;

    // Fetch campaign facilities for the provided campaign number and facility names
    const campaignFacilities = await getCampaignFacilities(campaignNumber, true, facilityNames);

    // Filter facilities without facilityId
    const facilitiesToCreate = campaignFacilities?.filter((facility: any) => !facility?.facilityId);
    const facilitiesToCreateLength = facilitiesToCreate?.length;

    if (facilitiesToCreateLength > 0) {
        logger.info(`Creating ${facilitiesToCreateLength} facilities for campaign number: ${campaignNumber}`);

        // Create facilities and fetch name to facilityId mapping
        await createFacilitiesAndPersistFacilityId(facilitiesToCreate, tenantId, userUuid);
    }
    else {
        logger.info(`No Facilities to create for campaign number: ${campaignNumber}`);
    }

    // wait for 5 seconds
    logger.info(`Waiting for 5 seconds for facility creation to complete`);
    await new Promise((resolve) => setTimeout(resolve, 5000));

    await checkAndPersistFacilityCreationResult(campaignNumber);
}

async function checkAndPersistFacilityCreationResult(campaignNumber: string) {
    const maxRetries = 15;
    const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

    for (let attempt = 0; attempt < maxRetries; attempt++) {
        try {
            // Check if the process is already completed before each retry
            const isProcessAlreadyCompleted = await checkIfProcessIsCompleted(
                campaignNumber,
                processNamesConstantsInOrder.facilityCreation
            );
            const isProcessFailed = await checkifProcessIsFailed(campaignNumber, processNamesConstantsInOrder.facilityCreation)
            if (isProcessAlreadyCompleted) {
                logger.info("Facility creation process already completed.");
                return;
            }
            else if (isProcessFailed) {
                logger.warn("Facility creation process is already failed.");
                return;
            }

            const isAllActiveCampaignFacilitiesWithFacilityId = await checkIfAllActiveCampaignFacilitiesHaveFacilityId(campaignNumber);

            // If counts match, mark as completed and exit
            if (isAllActiveCampaignFacilitiesWithFacilityId) {
                await markProcessStatus(campaignNumber, processNamesConstantsInOrder.facilityCreation, campaignProcessStatus.completed);
                logger.info("Facility creation process marked as completed.");
                return;
            } else {
                logger.warn(`Attempt ${attempt + 1}/${maxRetries}: Facility creation process not completed yet. Retrying...`);
                await delay(2000); // Delay before retrying
            }
        } catch (error) {
            console.error("Error during facility creation check:", error);
            throw error; // Stop the process if an error occurs
        }
    }

    logger.warn("Failed to check the status of the facility creation process after maximum retries.");
}

async function checkIfAllActiveCampaignFacilitiesHaveFacilityId(campaignNumber: string) {
    const query = `
        SELECT 1
        FROM ${config.DB_CONFIG.DB_CAMPAIGN_FACILITIES_TABLE_NAME}
        WHERE campaignnumber = $1
          AND isactive = true
          AND (facilityid IS NULL OR facilityid = '')
        LIMIT 1;
    `;
    const values = [campaignNumber];
    const result = await executeQuery(query, values);

    // If no rows are returned, it means all active employees have userServiceUuid
    return result.rows.length === 0;
}

export async function persistCreateResourceIdForFacility(CampaignDetails: any, RequestInfo: any, fileResponse: any, resourceFileId: any, tenantId: any){
    const resourceType = "facility";
    const campaignNumber = CampaignDetails?.campaignNumber;
    const workbook = await getExcelWorkbookFromFileURL(fileResponse?.fileStoreIds?.[0]?.url);
    const locale = await getLocaleFromCampaignFiles(resourceFileId, tenantId);
    const localizationMap = await getLocalizedMessagesHandlerViaLocale(locale, tenantId);
    const facilityWorkSheet = workbook.getWorksheet(getLocalizedName(config.facility.facilityTab, localizationMap));
    if (!facilityWorkSheet) {
        throw new Error("Facility sheet not found");
    }
    const campaignFacilities = await getCampaignFacilities(campaignNumber, false);
    const campaignMappings = await getCampaignMappings(campaignNumber, mappingTypes.facility);
    fillDataInProcessedFacilitySheet(facilityWorkSheet, campaignFacilities, campaignMappings, campaignNumber);
    const responseData = await createAndUploadFileWithLocaleAndCampaign(workbook, locale, CampaignDetails?.id, tenantId);
    await updateCreateResourceId(CampaignDetails, resourceType, responseData?.[0]?.fileStoreId, RequestInfo?.userInfo?.uuid);
    logger.info(`Resource file updated for resource type: ${resourceType}`);
}
