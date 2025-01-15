import config from '../config';
import { produceModifiedMessages } from '../kafka/Producer';
import { v4 as uuidv4 } from 'uuid';
import { executeQuery } from './db';
import { persistForProjectProcess } from './targetUtils';
import { createProjectsAndGetCreatedProjects, getProjectsCountsWithProjectIds, getProjectsWithProjectIds, updateProjects } from '../api/projectApis';
import { checkIfProcessIsCompleted, markProcessStatus, searchProjectCampaignResourcData } from './campaignUtils';
import { enrichProjectDetailsFromCampaignDetails } from './transforms/projectTypeUtils';
import { campaignProcessStatus, processNamesConstants } from '../config/constants';
import { logger } from './logger';


export async function persistCampaignProject(project: any, campaignDetails: any, requestInfo: any, campaignProjectId?: string) {
    const currentTime = new Date().getTime()
    const currentUserUuid = requestInfo?.userInfo?.uuid;
    const campainProject: any = {
        id: campaignProjectId || uuidv4(),
        projectId: project.id,
        campaignNumber: campaignDetails.campaignNumber,
        boundaryCode: project?.address?.boundary,
        additionalDetails: {
            targets: project?.targets?.map((target: any) => {
                return {
                    beneficiaryType: target?.beneficiaryType,
                    targetNo: target?.targetNo,
                }
            })
        },
        isActive: true,
        createdBy: currentUserUuid,
        lastModifiedBy: currentUserUuid,
        createdTime: currentTime,
        lastModifiedTime: currentTime
    }
    const produceMessage: any = {
        campaignProject: [campainProject]
    }
    await produceModifiedMessages(produceMessage, campaignProjectId ? config?.kafka?.KAFKA_UPDATE_CAMPAIGN_PROJECT : config?.kafka?.KAFKA_SAVE_CAMPAIGN_PROJECT);
}

export async function updateCampaignProjects(campaignProjects: any[]) {
    const chunkSize = 100;

    // Process campaignProjects in chunks of 100
    for (let i = 0; i < campaignProjects.length; i += chunkSize) {
        const produceMessage: any = {
            campaignProjects: campaignProjects.slice(i, i + chunkSize),
        };
        await produceModifiedMessages(produceMessage, config?.kafka?.KAFKA_UPDATE_CAMPAIGN_PROJECT);
    }
}

export async function addCampaignProjects(campaignProjects: any[]) {
    const chunkSize = 100;

    // Process campaignProjects in chunks of 100
    for (let i = 0; i < campaignProjects.length; i += chunkSize) {
        const produceMessage: any = {
            campaignProjects: campaignProjects.slice(i, i + chunkSize),
        };
        await produceModifiedMessages(produceMessage, config?.kafka?.KAFKA_SAVE_CAMPAIGN_PROJECT);
        // wait for 5 seconds between each chunk
        await new Promise(resolve => setTimeout(resolve, 5000));
    }
}

export async function getBoundariesCampaignProjectsMapping(campaignNumber: string) {
    const campaignProjects: any[] = await getCampaignProjects(campaignNumber, true);
    return campaignProjects.reduce((acc: any, curr: any) => {
        acc[curr.boundaryCode] = curr;
        return acc;
    }, {});
}

