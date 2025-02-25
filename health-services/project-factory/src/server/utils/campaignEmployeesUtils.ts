import { campaignProcessStatus, mappingStatus, mappingTypes, processNamesConstantsInOrder, processTrackStatuses, processTrackTypes, usageColumnStatus } from "../config/constants";
import { checkIfProcessIsCompleted, checkifProcessIsFailed, markProcessStatus, persistTrack } from "./processTrackUtils";
import { logger } from "./logger";
import { v4 as uuidv4 } from "uuid";
import config from "../config";
import { produceModifiedMessages } from "../kafka/Producer";
import { fillDataInProcessedUserSheet, getExcelWorkbookFromFileURL, getLocaleFromCampaignFiles } from "./excelUtils";
import { getDataFromSheetFromNormalCampaign, getLocalizedMessagesHandlerViaLocale } from "./genericUtils";
import createAndSearch from "../config/createAndSearch";
import { checkIfNoNeedToProceedForResource, createIdRequestsForEmployees, createUniqueUserNameViaIdGen, enrichProcessedFileAndPersist, getAllFormatedDataFromDataSheet, getLocalizedName, getResourceFileIdFromCampaignDetails, updateCreateResourceId } from "./campaignUtils";
import { executeQuery } from "./db";
import { generateUserPassword } from "../api/campaignApis";
import { encryptPassword } from "./encryptionUtils";
import { createEmployeesAndGetMobileNumbersAndUserServiceUuidMapping } from "../api/campaignEmployeeApi";
import { getCampaignMappings } from "./campaignMappingUtils";
import _ from "lodash";
import {  createAndUploadFileWithLocaleAndCampaign } from "../api/genericApis";