async function fetchCampaignProjectsData(
    campaignNumber: string,
    searchActiveOnly: boolean,
    boundaryCodes: string[] = [],
    parentBoundaryCodes: string[] = [],
    fetchCountOnly: boolean = false
) {
    // Determine whether to fetch count or full data
    const selectClause = fetchCountOnly ? `SELECT COUNT(*)` : `SELECT *`;
    let query = `${selectClause} FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROJECTS_TABLE_NAME} WHERE campaignnumber = $1`;
    const values: any[] = [campaignNumber];  // Initialize an array to hold query parameters

    // If boundaryCodes are provided, adjust the query
    if (boundaryCodes.length > 0) {
        query += ` AND boundarycode = ANY($${values.length + 1})`;
        values.push(boundaryCodes);
    }

    // If parentBoundaryCodes are provided, adjust the query
    if (parentBoundaryCodes.length > 0) {
        query += ` AND parentboundarycode = ANY($${values.length + 1})`;
        values.push(parentBoundaryCodes);
    }

    // Filter by active status if specified
    if (searchActiveOnly) {
        query += ` AND isactive = true`;
    }

    try {
        const queryResponse = await executeQuery(query, values);

        if (fetchCountOnly) {
            // Return the count as an integer if fetchCountOnly is true
            return parseInt(queryResponse.rows[0].count, 10);
        } else {
            // Map and return the project objects if fetching full data
            return queryResponse.rows.map((row: any) => ({
                id: row.id,
                projectId: row.projectid,
                campaignNumber: row.campaignnumber,
                boundaryCode: row.boundarycode,
                parentBoundaryCode: row.parentboundarycode,
                boundaryType: row.boundarytype,
                additionalDetails: row.additionaldetails,
                isActive: row.isactive,
                createdBy: row.createdby,
                lastModifiedBy: row.lastmodifiedby,
                createdTime: parseInt(row.createdtime),
                lastModifiedTime: parseInt(row.lastmodifiedtime)
            }));
        }
    } catch (error) {
        console.error("Error fetching campaign projects data:", error);
        throw error;
    }
}

// Exported functions using the reusable helper
export async function getCampaignProjects(
    campaignNumber: string,
    searchActiveOnly: boolean,
    boundaryCodes: string[] = [],
    parentBoundaryCodes: string[] = []
) {
    return fetchCampaignProjectsData(campaignNumber, searchActiveOnly, boundaryCodes, parentBoundaryCodes, false);
}

export async function getCampaignProjectsCount(
    campaignNumber: string,
    searchActiveOnly: boolean,
    boundaryCodes: string[] = [],
    parentBoundaryCodes: string[] = []
) {
    return fetchCampaignProjectsData(campaignNumber, searchActiveOnly, boundaryCodes, parentBoundaryCodes, true);
}





export async function updateTargetsInProjectCampaign(allTargetList: any, campaignProjects: any[]) {
    const campaignProjectsToUpdate: any[] = [];
    for (const campaignProject of campaignProjects) {
        const currentBoundary = campaignProject.boundaryCode;
        const newTargets = allTargetList[currentBoundary] || {};
        const oldTargets = campaignProject.additionalDetails?.targets || {};
        let isMatching = true;
        for (const beneficiaryType in newTargets) {
            const newTargetNoForCurrentBeneficiaryType = newTargets?.[beneficiaryType];
            const oldTargetNoForCurrentBeneficiaryType = oldTargets?.[beneficiaryType];
            if (newTargetNoForCurrentBeneficiaryType != oldTargetNoForCurrentBeneficiaryType) {
                isMatching = false;
                break;
            }
        }
        if(!isMatching){
            campaignProject.additionalDetails.newTargets = newTargets;
            campaignProjectsToUpdate.push(campaignProject);
        }
    }
    if(campaignProjectsToUpdate?.length > 0){
        await updateCampaignProjects(campaignProjects);
    }
}


export async function addBoundariesInProjectCampaign( allBoundaries: any, allTargetList: any, campaignNumber: string, currentUserUuid: string, campaignProjects: any[]) {
    const campaignProjectsToAdd: any[] = [];
    const alreadyPresentBoundarySet = new Set(campaignProjects.map((project: any) => project.boundaryCode));
    for (const boundary of allBoundaries) {
        const currentTime = new Date().getTime();
        if (!alreadyPresentBoundarySet.has(boundary.code)) {
            const campaignProject = {
                id: uuidv4(),
                projectId: null,
                campaignNumber: campaignNumber,
                boundaryCode: boundary.code,
                boundaryType: boundary.type,
                parentBoundaryCode: boundary?.parent || null,
                additionalDetails: {
                    targets: allTargetList[boundary.code]
                },
                isActive: true,
                createdBy: currentUserUuid,
                lastModifiedBy: currentUserUuid,
                createdTime: currentTime,
                lastModifiedTime: currentTime
            };
            campaignProjectsToAdd.push(campaignProject);
        }
    }
    if(campaignProjectsToAdd?.length > 0){
        await addCampaignProjects(campaignProjectsToAdd);
    }
}