export async function createCampaignEmployees(campaignDetailsAndRequestInfo: any) {
    try {
        const { CampaignDetails, RequestInfo } = campaignDetailsAndRequestInfo;
        const isProcessAlreadyCompleted = await checkIfProcessIsCompleted(
            CampaignDetails?.campaignNumber,
            processNamesConstantsInOrder.employeeCreation
        )
        if (isProcessAlreadyCompleted) {
            logger.info("Employee Creation process already completed");
            return;
        }
        const isNoNeedToProceed = await checkIfNoNeedToProceedForResource(
              "user",
              processNamesConstantsInOrder.employeeCreation,
              CampaignDetails,
              RequestInfo
            )
        if(isNoNeedToProceed){
            logger.info("Employee Creation process no need to proceed");
            return;
        }
        // TODO : Remove confirmation from here
        // Function to delay execution for a specified time
        let employeeCreationStatusConfirmed = false;
        let status = "";
        const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
        const processConfirmationAttempts = parseInt(config?.values?.processConfirmationAttempts || "75") || 75;

        // Check process completion with retries
        for (let attempt = 0; attempt < processConfirmationAttempts; attempt++) {
            const isCompleted = await checkIfProcessIsCompleted(
                CampaignDetails?.campaignNumber,
                processNamesConstantsInOrder.employeeCreation
            );
            const isFailed = await checkifProcessIsFailed(
                CampaignDetails?.campaignNumber,
                processNamesConstantsInOrder.employeeCreation
            )

            if (isCompleted || isFailed) {
                logger.info(`Employee Creation confirmed successfully. Attempt ${attempt + 1} out of ${processConfirmationAttempts}`);
                employeeCreationStatusConfirmed = true;
                status = isCompleted ? campaignProcessStatus.completed : campaignProcessStatus.failed;
                break;  // Exit successfully if process is completed
            }
            else {
                logger.warn(`Employee Creation process not completed yet. Attempt ${attempt + 1} out of ${processConfirmationAttempts}.`);
            }
            await delay(20000);  // Wait for 2 seconds before retrying
        }
        if (!employeeCreationStatusConfirmed) {
            logger.error(`Employee Creation process did not complete after ${processConfirmationAttempts} attempts.`);
            throw new Error(`Employee Creation process did not complete after ${processConfirmationAttempts} attempts.`);
        }
        else {
            if (status == campaignProcessStatus.failed) {
                logger.error(`Employee Creation process failed.`);
                throw new Error(`Employee Creation process failed.`);
            }
            else await enrichProcessedFileAndPersist(campaignDetailsAndRequestInfo, "user");
        }
    } catch (error: any) {
        console.log(error);
        await persistTrack(
            campaignDetailsAndRequestInfo?.CampaignDetails?.id,
            processTrackTypes.staffCreation,
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

export async function getEmployeeListForCampaignDetails(campaignDetails: any) {
    logger.info("GETTING EMPLOYEE LIST FOR CAMPAIGN DETAILS");
    const tenantId = campaignDetails?.tenantId;
    const type = "user";
    const employeeFileId = getResourceFileIdFromCampaignDetails(campaignDetails, type);
    const localeFromEmployeeFile = await getLocaleFromCampaignFiles(employeeFileId, tenantId);
    logger.info("LOCALE FROM EMPLOYEE FILE : " + localeFromEmployeeFile);
    const localizationMap = await getLocalizedMessagesHandlerViaLocale(localeFromEmployeeFile, tenantId);
    const dataFromSheet: any = await getDataFromSheetFromNormalCampaign(type, employeeFileId, tenantId, createAndSearch[type], createAndSearch[type]?.parseArrayConfig?.sheetName, localizationMap);
    const allEmployessFromDataSheet = await getAllFormatedDataFromDataSheet(type, dataFromSheet, localizationMap);
    allEmployessFromDataSheet.forEach((employee: any) => {
        employee.jurisdictions = Array.from(
            new Set((employee?.jurisdictions || "").split(",").map((j:any) => j.trim()))
        ).join(",");
    })
    logger.info("EMPLOYEE LIST FOR CAMPAIGN DETAILS FETCHED");
    return allEmployessFromDataSheet;
}

export async function getCampaignEmployees(
    campaignNumber: string,
    searchActiveOnly: boolean,
    mobileNumbers: string[] = [],
) {
    return fetchCampaignEmployeesData(campaignNumber, searchActiveOnly, mobileNumbers, false);
}

export async function getCampaignEmployeesCount(
    campaignNumber: string,
    searchActiveOnly: boolean,
    mobileNumbers: string[] = []
) {
    return fetchCampaignEmployeesData(campaignNumber, searchActiveOnly, mobileNumbers, true);
}


export async function fetchCampaignEmployeesData(
    campaignNumber: string,
    searchActiveOnly: boolean,
    mobileNumbers: string[] = [],
    fetchCountOnly: boolean = false
) {
    // Determine whether to fetch count or full data
    const selectClause = fetchCountOnly ? `SELECT COUNT(*)` : `SELECT *`;
    let query = `${selectClause} FROM ${config?.DB_CONFIG.DB_CAMPAIGN_EMPLOYEES_TABLE_NAME} WHERE campaignnumber = $1`;
    const values: any[] = [campaignNumber]; // Initialize an array to hold query parameters

    // Filter by active status if specified
    if (searchActiveOnly) {
        query += ` AND isactive = true`;
    }

    // If mobileNumbers are provided, adjust the query
    if (mobileNumbers?.length > 0) {
        query += ` AND mobilenumber = ANY($${values.length + 1})`;
        values.push(mobileNumbers);
    }

    try {
        const queryResponse = await executeQuery(query, values);

        if (fetchCountOnly) {
            // Return the count as an integer if fetchCountOnly is true
            return parseInt(queryResponse.rows[0].count, 10);
        } else {
            // Map and return the employee objects if fetching full data
            return queryResponse.rows.map((row: any) => {
                const employee : any = {
                    id: row.id,
                    campaignNumber: row.campaignnumber,
                    mobileNumber: row.mobilenumber,
                    name: row.name,
                    role: row.role,
                    userServiceUuid: row.userserviceuuid,
                    employeeType: row.employeetype,
                    userName: row.username,
                    tokenString: row.tokenstring,
                    additionalDetails: row.additionaldetails,
                    isActive: row.isactive,
                    createdBy: row.createdby,
                    lastModifiedBy: row.lastmodifiedby,
                    createdTime: parseInt(row.createdtime),
                    lastModifiedTime: parseInt(row.lastmodifiedtime)
                };
                return employee;
            });
        }
    } catch (error) {
        console.error("Error fetching campaign employees data:", error);
        throw error;
    }
}

export async function persistForActiveEmployees(
    employeesFromSheet: any[],
    campaignEmployees: any[],
    campaignNumber: string,
    userUuid: string,
    mobileNumbersAndCampaignEmployeeMapping: any
) {
    logger.info("PERSISTING FOR ACTIVE EMPLOYEES STARTED");
    await persistNewActiveEmployees(employeesFromSheet, campaignEmployees, campaignNumber, userUuid, mobileNumbersAndCampaignEmployeeMapping);
    await updateInactiveEmployeesToActive(employeesFromSheet, campaignEmployees, userUuid);
    logger.info("PERSISTING FOR ACTIVE EMPLOYEES ENDED");
}


async function persistNewActiveEmployees(
    employeesFromSheet: any[],
    campaignEmployees: any[],
    campaignNumber: string,
    userUuid: string,
    mobileNumbersAndCampaignEmployeeMapping: any
) {
    const setOfCampaignEmployeesMobileNumbers = new Set(campaignEmployees.map((employee: any) => employee?.mobileNumber));

    const activeEmployeesFromSheet = employeesFromSheet.filter(
        (employee: any) => employee?.["!isActive!"] == usageColumnStatus.active
    );

    const employeesToBePersisted = activeEmployeesFromSheet.filter(
        (employee: any) =>
            !setOfCampaignEmployeesMobileNumbers.has(employee?.user?.mobileNumber)
    );

    const currentTime = new Date().getTime();

    const employeesToBePersistedFinal = employeesToBePersisted.map((employee: any) => ({
        id: uuidv4(),
        mobileNumber: employee?.user?.mobileNumber,
        name: employee?.user?.name,
        role: employee?.user?.roles,
        campaignNumber: campaignNumber,
        isActive: true,
        userServiceUuid: mobileNumbersAndCampaignEmployeeMapping?.[employee?.user?.mobileNumber]?.userServiceUuid || null,
        userName: mobileNumbersAndCampaignEmployeeMapping?.[employee?.user?.mobileNumber]?.userName || null,
        employeeType: employee?.employeeType == "PERMANENT" ? "Permanent" : "Temporary",
        tokenString: mobileNumbersAndCampaignEmployeeMapping?.[employee?.user?.mobileNumber]?.tokenString || null,
        additionalDetails: {},
        createdBy: userUuid,
        lastModifiedBy: userUuid,
        createdTime: currentTime,
        lastModifiedTime: currentTime,
    }));

    for (let i = 0; i < employeesToBePersistedFinal.length; i += 100) {
        const chunk = employeesToBePersistedFinal.slice(i, i + 100);
        const produceMessage: any = {
            campaignEmployees: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SAVE_CAMPAIGN_EMPLOYEES_TOPIC
        );

    }
}

async function updateInactiveEmployeesToActive(
    employeesFromSheet: any[],
    campaignEmployees: any[],
    userUuid: string
) {
    const setOfCampaignInactiveEmployeesMobileNumbers = new Set(
        campaignEmployees
            .filter((employee: any) => employee?.isActive === false)
            .map((employee: any) => `N${employee?.mobileNumber.toString()}N`)
    );

    const setOfEmployeesToBeMadeActive = new Set(
        employeesFromSheet.filter((employee: any) =>
            setOfCampaignInactiveEmployeesMobileNumbers.has(`N${employee?.user?.mobileNumber.toString()}N`) && employee?.["!isActive!"] == usageColumnStatus.active
        ).map((employee: any) => `N${employee?.user?.mobileNumber.toString()}N`)
    );

    const employessToBeUpdatedAsActive = campaignEmployees.filter((employee: any) =>
        setOfEmployeesToBeMadeActive.has(`N${employee?.mobileNumber.toString()}N`)
    );

    const currentTime = new Date().getTime();

    employessToBeUpdatedAsActive.forEach((employee: any) => {
        employee.isActive = true;
        employee.lastModifiedBy = userUuid;
        employee.lastModifiedTime = currentTime;
    });

    for (let i = 0; i < employessToBeUpdatedAsActive.length; i += 100) {
        const chunk = employessToBeUpdatedAsActive.slice(i, i + 100);
        const produceMessage: any = {
            campaignEmployees: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_UPDATE_CAMPAIGN_EMPLOYEES_TOPIC
        );
    }
}



export async function persistForInactiveEmployees(employeesFromSheet: any[], campaignEmployees: any[], mobileNumbersAndCampaignEmployeeMapping: any, campaignNumber: string, userUuid: string) {
    logger.info("PERSISTING FOR INACTIVE EMPLOYEES STARTED");
    await updateActiveEmployeesToInactive(employeesFromSheet, campaignEmployees, userUuid);
    await persistNewInactiveEmployees(employeesFromSheet, campaignEmployees, mobileNumbersAndCampaignEmployeeMapping, campaignNumber, userUuid);
    logger.info("PERSISTING FOR INACTIVE EMPLOYEES ENDED");
}

async function updateActiveEmployeesToInactive(employeesFromSheet: any[], campaignEmployees: any[], userUuid: string){
    logger.info(`Updating active employees to inactive as per sheet...`);
    const setOfSheetInactiveEmployeesMobileNumbers = new Set(employeesFromSheet.filter((employee: any) => employee?.["!isActive!"] == usageColumnStatus.inactive).map((sheetEmployee: any) => sheetEmployee?.user?.mobileNumber));
    const campaignEmployeesToBeUpdatedAsInactive = [];
    for (const employee of campaignEmployees) {
        if (setOfSheetInactiveEmployeesMobileNumbers.has(employee?.mobileNumber) && employee?.isActive === true) {
            campaignEmployeesToBeUpdatedAsInactive.push(employee);
        }
    }
    const currentTime = new Date().getTime();
    campaignEmployeesToBeUpdatedAsInactive.forEach((employee: any) => {
        employee.isActive = false;
        employee.lastModifiedBy = userUuid;
        employee.lastModifiedTime = currentTime;
    })

    for (let i = 0; i < campaignEmployeesToBeUpdatedAsInactive.length; i += 100) {
        const chunk = campaignEmployeesToBeUpdatedAsInactive.slice(i, i + 100);
        const produceMessage: any = {
            campaignEmployees: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_UPDATE_CAMPAIGN_EMPLOYEES_TOPIC
        );
    }
    logger.info(`Finished updating active employees to inactive as per sheet...`);
}

async function persistNewInactiveEmployees(employeesFromSheet: any[], campaignEmployees: any[], mobileNumbersAndCampaignEmployeeMapping: any, campaignNumber: string, userUuid: string) {
    logger.info(`Persisting new inactive employees for campaign number: ${campaignNumber}`);
    const setOfCampaignEmployeesMobileNumbers = new Set(campaignEmployees.map((employee: any) => employee?.mobileNumber));
    const campaignEmployeesToBePersistedAsInactive = [];
    for(const employee of employeesFromSheet){
        if(!setOfCampaignEmployeesMobileNumbers.has(employee?.user?.mobileNumber) && employee?.["!isActive!"] == usageColumnStatus.inactive){
            campaignEmployeesToBePersistedAsInactive.push(employee);
        }
    }

    const currentTime = new Date().getTime();
    const formattedcampaignEmployeesToBePersistedAsInactive = campaignEmployeesToBePersistedAsInactive.map((employee: any) => {
        return {
            id: uuidv4(),
            mobileNumber: employee?.user?.mobileNumber,
            name: employee?.user?.name,
            role: employee?.user?.roles,
            campaignNumber: campaignNumber,
            isActive: false,
            userServiceUuid: mobileNumbersAndCampaignEmployeeMapping?.[employee?.user?.mobileNumber]?.userServiceUuid || null,
            userName: mobileNumbersAndCampaignEmployeeMapping?.[employee?.user?.mobileNumber]?.userName || null,
            employeeType: employee?.employeeType == "PERMANENT" ? "Permanent" : "Temporary",
            tokenString: mobileNumbersAndCampaignEmployeeMapping?.[employee?.user?.mobileNumber]?.tokenString || null,
            additionalDetails: {},
            createdBy: userUuid,
            lastModifiedBy: userUuid,
            createdTime: currentTime,
            lastModifiedTime: currentTime,
        }
    })

    for (let i = 0; i < formattedcampaignEmployeesToBePersistedAsInactive.length; i += 100){
        const chunk = formattedcampaignEmployeesToBePersistedAsInactive.slice(i, i + 100);
        const produceMessage: any = {
            campaignEmployees: chunk,
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SAVE_CAMPAIGN_EMPLOYEES_TOPIC
        );
    }
    logger.info(`Persisted new inactive employees for campaign number: ${campaignNumber}`);
}

export async function persistForActiveBoundariesFromEmployeeList(employeeList: any[], campaignMappings: any[], campaignNumber: string, userUuid: string) {
    logger.info("PERSISTING FOR ACTIVE BOUNDARIES FROM EMPLOYEE LIST STARTED");
    await persistNewMappingsFromEmployeeList(employeeList, campaignMappings, campaignNumber, userUuid);
    await updateMappingsFromEmployeeList(employeeList, campaignMappings, userUuid);
    logger.info("PERSISTING FOR ACTIVE BOUNDARIES FROM EMPLOYEE LIST ENDED");
}

export async function persistNewMappingsFromEmployeeList(employeeList: any[], campaignMappings: any[], campaignNumber: string, userUuid: string) {
    const currentTime = new Date().getTime();
    const activeEmployeeList = employeeList.filter((employee: any) => employee?.["!isActive!"] == usageColumnStatus.active);
    const setOfCombinationOfMobileNumberAndJurisdiction = new Set(campaignMappings.map((mapping: any) => `${mapping?.mappingIdentifier}#${mapping?.boundaryCode}`));

    const campaignMappingsToBePersisted = activeEmployeeList.flatMap((employee: any) => {
        const jurisdictions = employee?.jurisdictions;

        // Split the jurisdictions string into an array
        const jurisdictionsArray = jurisdictions?.split(",").map((jurisdiction: string) => jurisdiction.trim());

        // Return an array of mapping objects for each jurisdiction
        return jurisdictionsArray?.reduce((acc: any[], jurisdiction: string) => {
            // Skip mapping if combination already exists
            if (!setOfCombinationOfMobileNumberAndJurisdiction.has(`${employee?.user?.mobileNumber}#${jurisdiction}`)) {
                acc.push({
                    id: uuidv4(),
                    campaignNumber: campaignNumber,
                    mappingIdentifier: employee?.user?.mobileNumber,
                    mappingType: mappingTypes.staff,
                    mappingCode: null,
                    status: mappingStatus.toBeMapped,
                    boundaryCode: jurisdiction,
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

    // Persist the new campaign mappings in chunks of 100
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


export async function updateMappingsFromEmployeeList(employeeList: any[], campaignMappings: any[], userUuid: string) {
    const currentTime = new Date().getTime();
    const setOfCombinationOfMobileNumberAndJurisdictionFromSheet = new Set(
        employeeList
            .filter((employee: any) => employee?.["!isActive!"] == usageColumnStatus.active)
            .flatMap((employee: any) => {
                const jurisdictions = employee?.jurisdictions;

                // Split the jurisdictions string into an array
                const jurisdictionsArray = jurisdictions?.split(",").map((jurisdiction: string) => jurisdiction.trim());

                // Return an array of mapping strings for each jurisdiction
                return jurisdictionsArray?.map((jurisdiction: string) => `N${employee?.user?.mobileNumber}#${jurisdiction}N`);
            })
    );

    const notMappedStatus = [mappingStatus.detached, mappingStatus.toBeDetached];
    const campaignMappingsTobeUpdatedForMapping = [];

    // Check and prepare campaign mappings to update
    for (const mapping of campaignMappings) {
        if (setOfCombinationOfMobileNumberAndJurisdictionFromSheet.has(`N${mapping?.mappingIdentifier}#${mapping?.boundaryCode}N`) && notMappedStatus.includes(mapping?.status)) {
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

export async function persistForInActiveBoundariesFromEmployeeList(employeeList: any[], campaignMappings: any[], campaignNumber: string, userUuid: string) {
    logger.info("PERSISTING FOR INACTIVE BOUNDARIES FROM EMPLOYEE LIST STARTED");
    await inactivateMappingsForInactiveEmployees(employeeList, campaignMappings, userUuid);
    await removeMappingsForInactiveBoundariesForEmployeeList(employeeList, campaignMappings, userUuid);
    logger.info("PERSISTING FOR INACTIVE BOUNDARIES FROM EMPLOYEE LIST ENDED");
}

export async function inactivateMappingsForInactiveEmployees(employeeList: any[], campaignMappings: any[], userUuid: string) {
    const setOfCombinationOfMobileNumberAndJurisdictionToBeMadeInactiveBecauseOfInactivityOfEmployee = new Set(
        employeeList
            .filter((employee: any) => employee?.["!isActive!"] == usageColumnStatus.inactive)
            .flatMap((employee: any) => {
                const jurisdictions = employee?.jurisdictions;

                // Split the jurisdictions string into an array
                const jurisdictionsArray = jurisdictions?.split(",").map((jurisdiction: string) => jurisdiction.trim());

                // Return an array of mapping strings for each jurisdiction
                return jurisdictionsArray?.map((jurisdiction: string) => `N${employee?.user?.mobileNumber}#${jurisdiction}N`);
            })
    );

    const campaignMappingsToBeUpdatedAsInactive = [];

    // Check and prepare campaign mappings to update
    for (const mapping of campaignMappings) {
        if (setOfCombinationOfMobileNumberAndJurisdictionToBeMadeInactiveBecauseOfInactivityOfEmployee.has(`N${mapping?.mappingIdentifier}#${mapping?.boundaryCode}N`)) {
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

export async function removeMappingsForInactiveBoundariesForEmployeeList(employeeList: any[], campaignMappings: any[], userUuid: string) {
    const setOfAllActiveMobileNumbersAndJurisdictionsFromSheet = new Set(
        employeeList
            .filter((employee: any) => employee?.["!isActive!"] == usageColumnStatus.active)
            .flatMap((employee: any) => {
                const jurisdictions = employee?.jurisdictions;

                // Split the jurisdictions string into an array
                const jurisdictionsArray = jurisdictions?.split(",").map((jurisdiction: string) => jurisdiction.trim());

                // Return an array of mapping strings for each jurisdiction
                return jurisdictionsArray?.map((jurisdiction: string) => `N${employee?.user?.mobileNumber}#${jurisdiction}N`);
            })
    );

    const campaignMappingsToBeUpdatedAsInactiveViaBoundaryRemoval = [];

    // Check and prepare campaign mappings to update    
    for (const mapping of campaignMappings) {
        if (!setOfAllActiveMobileNumbersAndJurisdictionsFromSheet.has(`N${mapping?.mappingIdentifier}#${mapping?.boundaryCode}N`)) {
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

export async function persistForEmployeeCreationProcess(employeeList: any[], campaignNumber: string, tenantId: string, userUuid: string, hierarchyType: string, rootBoundaryCode: string, rootBoundaryType: string) {
    const mobileNumbers = employeeList
        .filter((employee: any) => employee["!isActive!"] == usageColumnStatus.active)
        .map((employee: any) => employee?.user?.mobileNumber);

    for (let i = 0; i < mobileNumbers.length; i += 100) {
        const chunk = mobileNumbers.slice(i, i + 100);
        const produceMessage: any = {
            processName: processNamesConstantsInOrder.employeeCreation,
            data: {
                tenantId: tenantId,
                userUuid: userUuid,
                mobileNumbers: chunk,
                hierarchyType: hierarchyType,
                rootBoundaryCode: rootBoundaryCode,
                rootBoundaryType: rootBoundaryType
            },
            campaignNumber: campaignNumber
        };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_SUB_PROCESS_HANDLER_TOPIC
        );
    }
}

export async function processSubEmployeeCreationFromConsumer(data: any, campaignNumber: string) {
    // wait for 5 second before processing the employee creation process for each chunk
    logger.info("Waiting for 5 seconds before starting the employee creation process for each chunk");
    await new Promise((resolve) => setTimeout(resolve, 5000));
    logger.info(`Processing employee creation process for campaign number: ${campaignNumber}`);
    const { mobileNumbers, tenantId, hierarchyType, rootBoundaryCode, rootBoundaryType, userUuid } = data;

    // Fetch campaign employees for the provided campaign number and mobile numbers
    const campaignEmployees =   await getCampaignEmployees(campaignNumber, true, mobileNumbers);

    // Filter employees without userServiceUuid
    const employeesToCreate = campaignEmployees?.filter((employee: any) => !employee?.userServiceUuid);
    const employeesToCreateLength = employeesToCreate?.length;

    if (employeesToCreateLength > 0) {
        logger.info(`Creating ${employeesToCreateLength} employees for campaign number: ${campaignNumber}`);
        // Generate employee creation body
        const employeesCreateBody = await getEmployeeCreateBody(
            employeesToCreate,
            tenantId,
            hierarchyType,
            rootBoundaryCode,
            rootBoundaryType
        );

        // Create employees and fetch mobile number to userServiceUuid mapping
        const mobileNumbersAndUserServiceUuidMapping = await createEmployeesAndGetMobileNumbersAndUserServiceUuidMapping(employeesCreateBody);

        // Generate encrypted passwords and update employees
        const mobileNumberAndEncryptedPasswordMappings: any = {};
        const currentTime = new Date().getTime();
        employeesToCreate.forEach((employee: any, index: number) => {
            const mobileNumber = employeesCreateBody[index]?.user?.mobileNumber;
            const encryptedPassword = encryptPassword(employeesCreateBody[index]?.user?.password);
            mobileNumberAndEncryptedPasswordMappings[mobileNumber] = encryptedPassword;
            employee.userName = employeesCreateBody[index]?.code;
            employee.userServiceUuid = mobileNumbersAndUserServiceUuidMapping[mobileNumber];
            employee.tokenString = encryptedPassword;
            employee.lastModifiedTime = currentTime;
            employee.lastModifiedBy = userUuid;
        });

        // Produce updated campaign employees message
        const produceMessage = { campaignEmployees: employeesToCreate };
        await produceModifiedMessages(
            produceMessage,
            config?.kafka?.KAFKA_UPDATE_CAMPAIGN_EMPLOYEES_TOPIC
        );
    }
    else{
        logger.info(`No employees to create for campaign number: ${campaignNumber}`);
    }

    // wait for 5 seconds
    logger.info(`Waiting for 5 seconds for employee creation to complete`);
    await new Promise((resolve) => setTimeout(resolve, 5000));

    await checkAndPersistEmployeeCreationResult(campaignNumber);
}


export async function getEmployeeCreateBody(employees: any[], tenantId: string, hierarchyType: string, rootBoundaryCode: string, rootBoundaryType: string) {
    if (!employees?.length) return []; // Return early if no employees

    const idRequests = createIdRequestsForEmployees(employees.length, tenantId);
    const result = await createUniqueUserNameViaIdGen(idRequests);

    return employees.map((employee: any, index: number) => {
        const generatedPassword = config?.user?.userPasswordAutoGenerate === "true"
            ? generateUserPassword()
            : config?.user?.userDefaultPassword;

        return {
            code: result?.idResponses?.[index]?.id,
            employeeType: employee?.employeeType == "Permanent" ? "PERMANENT" : "TEMPORARY",
            tenantId,
            jurisdictions: [{
                hierarchy: hierarchyType,
                boundary: rootBoundaryCode,
                boundaryType: rootBoundaryType,
                tenantId
            }],
            user: {
                password: generatedPassword,
                name: employee?.name,
                mobileNumber: employee?.mobileNumber,
                dob: 0,
                tenantId,
                type: "EMPLOYEE",
                roles: [{
                    name: employee?.role,
                    code: employee?.role?.toUpperCase()?.split(" ").join("_"),
                    tenantId
                }]
            }
        };
    });
}

async function checkAndPersistEmployeeCreationResult(campaignNumber: string) {
    const maxRetries = 15;
    const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

    for (let attempt = 0; attempt < maxRetries; attempt++) {
        try {
            // Check if the process is already completed before each retry
            const isProcessAlreadyCompleted = await checkIfProcessIsCompleted(
                campaignNumber,
                processNamesConstantsInOrder.employeeCreation
            );
            const isProcessFailed = await checkifProcessIsFailed(campaignNumber, processNamesConstantsInOrder.employeeCreation)
            if (isProcessAlreadyCompleted) {
                logger.info("Employee creation Process already completed.");
                return;
            }
            else if (isProcessFailed) {
                logger.warn("Employee creation Process is already failed.");
                return;
            }

            const isAllActiveCampaignEmployeesWithUserServiceUuid = await checkIfAllActiveCampaignEmployeesHaveUserServiceUuid(campaignNumber);

            // If counts match, mark as completed and exit
            if (isAllActiveCampaignEmployeesWithUserServiceUuid) {
                await markProcessStatus(campaignNumber, processNamesConstantsInOrder.employeeCreation, campaignProcessStatus.completed);
                logger.info("Employee creation process marked as completed.");
                return;
            } else {
                logger.warn(`Attempt ${attempt + 1}/${maxRetries}: Employee creation process not completed yet. Retrying...`);
                await delay(2000); // Delay before retrying
            }
        } catch (error) {
            console.error("Error during employee creation check:", error);
            throw error; // Stop the process if an error occurs
        }
    }

    logger.warn("Failed to check the status of the employee creation process after maximum retries.");
}

async function checkIfAllActiveCampaignEmployeesHaveUserServiceUuid(campaignNumber: string): Promise<boolean> {
    const query = `
        SELECT 1
        FROM ${config.DB_CONFIG.DB_CAMPAIGN_EMPLOYEES_TABLE_NAME}
        WHERE campaignnumber = $1
          AND isactive = true
          AND (userserviceuuid IS NULL OR userserviceuuid = '')
        LIMIT 1;
    `;
    const values = [campaignNumber];
    const result = await executeQuery(query, values);

    // If no rows are returned, it means all active employees have userServiceUuid
    return result.rows.length === 0;
}

export async function getAllCampaignEmployeesWithJustMobileNumbers(mobileNumbers: any[]) {
    const query = `
        SELECT DISTINCT ON (mobilenumber) * 
        FROM ${config?.DB_CONFIG.DB_CAMPAIGN_EMPLOYEES_TABLE_NAME} 
        WHERE mobilenumber = ANY($1)
          AND userserviceuuid IS NOT NULL  -- Only include rows with a valid userServiceUuid
        ORDER BY mobilenumber, lastmodifiedtime DESC
    `;
    const values = [mobileNumbers]; // Use mobileNumbers as the parameter

    try {
        const queryResponse = await executeQuery(query, values);

        // Map and return the employee objects
        return queryResponse.rows.map((row: any) => ({
            id: row.id,
            campaignNumber: row.campaignnumber,
            mobileNumber: row.mobilenumber,
            name: row.name,
            role: row.role,
            userServiceUuid: row.userserviceuuid,  // Only included if it exists
            employeeType: row.employeetype,
            userName: row.username,
            additionalDetails: row.additionaldetails,
            tokenString: row.tokenstring,
            isActive: row.isactive,
            createdBy: row.createdby,
            lastModifiedBy: row.lastmodifiedby,
            createdTime: parseInt(row.createdtime, 10),
            lastModifiedTime: parseInt(row.lastmodifiedtime, 10)
        }));
    } catch (error) {
        console.error("Error fetching employees by mobile numbers:", error);
        throw error;
    }
}

export function getMobileNumbersAndCampaignEmployeeMappingFromCampaignEmployees(campaignEmployees: any[]) {
    const mobileNumbersAndCampaignEmployeeMapping: any = {};
    for (const employee of campaignEmployees) {
        if(!employee?.userServiceUuid) continue;
        mobileNumbersAndCampaignEmployeeMapping[employee?.mobileNumber] = employee;
    }
    return mobileNumbersAndCampaignEmployeeMapping;
}

export async function persistCreateResourceIdForUser(CampaignDetails: any, RequestInfo: any, fileResponse: any, resourceFileId: any, tenantId: any) {
    const resourceType = "user";
    const workbook = await getExcelWorkbookFromFileURL(fileResponse?.fileStoreIds?.[0]?.url);
    const locale = await getLocaleFromCampaignFiles(resourceFileId, tenantId);
    logger.info("LOCALE FROM EMPLOYEE FILE : " + locale);
    const localizationMap = await getLocalizedMessagesHandlerViaLocale(locale, tenantId);
    const userWorkSheet = workbook.getWorksheet(getLocalizedName(config.user.userTab, localizationMap));
    if (!userWorkSheet) {
        throw new Error("User sheet not found");
    }
    const campaignEmployees = await getCampaignEmployees(CampaignDetails?.campaignNumber, false);
    const campaignMappings = await getCampaignMappings(CampaignDetails?.campaignNumber, mappingTypes.staff);
    fillDataInProcessedUserSheet(userWorkSheet, campaignEmployees, campaignMappings);
    const responseData = await createAndUploadFileWithLocaleAndCampaign(workbook, locale, CampaignDetails?.id, tenantId);
    await updateCreateResourceId(CampaignDetails, resourceType, responseData?.[0]?.fileStoreId, RequestInfo?.userInfo?.uuid);
    logger.info(`Resource file updated for resource type: ${resourceType}`);
}