export async function processProjectCreationFromConsumer(data: any, campaignNumber: string) {
    const { parentProjectId, childrenBoundaryCodes, tenantId } = data;

    // Get campaign projects for the provided campaign number and children boundary codes
    const campaignProjects = await getCampaignProjects(campaignNumber, true, childrenBoundaryCodes);

    // Perform project updates and creation one after the other (sequential)
    const boundaryAndProjectMappingFromUpdate = await updateTargetsAndGetProjectIdsMapping(campaignProjects, tenantId);
    const boundaryAndProjectMappingFromCreation = await createProjectsAndGetProjectIdsMapping(campaignProjects, campaignNumber, tenantId, parentProjectId);

    // Combine the mappings from updates and creations
    const finalBoundaryAndProjectMapping = { ...boundaryAndProjectMappingFromUpdate, ...boundaryAndProjectMappingFromCreation };

    // Get the mappings for child boundaries for the current boundary codes
    const parentToBoundaryCodeMap =await getParentToBoundaryCodeMapping(campaignNumber, childrenBoundaryCodes);

    // If no children mappings exist, persist project creation result and return early
    if (Object.keys(parentToBoundaryCodeMap).length === 0) {
        await checkAndPersistProjectCreationResult(campaignNumber, tenantId);
        return;
    }

    // Iterate over the mappings and persist project process
    for (const boundaryCode in parentToBoundaryCodeMap) {
        const childBoundaryCodes = parentToBoundaryCodeMap[boundaryCode];
        const projectIdForCurrentBoundary = finalBoundaryAndProjectMapping[boundaryCode];
        await persistForProjectProcess(childBoundaryCodes, campaignNumber, tenantId, projectIdForCurrentBoundary);
    }
}


/**
 * Fetch and prepare the boundary mappings and project IDs for updating.
 */
async function prepareTargetMappings(campaignProjects: any[]) {
    const boundaryCodesForTargetUpdateMappings: any = {};
    const projectIdsToUpdate: string[] = [];

    campaignProjects.forEach((campaignProject: any) => {
        if (campaignProject.additionalDetails?.newTargets) {
            boundaryCodesForTargetUpdateMappings[campaignProject.boundaryCode] = {
                projectId: campaignProject.projectId,
                targets: campaignProject.additionalDetails.newTargets
            };
            projectIdsToUpdate.push(campaignProject.projectId);
        }
    });

    return { boundaryCodesForTargetUpdateMappings, projectIdsToUpdate };
}

/**
 * Process and update project targets in chunks, including campaign project updates.
 */
async function processAndUpdateProjects(projectIdsToUpdate: string[], boundaryCodesForTargetUpdateMappings: any, campaignProjects: any[], tenantId: string) {
    for (let i = 0; i < projectIdsToUpdate.length; i += 100) {
        const projectIds = projectIdsToUpdate.slice(i, i + 100);
        const projectsToUpdate = await getProjectsWithProjectIds(projectIds, tenantId);

        // Update project targets
        projectsToUpdate.forEach((project: any) => {
            const newTargets = boundaryCodesForTargetUpdateMappings[project.boundaryCode]?.targets;
            if (newTargets) {
                project.targets?.forEach((target: any) => {
                    if (newTargets[target.beneficiaryType]) {
                        target.targetNo = newTargets[target.beneficiaryType];
                    }
                });
            }
        });

        // Update only the relevant campaign projects
        const campaignProjectsToUpdate = campaignProjects
            .filter((campaignProject: any) => projectIds.includes(campaignProject.projectId))
            .map((campaignProject: any) => {
                campaignProject.additionalDetails.targets = boundaryCodesForTargetUpdateMappings[campaignProject.boundaryCode]?.targets;
                delete campaignProject.additionalDetails.newTargets;
                return campaignProject;
            });

        await updateProjects(projectsToUpdate);
        await updateCampaignProjects(campaignProjectsToUpdate)
    }
}

/**
 * Main function to update targets and return the mappings.
 */
async function updateTargetsAndGetProjectIdsMapping(campaignProjects : any[], tenantId: string) {
    // Prepare mappings and project IDs
    const { boundaryCodesForTargetUpdateMappings, projectIdsToUpdate } =
        await prepareTargetMappings(campaignProjects);

    // Process updates in chunks
    await processAndUpdateProjects(projectIdsToUpdate, boundaryCodesForTargetUpdateMappings, campaignProjects, tenantId);

    // Clean up the mappings
    Object.keys(boundaryCodesForTargetUpdateMappings).forEach((boundaryCode) => {
        delete boundaryCodesForTargetUpdateMappings[boundaryCode].targets;
    });

    return boundaryCodesForTargetUpdateMappings;
}

async function createProjectsAndGetProjectIdsMapping(
    campaignProjects: any[],
    campaignNumber: string,
    tenantId: string,
    parentProjectId: string | null = null
) {
    const boundaryCodesForProjectCreationMappings: Record<string, string> = {};
    const boundariesForProjectsToCreate: any[] = [];
    const updatedCampaignProjects: any[] = []; // Store modified campaign projects

    // Step 1: Prepare data for new and existing projects
    const boundaryCodeToCampaignProjectMap = new Map<string, any>();

    for (const campaignProject of campaignProjects) {
        boundaryCodeToCampaignProjectMap.set(campaignProject.boundaryCode, campaignProject);
        if (!campaignProject.projectId) {
            // Prepare new project data
            const targets = Object.entries(campaignProject?.additionalDetails?.targets || {}).map(
                ([beneficiaryType, targetNo]) => ({ beneficiaryType, targetNo })
            );

            boundariesForProjectsToCreate.push({
                boundaryCode: campaignProject.boundaryCode,
                boundaryType: campaignProject.boundaryType,
                targets
            });
        } else {
            // Map existing project boundary codes to project IDs
            boundaryCodesForProjectCreationMappings[campaignProject.boundaryCode] = campaignProject.projectId;
        }
    }

    // Get default project configuration
    const defaultProject = await getDefaultProject(campaignNumber, tenantId);

    // Step 2: Process and create projects in chunks of 100
    const chunkSize = 100;
    for (let i = 0; i < boundariesForProjectsToCreate.length; i += chunkSize) {
        const chunk = boundariesForProjectsToCreate.slice(i, i + chunkSize);

        // Step 2.1: Prepare project creation data for the current chunk
        const projectsToCreate = chunk.map((boundary) => ({
            ...defaultProject,
            address: {
                tenantId,
                boundary: boundary.boundaryCode,
                boundaryType: boundary.boundaryType,
            },
            targets: boundary.targets,
            parent: parentProjectId
        }));

        // Step 2.2: Create projects and map returned IDs to the respective boundary codes
        const createdProjects = await createProjectsAndGetCreatedProjects(projectsToCreate);

        // Step 2.3: Map the project IDs to the corresponding boundary codes and update only the relevant campaign projects
        createdProjects.forEach((project: any) => {
            const boundaryCode = project?.address?.boundary;
            boundaryCodesForProjectCreationMappings[boundaryCode] = project?.id;

            // If the campaign project exists, update its projectId
            const campaignProject = boundaryCodeToCampaignProjectMap.get(boundaryCode);
            if (campaignProject) {
                campaignProject.projectId = project?.id;
                updatedCampaignProjects.push(campaignProject); // Collect updated projects
            }
        });
        const projectIds = createdProjects.map((project: any) => project?.id);
        await confirmBulkProjectConfirmation(projectIds, tenantId);
    }

    // Step 3: Update campaign projects only once after processing all chunks
    if (updatedCampaignProjects.length > 0) {
        await updateCampaignProjects(updatedCampaignProjects);
    }

    // Step 4: Return the final mappings
    return boundaryCodesForProjectCreationMappings;
}

export async function confirmBulkProjectConfirmation(
    projectIds: string[],
    tenantId: string
) {
    const maxRetries = 20;
    const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
    const expectedTotalCount = projectIds.length;

    for (let attempt = 0; attempt < maxRetries; attempt++) {
        try {
            const actualTotalCount = await getProjectsCountsWithProjectIds(projectIds, tenantId);

            if (actualTotalCount === expectedTotalCount) {
                console.log("Bulk project confirmation successful.");
                return;
            } else {
                console.warn(`Attempt ${attempt + 1}/${maxRetries}: Project count mismatch. Retrying in 1 second...`);
                await delay(1000); // Delay before retrying
            }
        } catch (error) {
            console.error("Error during bulk project confirmation:", error);
            throw error; // Stop the process if an error occurs
        }
    }

    throw new Error("Failed to confirm project bulk creation after maximum retries.");
}





async function getDefaultProject(campaignNumber: string, tenantId: string) {
    const campaignDetails = {
        tenantId: tenantId,
        campaignNumber: campaignNumber,
    }
    const { responseData } = await searchProjectCampaignResourcData(campaignDetails);
    if(responseData.length > 0) {
        const defaultProject = enrichProjectDetailsFromCampaignDetails(responseData[0]);
        return defaultProject;
    }
    else{
        throw new Error(`No campaign found for campaign number ${campaignNumber}`);
    }
}

export async function getParentToBoundaryCodeMapping(
    campaignNumber: string,
    parentBoundaryCodes: string[] = []
) {
    // Start with the base query
    let query = `SELECT parentboundarycode, boundarycode FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROJECTS_TABLE_NAME} WHERE campaignnumber = $1`;
    const values: any[] = [campaignNumber];  // Initialize an array to hold query parameters

    // If parentBoundaryCodes are provided and not empty, adjust the query
    if (parentBoundaryCodes.length > 0) {
        query += ` AND parentboundarycode = ANY($2)`;  // Use ANY operator for array matching
        values.push(parentBoundaryCodes);  // Pass the parentBoundaryCodes array directly
    }

    // Add the active condition to check for active projects
    query += ` AND isactive = true`;

    try {
        const queryResponse = await executeQuery(query, values);

        // Initialize a mapping of parentBoundaryCode to an array of boundaryCodes
        const parentToBoundaryCodeMap: Record<string, string[]> = {};

        // Map over the rows and populate the mapping
        queryResponse.rows.forEach((row: any) => {
            const { parentboundarycode, boundarycode } = row;

            // Initialize the array for this parent boundary code if it doesn't exist
            if (!parentToBoundaryCodeMap[parentboundarycode]) {
                parentToBoundaryCodeMap[parentboundarycode] = [];
            }

            // Add the boundary code to the corresponding parent boundary code
            parentToBoundaryCodeMap[parentboundarycode].push(boundarycode);
        });

        return parentToBoundaryCodeMap;
    } catch (error) {
        console.error("Error fetching parent to boundary code mapping:", error);
        throw error;
    }
}

export async function checkAndPersistProjectCreationResult(
    campaignNumber: string,
    tenantId: string
) {
    const maxRetries = 10;
    const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

    for (let attempt = 0; attempt < maxRetries; attempt++) {
        try {
            // Check if the process is already completed before each retry
            const isProcessAlreadyCompleted = await checkIfProcessIsCompleted(
                campaignNumber,
                processNamesConstants.projectCreation
            );
            if (isProcessAlreadyCompleted) {
                logger.info("Process already completed.");
                return;
            }

            const isAllProjectCampaignWithProjectId = await checkIfAllProjectCampaignWithProjectId(campaignNumber);

            // If counts match, mark as completed and exit
            if (isAllProjectCampaignWithProjectId) {
                await markProcessStatus(campaignNumber, processNamesConstants.projectCreation, campaignProcessStatus.completed);
                logger.info("Project creation process marked as completed.");
                return;
            } else {
                logger.warn(`Attempt ${attempt + 1}/${maxRetries}: Project counts do not match. Retrying from the start in 2 seconds...`);
                await delay(2000); // Delay before retrying
            }
        } catch (error) {
            console.error("Error during project creation check:", error);
            throw error; // Stop the process if an error occurs
        }
    }

    logger.warn("Failed to check the status of the project creation process after maximum retries.");
}

export async function checkIfAllProjectCampaignWithProjectId(campaignNumber: string): Promise<boolean> {
    // Start with the base query to check for any rows with a null or missing projectId
    const query = `
        SELECT 1 
        FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROJECTS_TABLE_NAME} 
        WHERE campaignnumber = $1 
          AND (projectid IS NULL OR projectid = '')
        LIMIT 1;
    `;

    const values = [campaignNumber];  // Prepare the query parameters

    try {
        // Execute the query and fetch results
        const queryResponse = await executeQuery(query, values);

        // If no rows are returned, all projects have a non-null projectId
        const allProjectsHaveProjectId = queryResponse.rows.length === 0;

        return allProjectsHaveProjectId;
    } catch (error) {
        console.error("Error checking campaign projects for projectId presence:", error);
        throw error;
    }
}








