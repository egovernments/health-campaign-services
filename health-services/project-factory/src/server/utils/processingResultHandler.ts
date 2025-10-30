import { logger } from './logger';
import { searchSheetData } from './excelIngestionUtils';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { getRelatedDataWithCampaign, getMappingDataRelatedToCampaign, prepareProcessesInDb, getRelatedDataWithUniqueIdentifiers, checkCampaignDataCompletionStatus, checkCampaignMappingCompletionStatus, throwError, getCurrentProcesses } from './genericUtils';
import { produceModifiedMessages } from '../kafka/Producer';
import { dataRowStatuses, mappingStatuses, usageColumnStatus, campaignStatuses, allProcesses, processStatuses } from '../config/constants';
import { searchMDMSDataViaV2Api, searchBoundaryRelationshipData, defaultRequestInfo } from '../api/coreApis';
import { populateBoundariesRecursively, getLocalizedName, enrichAndPersistCampaignWithError, enrichAndPersistCampaignForCreateViaFlow2, userCredGeneration } from './campaignUtils';
import { getLocalisationModuleName } from './localisationUtils';
import Localisation from '../controllers/localisationController/localisation.controller';
import { enrichProjectDetailsFromCampaignDetails } from './transforms/projectTypeUtils';
import { confirmProjectParentCreation } from '../api/campaignApis';
import { httpRequest } from './request';
import { fetchProjectsWithBoundaryCodeAndReferenceId } from './onGoingCampaignUpdateUtils';
import { v4 as uuidv4 } from 'uuid';
import config from '../config';
import { sendCampaignFailureMessage } from './campaignFailureHandler';
import { triggerUserCredentialEmailFlow } from './mailUtils';

/**
 * Helper function to get localized sheet name and trim to 31 characters
 */
function getLocalizedSheetName(sheetName: string, localizationMap: Record<string, string>): string {
    const localizedName = getLocalizedName(sheetName, localizationMap);
    // Excel sheet names have a 31 character limit
    return localizedName.substring(0, 31);
}

/**
 * Fetch localization data for hierarchy and admin schemas
 */
async function fetchLocalizationData(tenantId: string, campaignId: string, locale: string = 'en_IN'): Promise<Record<string, string>> {
    try {
        // Get campaign details to fetch hierarchy type
        const campaignResponse = await searchProjectTypeCampaignService({
            tenantId,
            ids: [campaignId]
        });
        const campaignDetails = campaignResponse?.CampaignDetails?.[0];
        
        if (!campaignDetails) {
            logger.warn('Campaign not found, using empty localization map');
            return {};
        }
        
        const hierarchyType = campaignDetails.hierarchyType;
        const localisationController = Localisation.getInstance();
        
        // Fetch localization for hierarchy module
        const hierarchyModuleName = getLocalisationModuleName(hierarchyType);
        logger.info(`Fetching localization for hierarchy module: ${hierarchyModuleName}`);
        const hierarchyLocalization = await localisationController.getLocalisedData(
            hierarchyModuleName,
            locale,
            tenantId,
            false
        );
        
        // Fetch localization for HCM admin schemas module
        const adminModuleName = 'hcm-admin-schemas';
        logger.info(`Fetching localization for admin module: ${adminModuleName}`);
        const adminLocalization = await localisationController.getLocalisedData(
            adminModuleName,
            locale,
            tenantId,
            false
        );
        
        // Merge both localization maps
        const localizationMap = {
            ...hierarchyLocalization,
            ...adminLocalization
        };
        
        logger.info(`Fetched localization map with ${Object.keys(localizationMap).length} entries`);
        return localizationMap;
        
    } catch (error) {
        logger.error('Error fetching localization data:', error);
        throw error;
    }
}

/**
 * Handler for HCM processing result messages from excel-ingestion service
 * This handler receives the ProcessResource object after Excel processing is complete
 * @param messageObject - The ProcessResource object from excel-ingestion service
 */
export async function handleProcessingResult(messageObject: any) {
    try {
        logger.info('=== HCM PROCESSING RESULT RECEIVED ===');
        
        // Log basic information
        logger.info(`Processing ID: ${messageObject.id}`);
        logger.info(`Tenant ID: ${messageObject.tenantId}`);
        logger.info(`Processing Type: ${messageObject.type}`);
        logger.info(`Status: ${messageObject.status}`);
        
        // Log additional details (includes error information if failed)
        if (!messageObject.additionalDetails) {
            logger.warn('No additional details found in message object - cannot validate processing status');
            return;
        }
        
        // Check validation status first
        const validationStatus = messageObject?.additionalDetails?.validationStatus;
        const totalRowsProcessed = messageObject?.additionalDetails?.totalRowsProcessed || 0;
        const totalErrors = messageObject?.additionalDetails?.totalErrors || 0;
        const createdByEmail = messageObject?.additionalDetails?.createdByEmail ?? null;
        
        logger.info(`Validation Status: ${validationStatus}`);
        logger.info(`Total Rows Processed: ${totalRowsProcessed}`);
        logger.info(`Total Errors: ${totalErrors}`);
        
        // Fetch campaign details first (needed for validation failure handling)
        logger.info('=== FETCHING CAMPAIGN DETAILS ===');
        const campaignSearchCriteria = {
            tenantId: messageObject.tenantId,
            ids: [messageObject.referenceId]
        };
        const campaignResponse = await searchProjectTypeCampaignService(campaignSearchCriteria);
        const campaignDetails = campaignResponse?.CampaignDetails?.[0];
        
        if (!campaignDetails) {
            logger.error(`No campaign found with campaignId: ${messageObject.referenceId}`);
            return;
        }
        
        logger.info(`Found campaign: ${campaignDetails.campaignName} (${campaignDetails.id})`);
        if(campaignDetails.status === campaignStatuses.failed){
            logger.warn('Campaign is already marked as failed, skipping further processing');
            return;
        }
        
        // Fetch parent campaign if exists
        let parentCampaign = null;
        if (campaignDetails.parentId) {
            logger.info(`Fetching parent campaign with ID: ${campaignDetails.parentId}`);
            try {
                const parentCampaignResponse = await searchProjectTypeCampaignService({
                    tenantId: messageObject.tenantId,
                    ids: [campaignDetails.parentId]
                });
                parentCampaign = parentCampaignResponse?.CampaignDetails?.[0];
                if (parentCampaign) {
                    logger.info(`Found parent campaign: ${parentCampaign.campaignName}`);
                } else {
                    throw new Error(`Parent campaign not found with ID: ${campaignDetails.parentId}`);
                }
            } catch (error) {
                logger.error(`Error fetching parent campaign: ${error}`);
                throw error;
            }
        }
        
        // Only proceed if validation status is valid
        if (validationStatus !== 'valid' || messageObject.status !== 'completed') {
            logger.warn('=== VALIDATION STATUS IS NOT VALID - STOPPING PROCESSING ===');
            logger.warn(`Validation Status: ${validationStatus}, cannot proceed with campaign data processing`);
            
            // Mark campaign as failed

            // const validationError = new Error(`Validation failed: ${validationStatus}, Status: ${messageObject.status}`);
            throwError('COMMON', 400, 'VALIDATION_ERROR_UNIFIED_CONSOLE_TEMPLATE', "Unified console template is not valid. Please correct the errors and try again.");
            logger.info('Campaign marked as failed due to validation failure');
            return;
        }
        
        // Add timing delay based on totalRowsProcessed 
        // Minimum 5 seconds, or 8ms per row processed
        const delayMs = Math.max(5000, totalRowsProcessed * 8);
        logger.info(`=== WAITING ${delayMs}ms BEFORE PROCESSING (${totalRowsProcessed} rows * 8ms, min 5s) ===`);
        await new Promise(resolve => setTimeout(resolve, delayMs));
        
        // Check for errors
        if (messageObject.additionalDetails.errorCode) {
            logger.error(`Error Code: ${messageObject.additionalDetails.errorCode}`);
            logger.error(`Error Message: ${messageObject.additionalDetails.errorMessage}`);
        }
        
        // Check for sheet error counts
        if (messageObject.additionalDetails.sheetErrorCounts) {
            logger.info('Sheet Error Counts:');
            Object.entries(messageObject.additionalDetails.sheetErrorCounts).forEach(([sheet, count]) => {
                logger.info(`  ${sheet}: ${count} errors`);
            });
        }
        
        // Fetch localization data
        logger.info('=== FETCHING LOCALIZATION DATA ===');
        const locale = messageObject.locale || config.localisation.defaultLocale;
        const localizationMap = await fetchLocalizationData(messageObject.tenantId, messageObject.referenceId, locale);
        logger.info(`Localization data fetched with ${Object.keys(localizationMap).length} keys`);
        
        // Search temp data and process campaign data
        logger.info('=== SEARCHING TEMP DATA AND PROCESSING CAMPAIGN DATA ===');
        
        if (messageObject.referenceId && messageObject.fileStoreId && messageObject.tenantId) {
            const tempData = await searchSheetData(
                messageObject.tenantId,
                messageObject.referenceId,
                messageObject.fileStoreId,
                5000 // Increased limit for processing
            );
            
            if (tempData && tempData.length > 0) {
                
                // Process campaign data from all sheets in parallel
                logger.info('=== PROCESSING ALL CAMPAIGN DATA TYPES IN PARALLEL ===');
                await Promise.all([
                    processCampaignBoundariesFromExcelData(
                        messageObject.tenantId,
                        messageObject.fileStoreId,
                        localizationMap,
                        campaignDetails
                    ),
                    processCampaignFacilitiesFromExcelData(
                        messageObject.tenantId,
                        messageObject.fileStoreId,
                        localizationMap,
                        campaignDetails
                    ),
                    processCampaignUsersFromExcelData(
                        messageObject.tenantId,
                        messageObject.fileStoreId,
                        localizationMap,
                        campaignDetails
                    )
                ]);
                logger.info('=== ALL CAMPAIGN DATA PROCESSING COMPLETED ===');
                
                // Trigger background resource creation and mapping flow
                logger.info('=== TRIGGERING BACKGROUND RESOURCE CREATION FLOW ===');
                await triggerBackgroundResourceCreationFlow(messageObject.tenantId, campaignDetails, parentCampaign, locale,createdByEmail);
            } else {
                throw new Error('No temp data found to process for campaign');
            }
        } else {
            throw new Error('Missing referenceId, fileStoreId, or tenantId in message object');
        }
        
        logger.info('=== END OF HCM PROCESSING RESULT ===');
        
    } catch (error) {
        logger.error('Error handling HCM processing result:', error);
        
        // Mark campaign as failed if we have referenceId and tenantId
        if (messageObject?.referenceId && messageObject?.tenantId) {
            try {
                await sendCampaignFailureMessage(messageObject.referenceId, messageObject.tenantId, error);
                logger.info(`Campaign ${messageObject.referenceId} marked as failed due to processing error`);
            } catch (failureError) {
                logger.error('Error marking campaign as failed:', failureError);
            }
        }
    }
}

/**
 * Process campaign boundaries from excel data using existing boundary processing logic
 */
async function processCampaignBoundariesFromExcelData(
    tenantId: string,
    fileStoreId: string,
    localizationMap: Record<string, string>,
    campaignDetails: any
): Promise<void> {
    try {
        logger.info('=== PROCESSING CAMPAIGN BOUNDARIES FROM EXCEL DATA ===');
        
        const campaignNumber = campaignDetails.campaignNumber;
        const campaignId = campaignDetails.id;

        // Step 2: Get target configuration from MDMS 
        const MdmsCriteria = {
            MdmsCriteria: {
                tenantId,
                schemaCode: "HCM-ADMIN-CONSOLE.targetConfigs",
                uniqueIdentifiers: [campaignDetails.projectType]
            }
        };
        
        const response = await searchMDMSDataViaV2Api(MdmsCriteria, true);
        if (!response?.mdms?.[0]?.data) {
            logger.error(`Target Config not found for ${campaignDetails.projectType}`);
            return;
        }

        const targetConfig = response.mdms[0].data;
        const targetColumns: string[] = [];
        
        for (const beneficiary of targetConfig.beneficiaries) {
            for (const col of beneficiary.columns) {
                targetColumns.push(col);
            }
        }

        logger.info(`Target columns from config: ${targetColumns.join(', ')}`);

        // Step 3: Enrich boundaries with includeChildren=true 
        const boundaryRelationshipResponse: any = await searchBoundaryRelationshipData(
            tenantId, campaignDetails.hierarchyType, true, true, false
        );

        const boundaries = campaignDetails?.boundaries || [];
        const boundaryChildren: Record<string, boolean> = boundaries.reduce((acc: any, boundary: any) => {
            acc[boundary.code] = boundary.includeAllChildren;
            return acc;
        }, {});
        
        const boundaryCodes: any = new Set(boundaries.map((b: any) => b.code));

        await populateBoundariesRecursively(
            boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary?.[0],
            boundaries,
            boundaryChildren[boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary?.[0]?.code],
            boundaryCodes,
            boundaryChildren
        );

        logger.info(`Enriched ${boundaries.length} boundaries with includeChildren=true`);

        // Step 4: Search specific boundary hierarchy sheet data  
        const boundarySheetName = getLocalizedSheetName('HCM_CONSOLE_BOUNDARY_HIERARCHY', localizationMap);
        logger.info(`Searching ${boundarySheetName} sheet data...`);
        const boundarySheetData = await searchSheetData(
            tenantId, 
            campaignId, 
            fileStoreId,
            null,
            boundarySheetName
        );

        if (!boundarySheetData || boundarySheetData.length === 0) {
            logger.warn('No boundary hierarchy sheet data found');
            return;
        }

        logger.info(`Found ${boundarySheetData.length} records in boundary hierarchy sheet`);

        // Step 5: Extract target data from sheet (only lowest level boundaries have targets)
        const sheetTargetData = extractTargetDataFromBoundarySheet(boundarySheetData, targetColumns);

        if (sheetTargetData.length === 0) {
            logger.warn('No boundary target data found in sheets');
            return;
        }

        logger.info(`Extracted target data for ${sheetTargetData.length} boundaries from sheet`);

        // Step 6: Map targets to all enriched boundaries (cascade from children to parents)
        const allBoundaryDataWithTargets = mapTargetsToEnrichedBoundaries(boundaries, sheetTargetData, targetColumns);
        logger.info(`Mapped targets to ${allBoundaryDataWithTargets.length} total boundaries (all hierarchy levels)`);

        // Step 8: Process all boundary data (with cascaded targets) and update eg_cm_campaign_data
        await processBoundaryDataInCampaignTable(campaignNumber, tenantId, allBoundaryDataWithTargets, targetColumns);

        // Step 9: Process resource-boundary mappings for campaign resources
        await processResourceBoundaryMappings(campaignNumber, tenantId, boundaries, campaignDetails);

        logger.info('=== CAMPAIGN BOUNDARY PROCESSING COMPLETED ===');

    } catch (error) {
        logger.error('Error processing campaign boundaries from excel data:', error);
        throw error;
    }
}

/**
 * Extract target data from boundary hierarchy sheet records (only lowest level boundaries)
 */
function extractTargetDataFromBoundarySheet(sheetData: any[], targetColumns: string[]): any[] {
    const boundaryDataList: any[] = [];
    const BOUNDARY_CODE_COLUMN = 'HCM_ADMIN_CONSOLE_BOUNDARY_CODE';
    
    sheetData.forEach(record => {
        const rowJson = record.rowjson || record.rowJson || {};
        const boundaryCode = rowJson[BOUNDARY_CODE_COLUMN];
        
        if (!boundaryCode) {
            logger.warn(`No boundary code found in row`);
            return;
        }

        // Check if row has any target data
        let hasTargets = false;
        targetColumns.forEach(targetColumn => {
            const targetValue = rowJson[targetColumn];
            if (targetValue !== undefined && targetValue !== null && targetValue !== '') {
                hasTargets = true;
            }
        });

        if (hasTargets) {
            boundaryDataList.push({
                boundaryCode,
                data: rowJson
            });
            logger.debug(`Extracted boundary: ${boundaryCode} with targets`);
        }
    });

    logger.info(`Extracted ${boundaryDataList.length} boundary records with targets from boundary hierarchy sheet`);
    return boundaryDataList;
}

/**
 * Map targets to all enriched boundaries with cascading logic
 * Following boundary-processClass enrichDatasForParents pattern
 */
function mapTargetsToEnrichedBoundaries(enrichedBoundaries: any[], sheetTargetData: any[], targetColumns: string[]): any[] {
    const BOUNDARY_CODE_COLUMN = 'HCM_ADMIN_CONSOLE_BOUNDARY_CODE';
    
    // Step 1: Create target data array from sheet data (lowest level)
    const datas: any[] = [];
    sheetTargetData.forEach(({ boundaryCode, data }) => {
        const targetData: any = { [BOUNDARY_CODE_COLUMN]: boundaryCode };
        
        targetColumns.forEach(col => {
            targetData[col] = data[col] || 0;
        });
        
        datas.push(targetData);
    });
    
    // Step 2: Enrich datas for parent boundaries (cascade targets upward)
    enrichDatasForParents(enrichedBoundaries, datas, targetColumns);
    
    // Step 3: Convert enriched data array to boundary data format
    const allBoundaryDataWithTargets: any[] = [];
    
    datas.forEach(data => {
        const boundaryCode = data[BOUNDARY_CODE_COLUMN];
        allBoundaryDataWithTargets.push({
            boundaryCode,
            data
        });
    });
    
    return allBoundaryDataWithTargets;
}

/**
 * Enrich data for parent boundaries by cascading targets from children
 * Following boundary-processClass enrichDatasForParents logic
 */
function enrichDatasForParents(boundaries: any[], datas: any[], targetColumns: string[]) {
    const BOUNDARY_CODE_COLUMN = 'HCM_ADMIN_CONSOLE_BOUNDARY_CODE';
    const codeToChildren: Record<string, string[]> = {};
    const codeToTarget: Record<string, Record<string, number>> = {};
    const rootBoundaryCode = boundaries.find((b: any) => !b.parent)?.code;

    // Step 1: Build parent → children map
    for (const b of boundaries) {
        if(!b.parent) continue;
        if (!codeToChildren[b.parent]) codeToChildren[b.parent] = [];
        codeToChildren[b.parent].push(b.code);
    }

    // Step 2: Initialize data for leaf nodes (from sheet)
    for (const d of datas) {
        const code = d[BOUNDARY_CODE_COLUMN];
        codeToTarget[code] = {};

        for (const key in d) {
            if (key === BOUNDARY_CODE_COLUMN) continue;
            const val = Number(d[key]);
            if (!isNaN(val)) codeToTarget[code][key] = val;
        }
    }

    // Step 3: DFS function to aggregate children's data
    const dfs = (code: string): Record<string, number> => {
        const result: Record<string, number> = { ...(codeToTarget[code] || {}) };

        for (const child of codeToChildren[code] || []) {
            const childData = dfs(child);
            for (const key in childData) {
                result[key] = (result[key] || 0) + childData[key];
            }
        }

        codeToTarget[code] = result;
        return result;
    };

    // Step 4: DFS traversal starting from root
    if (rootBoundaryCode) {
        dfs(rootBoundaryCode);
    }

    // Step 5: Convert aggregated map back to datas array (add parent boundaries)
    for (const code in codeToTarget) {
        if (!datas.find(d => d[BOUNDARY_CODE_COLUMN] === code)) {
            datas.push({
                [BOUNDARY_CODE_COLUMN]: code,
                ...codeToTarget[code],
            });
        }
    }
    
    logger.info(`Enriched data with cascaded targets for ${Object.keys(codeToTarget).length} total boundaries`);
}

/**
 * Process boundary data and update eg_cm_campaign_data table
 */
async function processBoundaryDataInCampaignTable(
    campaignNumber: string, 
    tenantId: string, 
    boundaryDataList: any[],
    targetColumns: string[]
): Promise<void> {
    if (boundaryDataList.length === 0) {
        return;
    }

    const BOUNDARY_CODE_COLUMN = 'HCM_ADMIN_CONSOLE_BOUNDARY_CODE';

    // Get existing campaign data
    const currentBoundaryData = await getRelatedDataWithCampaign('boundary', campaignNumber, tenantId);
    logger.info(`Found ${currentBoundaryData.length} existing boundary records in eg_cm_campaign_data`);

    // Create maps for efficient lookups
    const existingBoundaryMap = new Map(
        currentBoundaryData.map((d: any) => [d?.data?.[BOUNDARY_CODE_COLUMN], d])
    );

    // Process new and updated boundary data
    const newEntries: any[] = [];
    const updatedEntries: any[] = [];

    boundaryDataList.forEach(boundaryData => {
        const { boundaryCode, data } = boundaryData;
        const existingEntry = existingBoundaryMap.get(boundaryCode);

        if (!existingEntry) {
            // New boundary entry
            newEntries.push({
                campaignNumber,
                data,
                type: 'boundary',
                uniqueIdentifier: boundaryCode,
                uniqueIdAfterProcess: null,
                status: dataRowStatuses.pending
            });
            logger.info(`New boundary entry: ${boundaryCode}`);
        } else {
            // Check if targets differ
            let hasChanges = false;
            const existingData = existingEntry.data;

            targetColumns.forEach(targetColumn => {
                const newValue = data[targetColumn];
                const existingValue = existingData[targetColumn];
                
                if (newValue !== undefined && newValue !== existingValue) {
                    existingData[targetColumn] = newValue;
                    hasChanges = true;
                    logger.info(`Updated ${targetColumn} for ${boundaryCode}: ${existingValue} -> ${newValue}`);
                }
            });

            if (hasChanges) {
                existingEntry.status = dataRowStatuses.pending;
                updatedEntries.push(existingEntry);
                logger.info(`Updated boundary entry: ${boundaryCode}`);
            }
        }
    });

    // Persist changes
    if (newEntries.length > 0) {
        logger.info(`Persisting ${newEntries.length} new boundary entries to eg_cm_campaign_data`);
        await persistDataInBatches(newEntries, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC, tenantId);
    }

    if (updatedEntries.length > 0) {
        logger.info(`Updating ${updatedEntries.length} existing boundary entries in eg_cm_campaign_data`);
        await persistDataInBatches(updatedEntries, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
    }

    if (newEntries.length === 0 && updatedEntries.length === 0) {
        logger.info('No boundary data changes detected');
    } else {
        logger.info(`Campaign data table updated: ${newEntries.length} new, ${updatedEntries.length} updated`);
    }
}

/**
 * Process resource-boundary mappings for all campaign resources
 */
async function processResourceBoundaryMappings(
    campaignNumber: string,
    tenantId: string,
    boundaries: any[],
    campaignDetails: any
): Promise<void> {
    try {
        logger.info('=== PROCESSING RESOURCE-BOUNDARY MAPPINGS ===');
        
        // Step 1: Extract pvar IDs from campaign delivery rules (same logic as campaignMappingUtils.getPvarIds)
        const pvarIds = extractPvarIdsFromCampaign(campaignDetails);
        
        if (!pvarIds || pvarIds.length === 0) {
            logger.info('No pvar IDs found in campaign delivery rules, skipping resource mapping');
            return;
        }
        
        logger.info(`Found ${pvarIds.length} unique pvar IDs for resource mapping: ${pvarIds.join(', ')}`);
        
        // Step 2: Create resource-boundary mappings for each boundary and each pvar ID
        const resourceBoundaryMappings: any[] = [];
        
        // Get all boundary codes from enriched boundaries
        const boundaryCodes = boundaries.map(boundary => boundary.code).filter(Boolean);
        
        // Create mappings: each pvar ID maps to each boundary
        boundaryCodes.forEach(boundaryCode => {
            pvarIds.forEach(pvarId => {
                resourceBoundaryMappings.push({
                    pvarId,
                    boundaryCode,
                    active: true
                });
            });
        });
        
        logger.info(`Created ${resourceBoundaryMappings.length} resource-boundary mapping entries`);
        
        // Step 3: Process resource-boundary mappings (only create new ones, no demapping)
        if (resourceBoundaryMappings.length > 0) {
            await handleResourceBoundaryMappings(campaignNumber, tenantId, resourceBoundaryMappings);
        }
        
        logger.info('=== RESOURCE-BOUNDARY MAPPINGS PROCESSING COMPLETED ===');
        
    } catch (error) {
        logger.error('Error processing resource-boundary mappings:', error);
        throw error;
    }
}

/**
 * Extract pvar IDs from campaign delivery rules (following campaignMappingUtils.getPvarIds pattern)
 */
function extractPvarIdsFromCampaign(campaignDetails: any): string[] {
    const deliveryRules = campaignDetails?.deliveryRules;
    const uniquePvarIds = new Set<string>(); // Create a Set to store unique pvar IDs
    
    if (deliveryRules) {
        for (const deliveryRule of deliveryRules) {
            const products = deliveryRule?.resources;
            if (products) {
                for (const product of products) {
                    if (product?.productVariantId) {
                        uniquePvarIds.add(product.productVariantId); // Add pvar ID to the Set
                    }
                }
            }
        }
    }
    
    return Array.from(uniquePvarIds); // Convert Set to array before returning
}

/**
 * Handle resource-boundary mappings - only create new mappings (no demapping)
 */
async function handleResourceBoundaryMappings(
    campaignNumber: string,
    tenantId: string,
    newMappings: any[]
): Promise<void> {
    // Get existing resource mappings for this campaign
    const existingMappings = await getMappingDataRelatedToCampaign('resource', campaignNumber, tenantId);
    const existingMappingSet = new Set(
        existingMappings.map((m: any) => `${m.uniqueIdentifierForData}#${m.boundaryCode}`)
    );
    
    // Prepare new mappings to be created (only create, no demap)
    const mappingsToCreate: any[] = [];
    
    newMappings.forEach(mapping => {
        const key = `${mapping.pvarId}#${mapping.boundaryCode}`;
        
        if (!existingMappingSet.has(key)) {
            mappingsToCreate.push({
                campaignNumber,
                type: 'resource',
                uniqueIdentifierForData: mapping.pvarId,
                boundaryCode: mapping.boundaryCode,
                mappingId: null,
                status: mappingStatuses.toBeMapped
            });
        }
    });
    
    // Persist only new mapping creations
    if (mappingsToCreate.length > 0) {
        logger.info(`Creating ${mappingsToCreate.length} new resource-boundary mappings`);
        await persistDataInBatches(mappingsToCreate, config.kafka.KAFKA_SAVE_MAPPING_DATA_TOPIC, tenantId);
    } else {
        logger.info('No new resource-boundary mappings to create - all mappings already exist');
    }
}

/**
 * Mark all creation processes as completed
 */
async function markCreationProcessesAsCompleted(
    campaignNumber: string,
    tenantId: string,
    userUuid: string
): Promise<void> {
    try {
        logger.info(`Marking creation processes as completed for campaign: ${campaignNumber}`);
        
        // Use imported constants and functions
        
        // Get all creation processes
        const creationProcessTypes = [
            allProcesses.facilityCreation,
            allProcesses.userCreation,
            allProcesses.projectCreation
        ];
        
        const processesToUpdate = [];
        const currentTime = Date.now();
        
        // Check each creation process type
        for (const processType of creationProcessTypes) {
            const processes = await getCurrentProcesses(campaignNumber, tenantId, processType);
            
            for (const process of processes) {
                // Only update if not already completed
                if (process.status !== processStatuses.completed) {
                    process.status = processStatuses.completed;
                    process.auditDetails = {
                        createdBy: process.auditDetails?.createdBy || userUuid,
                        createdTime: process.auditDetails?.createdTime || currentTime,
                        lastModifiedBy: userUuid,
                        lastModifiedTime: currentTime
                    };
                    processesToUpdate.push(process);
                }
            }
        }
        
        // Update processes via Kafka if any need updating
        if (processesToUpdate.length > 0) {
            logger.info(`Updating ${processesToUpdate.length} creation processes to completed status`);
            await produceModifiedMessages(
                { processes: processesToUpdate }, 
                config.kafka.KAFKA_UPDATE_PROCESS_DATA_TOPIC, 
                tenantId
            );
        } else {
            logger.info('All creation processes are already completed');
        }
        
    } catch (error) {
        logger.error(`Error marking creation processes as completed: ${error}`);
        throwError('COMMON', 500, 'PROCESS_UPDATE_ERROR', 'Error updating the statuses of creation processes');
    }
}

/**
 * Monitor campaign data completion status with polling
 */
async function monitorCampaignDataCompletion(
    campaignNumber: string,
    tenantId: string,
    campaignId: string,
    campaignAlreadyFailed: { value: boolean },
    userUuid: string
): Promise<void> {
    try {
        logger.info(`Starting data completion monitoring for campaign: ${campaignNumber}`);
        
        const maxAttempts = config.resourceCreationConfig.maxAttemptsForResourceCreationOrMapping;
        const waitTimeMs = config.resourceCreationConfig.waitTimeOfEachAttemptOfResourceCreationOrMappping;
        
        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            // Check if campaign itself is failed
            try {
                const campaignResponse = await searchProjectTypeCampaignService({
                    tenantId: tenantId,
                    ids: [campaignId]
                });
                const campaign = campaignResponse?.CampaignDetails?.[0];
                
                if (campaign?.status === campaignStatuses.failed) {
                    logger.info(`Campaign ${campaignNumber} is already marked as failed. Stopping data monitoring.`);
                    campaignAlreadyFailed.value = true;
                    return;
                }
            } catch (campaignCheckError) {
                logger.warn(`Could not check campaign status, continuing with data monitoring: ${campaignCheckError}`);
            }
            
            const status = await checkCampaignDataCompletionStatus(campaignNumber, tenantId);
            
            logger.info(`Campaign ${campaignNumber} polling attempt ${attempt}/${maxAttempts}: ${status.completedRows}/${status.totalRows} completed, ${status.failedRows} failed, ${status.pendingRows} pending`);
            
            if (status.anyFailed) {
                logger.error(`Campaign ${campaignNumber} has failed data entries. Marking campaign as failed.`);
                const failureError = new Error(`Data creation failed: ${status.failedRows} out of ${status.totalRows} data entries failed`);
                await sendCampaignFailureMessage(campaignId, tenantId, failureError);
                throw failureError;
            }
            
            if (status.allCompleted && status.totalRows > 0) {
                logger.info(`Campaign ${campaignNumber} data creation completed successfully. All ${status.totalRows} entries are completed.`);
                // Mark all creation processes as completed
                await markCreationProcessesAsCompleted(campaignNumber, tenantId, userUuid);
                return;
            }
            
            // If not the last attempt, wait before next poll
            if (attempt < maxAttempts) {
                await new Promise(resolve => setTimeout(resolve, waitTimeMs));
            }
        }
        
        // Max attempts reached
        logger.error(`Campaign ${campaignNumber} data creation timed out after ${maxAttempts} attempts`);
        const timeoutError = new Error(`Data creation timed out: polling exceeded ${maxAttempts} attempts`);
        await sendCampaignFailureMessage(campaignId, tenantId, timeoutError);
        throw timeoutError;
        
    } catch (error) {
        logger.error(`Error monitoring campaign ${campaignNumber} data completion:`, error);
        throw error;
    }
}

/**
 * Mark all mapping processes as completed
 */
async function markMappingProcessesAsCompleted(
    campaignNumber: string,
    tenantId: string,
    userUuid: string
): Promise<void> {
    try {
        logger.info(`Marking mapping processes as completed for campaign: ${campaignNumber}`);
        
        // Get all mapping processes
        const mappingProcessTypes = [
            allProcesses.facilityMapping,
            allProcesses.userMapping,
            allProcesses.resourceMapping
        ];
        
        const processesToUpdate = [];
        const currentTime = Date.now();
        
        // Check each mapping process type
        for (const processType of mappingProcessTypes) {
            const processes = await getCurrentProcesses(campaignNumber, tenantId, processType);
            
            for (const process of processes) {
                // Only update if not already completed
                if (process.status !== processStatuses.completed) {
                    process.status = processStatuses.completed;
                    process.auditDetails = {
                        createdBy: process.auditDetails?.createdBy || userUuid,
                        createdTime: process.auditDetails?.createdTime || currentTime,
                        lastModifiedBy: userUuid,
                        lastModifiedTime: currentTime
                    };
                    processesToUpdate.push(process);
                }
            }
        }
        
        // Update processes via Kafka if any need updating
        if (processesToUpdate.length > 0) {
            logger.info(`Updating ${processesToUpdate.length} mapping processes to completed status`);
            await produceModifiedMessages(
                { processes: processesToUpdate }, 
                config.kafka.KAFKA_UPDATE_PROCESS_DATA_TOPIC, 
                tenantId
            );
        } else {
            logger.info('All mapping processes are already completed');
        }
        
    } catch (error) {
        logger.error(`Error marking mapping processes as completed: ${error}`);
        throwError('COMMON', 500, 'PROCESS_UPDATE_ERROR', 'Error updating the statuses of mapping processes');
    }
}

/**
 * Monitor campaign mapping completion status with polling
 */
async function monitorCampaignMappingCompletion(
    campaignNumber: string,
    tenantId: string,
    campaignId: string,
    campaignAlreadyFailed: { value: boolean },
    userUuid: string
): Promise<void> {
    try {
        logger.info(`Starting mapping completion monitoring for campaign: ${campaignNumber}`);
        
        const maxAttempts = config.resourceCreationConfig.maxAttemptsForResourceCreationOrMapping;
        const waitTimeMs = config.resourceCreationConfig.waitTimeOfEachAttemptOfResourceCreationOrMappping;
        
        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            // Check if campaign itself is failed
            try {
                const campaignResponse = await searchProjectTypeCampaignService({
                    tenantId: tenantId,
                    ids: [campaignId]
                });
                const campaign = campaignResponse?.CampaignDetails?.[0];
                
                if (campaign?.status === campaignStatuses.failed) {
                    logger.info(`Campaign ${campaignNumber} is already marked as failed. Stopping mapping monitoring.`);
                    campaignAlreadyFailed.value = true;
                    return;
                }
            } catch (campaignCheckError) {
                logger.warn(`Could not check campaign status, continuing with mapping monitoring: ${campaignCheckError}`);
            }
            
            const status = await checkCampaignMappingCompletionStatus(campaignNumber, tenantId);
            
            logger.info(`Campaign ${campaignNumber} mapping attempt ${attempt}/${maxAttempts}: ${status.completedMappings}/${status.totalMappings} completed, ${status.failedMappings} failed, ${status.pendingMappings} pending`);
            
            if (status.anyFailed) {
                logger.error(`Campaign ${campaignNumber} has failed mappings. Marking campaign as failed.`);
                const failureError = new Error(`Mapping failed: ${status.failedMappings} out of ${status.totalMappings} mappings failed`);
                await sendCampaignFailureMessage(campaignId, tenantId, failureError);
                throw failureError;
            }
            
            if (status.allCompleted && status.totalMappings > 0) {
                logger.info(`Campaign ${campaignNumber} mapping completed successfully. All ${status.totalMappings} mappings are completed.`);
                // Mark all mapping processes as completed
                await markMappingProcessesAsCompleted(campaignNumber, tenantId, userUuid);
                return;
            }
            
            // If not the last attempt, wait before next poll
            if (attempt < maxAttempts) {
                await new Promise(resolve => setTimeout(resolve, waitTimeMs));
            }
        }
        
        // Max attempts reached
        logger.error(`Campaign ${campaignNumber} mapping timed out after ${maxAttempts} attempts`);
        const timeoutError = new Error(`Mapping timed out: polling exceeded ${maxAttempts} attempts`);
        await sendCampaignFailureMessage(campaignId, tenantId, timeoutError);
        throw timeoutError;
        
    } catch (error) {
        logger.error(`Error monitoring campaign ${campaignNumber} mapping completion:`, error);
        throw error;
    }
}

/**
 * Mark campaign as completed conditionally (only if not failed) using existing function
 */
async function markCampaignCompletedConditionally(
    campaignDetails: any,
    parentCampaign: any,
    useruuid: string,
    tenantId: string
): Promise<void> {
    try {
        logger.info(`Checking campaign status before marking as completed: ${campaignDetails.id}`);
        
        // Search for latest campaign details to check current status
        const campaignResponse = await searchProjectTypeCampaignService({
            tenantId: tenantId,
            ids: [campaignDetails.id]
        });
        const latestCampaign = campaignResponse?.CampaignDetails?.[0];
        
        if (!latestCampaign) {
            logger.error(`Campaign not found for completion marking: ${campaignDetails.id}`);
            return;
        }
        
        if (latestCampaign.status === campaignStatuses.failed) {
            logger.info(`Campaign ${campaignDetails.id} is already marked as failed. Skipping completion marking.`);
            return;
        }
        
        logger.info(`Campaign ${campaignDetails.id} is not failed. Marking as completed using existing function.`);
        
        // Use existing function to mark campaign as completed and handle parent
        const RequestInfo = { 
            userInfo: { uuid: useruuid || campaignDetails?.auditDetails?.createdBy }
        };
        
        // Set status as inprogress (completed) and send to persister
        latestCampaign.status = campaignStatuses.inprogress;
        
        await enrichAndPersistCampaignForCreateViaFlow2(
            latestCampaign,
            RequestInfo,
            parentCampaign,
            useruuid
        );
        
        logger.info(`Campaign ${campaignDetails.id} marked as completed successfully`);
        if (parentCampaign) {
            logger.info(`Parent campaign ${parentCampaign.id} marked as inactive successfully`);
        }
        
    } catch (error) {
        logger.error(`Error marking campaign ${campaignDetails.id} as completed:`, error);
        throw error;
    }
}

/**
 * Persist data in batches using Kafka
 */
async function persistDataInBatches(dataList: any[], topic: string, tenantId: string): Promise<void> {
    const batchSize = 100;
    
    for (let i = 0; i < dataList.length; i += batchSize) {
        const batch = dataList.slice(i, i + batchSize);
        await produceModifiedMessages({ datas: batch }, topic, tenantId);
        
        if (i + batchSize < dataList.length) {
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
    }

    // Wait for persistence to complete
    const waitTime = Math.max(5000, dataList.length * 8);
    logger.info(`Waiting ${waitTime}ms for persistence to complete`);
    await new Promise(resolve => setTimeout(resolve, waitTime));
}

/**
 * Process campaign facilities from excel data
 */
async function processCampaignFacilitiesFromExcelData(
    tenantId: string,
    fileStoreId: string,
    localizationMap: Record<string, string>,
    campaignDetails: any
): Promise<void> {
    try {
        logger.info('=== PROCESSING CAMPAIGN FACILITIES FROM EXCEL DATA ===');
        
        const campaignNumber = campaignDetails.campaignNumber;
        const campaignId = campaignDetails.id;
        logger.info(`Processing facilities for campaign: ${campaignDetails.campaignName}`);

        // Search facilities sheet data
        const facilitySheetName = getLocalizedSheetName('HCM_ADMIN_CONSOLE_FACILITIES_LIST', localizationMap);
        logger.info(`Searching ${facilitySheetName} sheet data...`);
        const facilitySheetData = await searchSheetData(
            tenantId, 
            campaignId, 
            fileStoreId,
            null,
            facilitySheetName
        );

        if (!facilitySheetData || facilitySheetData.length === 0) {
            logger.info('No facilities sheet data found');
            return;
        }

        logger.info(`Found ${facilitySheetData.length} records in facilities sheet`);

        // Process facilities and their mappings
        await processFacilityDataAndMappings(campaignNumber, tenantId, facilitySheetData);

        logger.info('=== CAMPAIGN FACILITIES PROCESSING COMPLETED ===');

    } catch (error) {
        logger.error('Error processing campaign facilities from excel data:', error);
        throw error;
    }
}

/**
 * Process facility data and update campaign data and mapping tables
 */
async function processFacilityDataAndMappings(
    campaignNumber: string,
    tenantId: string,
    sheetData: any[]
): Promise<void> {
    const FACILITY_NAME_KEY = 'HCM_ADMIN_CONSOLE_FACILITY_NAME';
    const FACILITY_CODE_KEY = 'HCM_ADMIN_CONSOLE_FACILITY_CODE';
    const BOUNDARY_CODE_KEY = 'HCM_ADMIN_CONSOLE_BOUNDARY_CODE';
    const USAGE_KEY = 'HCM_ADMIN_CONSOLE_FACILITY_USAGE';
    
    // Get existing facility data
    const existingFacilities = await getRelatedDataWithCampaign('facility', campaignNumber, tenantId);
    logger.info(`Found ${existingFacilities.length} existing facility records in eg_cm_campaign_data`);
    
    // Create map for existing facilities
    const existingFacilityMap = new Map(
        existingFacilities.map((f: any) => [f?.data?.[FACILITY_NAME_KEY], f])
    );
    
    // Process facilities from sheet
    const newFacilities: any[] = [];
    const updatedFacilities: any[] = [];
    const facilityBoundaryMappings: any[] = [];
    
    sheetData.forEach(record => {
        const rowJson = record.rowjson || record.rowJson || {};
        const facilityName = rowJson[FACILITY_NAME_KEY];
        const facilityCode = rowJson[FACILITY_CODE_KEY];
        const boundaryCodes = rowJson[BOUNDARY_CODE_KEY];
        const usage = rowJson[USAGE_KEY];
        
        if (!facilityName) {
            logger.warn('No facility name found in row');
            return;
        }
        
        const existingEntry = existingFacilityMap.get(facilityName);
        
        if (!existingEntry) {
            // New facility
            newFacilities.push({
                campaignNumber,
                data: rowJson,
                type: 'facility',
                uniqueIdentifier: facilityName,
                uniqueIdAfterProcess: facilityCode || null,
                status: facilityCode ? dataRowStatuses.completed : dataRowStatuses.pending
            });
            logger.info(`New facility entry: ${facilityName}`);
        } else {
            // Check if data has changed
            let hasChanges = false;
            const existingData = existingEntry.data;
            
            // Update facility code if different
            if (facilityCode && existingData[FACILITY_CODE_KEY] !== facilityCode) {
                existingData[FACILITY_CODE_KEY] = facilityCode;
                existingEntry.uniqueIdAfterProcess = facilityCode;
                existingEntry.status = dataRowStatuses.completed;
                hasChanges = true;
            }
            
            // Update boundary codes if different
            if (boundaryCodes !== existingData[BOUNDARY_CODE_KEY]) {
                existingData[BOUNDARY_CODE_KEY] = boundaryCodes;
                hasChanges = true;
            }
            
            // Update usage if different
            if (usage !== existingData[USAGE_KEY]) {
                existingData[USAGE_KEY] = usage;
                hasChanges = true;
            }
            
            if (hasChanges) {
                updatedFacilities.push(existingEntry);
                logger.info(`Updated facility entry: ${facilityName}`);
            }
        }
        
        // Prepare boundary mappings for active facilities
        if (usage === usageColumnStatus.active && boundaryCodes) {
            const boundaryList = boundaryCodes.split(',').map((b: string) => b.trim()).filter(Boolean);
            boundaryList.forEach((boundaryCode: string) => {
                facilityBoundaryMappings.push({
                    facilityName,
                    boundaryCode,
                    active: true
                });
            });
        }
    });
    
    // Persist facility data changes
    if (newFacilities.length > 0) {
        logger.info(`Persisting ${newFacilities.length} new facility entries`);
        await persistDataInBatches(newFacilities, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC, tenantId);
    }
    
    if (updatedFacilities.length > 0) {
        logger.info(`Updating ${updatedFacilities.length} existing facility entries`);
        await persistDataInBatches(updatedFacilities, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
    }
    
    // Process facility-boundary mappings
    if (facilityBoundaryMappings.length > 0) {
        await processFacilityBoundaryMappings(campaignNumber, tenantId, facilityBoundaryMappings);
    }
    
    logger.info(`Facility processing completed: ${newFacilities.length} new, ${updatedFacilities.length} updated`);
}

/**
 * Process facility-boundary mappings
 */
async function processFacilityBoundaryMappings(
    campaignNumber: string,
    tenantId: string,
    mappings: any[]
): Promise<void> {
    // Get existing mappings
    const existingMappings = await getMappingDataRelatedToCampaign('facility', campaignNumber, tenantId);
    const existingMappingSet = new Set(
        existingMappings.map((m: any) => `${m.uniqueIdentifierForData}#${m.boundaryCode}`)
    );
    
    // Identify new mappings
    const newMappings: any[] = [];
    const toBeDemapped: any[] = [];
    
    // Check for new mappings from sheet
    mappings.forEach(mapping => {
        const key = `${mapping.facilityName}#${mapping.boundaryCode}`;
        if (!existingMappingSet.has(key)) {
            newMappings.push({
                campaignNumber,
                type: 'facility',
                uniqueIdentifierForData: mapping.facilityName,
                boundaryCode: mapping.boundaryCode,
                mappingId: null,
                status: mappingStatuses.toBeMapped
            });
        }
    });
    
    // Check for mappings to be demapped (exist in DB but not in sheet)
    const sheetMappingSet = new Set(
        mappings.map(m => `${m.facilityName}#${m.boundaryCode}`)
    );
    
    existingMappings.forEach((existing: any) => {
        const key = `${existing.uniqueIdentifierForData}#${existing.boundaryCode}`;
        if (!sheetMappingSet.has(key) && existing.status !== mappingStatuses.toBeDeMapped) {
            toBeDemapped.push({
                ...existing,
                status: mappingStatuses.toBeDeMapped
            });
        }
    });
    
    // Persist mapping changes
    if (newMappings.length > 0) {
        logger.info(`Creating ${newMappings.length} new facility-boundary mappings`);
        await persistDataInBatches(newMappings, config.kafka.KAFKA_SAVE_MAPPING_DATA_TOPIC, tenantId);
    }
    
    if (toBeDemapped.length > 0) {
        logger.info(`Marking ${toBeDemapped.length} facility-boundary mappings for demapping`);
        await persistDataInBatches(toBeDemapped, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
    }
}

/**
 * Process campaign users from excel data
 */
async function processCampaignUsersFromExcelData(
    tenantId: string,
    fileStoreId: string,
    localizationMap: Record<string, string>,
    campaignDetails: any
): Promise<void> {
    try {
        logger.info('=== PROCESSING CAMPAIGN USERS FROM EXCEL DATA ===');
        
        const campaignNumber = campaignDetails.campaignNumber;
        const campaignId = campaignDetails.id;
        logger.info(`Processing users for campaign: ${campaignDetails.campaignName}`);

        // Search users sheet data
        const userSheetName = getLocalizedSheetName('HCM_ADMIN_CONSOLE_USERS_LIST', localizationMap);
        logger.info(`Searching ${userSheetName} sheet data...`);
        const userSheetData = await searchSheetData(
            tenantId, 
            campaignId, 
            fileStoreId,
            null,
            userSheetName
        );

        if (!userSheetData || userSheetData.length === 0) {
            logger.info('No users sheet data found');
            return;
        }

        logger.info(`Found ${userSheetData.length} records in users sheet`);

        // Extract phone numbers from sheet data
        const phoneNumbers = userSheetData
            .map((row: any) => {
                const rowJson = row?.rowjson || {};
                const phone = rowJson["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"];
                return phone ? String(phone).trim() : null;
            })
            .filter((phone: string | null) => phone && phone !== "");

        if (phoneNumbers.length === 0) {
            logger.info('No phone numbers found in user sheet data');
            return;
        }

        logger.info(`Processing ${phoneNumbers.length} unique phone numbers`);

        // Simple user processing logic
        await processUsersSimple(userSheetData, phoneNumbers, campaignNumber, tenantId);

        logger.info('=== CAMPAIGN USERS PROCESSING COMPLETED ===');

    } catch (error) {
        logger.error('Error processing campaign users from excel data:', error);
        throw error;
    }
}

/**
 * Simple user processing - handles both data persistence and mappings
 */
async function processUsersSimple(
    userSheetData: any[],
    phoneNumbers: any[],
    campaignNumber: string,
    tenantId: string
): Promise<void> {
    const PHONE_KEY = 'HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER';
    const BOUNDARY_KEY = 'HCM_ADMIN_CONSOLE_BOUNDARY_CODE';
    const USAGE_KEY = 'HCM_ADMIN_CONSOLE_USER_USAGE';
    
    // Step 1: Get existing users from current campaign
    const currentCampaignUsers = await getRelatedDataWithCampaign("user", campaignNumber, tenantId);
    const currentUserMap = new Map(
        currentCampaignUsers.map((u: any) => [String(u?.data?.[PHONE_KEY]), u])
    );
    
    // Step 2: Get completed users from other campaigns to reuse
    const otherCampaignUsers = await getRelatedDataWithUniqueIdentifiers(
        "user", phoneNumbers, tenantId, dataRowStatuses.completed
    );
    const otherUserMap = new Map(
        otherCampaignUsers
            .filter((u: any) => u.campaignNumber !== campaignNumber)
            .map((u: any) => [String(u?.data?.[PHONE_KEY]), u])
    );
    
    // Step 3: Process each user from sheet
    const usersToSave: any[] = [];
    const usersToUpdate: any[] = [];
    const userBoundaryMappings: any[] = [];
    
    userSheetData.forEach(record => {
        const rowJson = record.rowjson || {};
        const phoneNumber = String(rowJson[PHONE_KEY]).trim();
        const boundaryCode = rowJson[BOUNDARY_KEY];
        const usage = rowJson[USAGE_KEY];
        
        if (!phoneNumber || phoneNumber === 'undefined') return;
        
        const currentUser = currentUserMap.get(phoneNumber);
        const otherUser = otherUserMap.get(phoneNumber);
        
        if (currentUser) {
            // User exists in current campaign - check if boundary and usage need to be updated
            const existingBoundary = currentUser.data[BOUNDARY_KEY];
            const existingUsage = currentUser.data[USAGE_KEY];
            if (boundaryCode !== existingBoundary || usage !== existingUsage) {
                const updatedData = { ...currentUser.data, [BOUNDARY_KEY]: boundaryCode, [USAGE_KEY]: usage };
                usersToUpdate.push({
                    ...currentUser,
                    data: updatedData,
                });
                logger.info(`Updated boundary for user ${phoneNumber}: ${existingBoundary} -> ${boundaryCode}`);
            }
        } else if (otherUser) {
            // User exists in other campaign - reuse with all fields preserved except boundary and usage
            const reusedData = { ...otherUser.data, [BOUNDARY_KEY]: boundaryCode, [USAGE_KEY]: usage };
            
            usersToSave.push({
                campaignNumber,
                data: reusedData,
                type: 'user',
                uniqueIdentifier: phoneNumber,
                uniqueIdAfterProcess: otherUser.uniqueIdAfterProcess,
                status: dataRowStatuses.completed
            });
            logger.info(`Reused user ${phoneNumber} from other campaign with new boundary: ${boundaryCode}`);
        } else {
            // Completely new user
            usersToSave.push({
                campaignNumber,
                data: rowJson,
                type: 'user',
                uniqueIdentifier: phoneNumber,
                uniqueIdAfterProcess: null,
                status: dataRowStatuses.pending
            });
            logger.info(`Added new user ${phoneNumber}`);
        }
        
        // Prepare boundary mappings for active users
        if (usage === usageColumnStatus.active && boundaryCode) {
            const boundaries = boundaryCode.split(',').map((b: string) => b.trim()).filter(Boolean);
            boundaries.forEach((boundary : String ) => {
                userBoundaryMappings.push({
                    phoneNumber,
                    boundaryCode: boundary,
                    active: true
                });
            });
        }
    });
    
    // Step 4: Persist user data
    if (usersToSave.length > 0) {
        logger.info(`Persisting ${usersToSave.length} user records`);
        await persistDataInBatches(usersToSave, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC, tenantId);
    }
    
    if (usersToUpdate.length > 0) {
        logger.info(`Updating ${usersToUpdate.length} user records`);
        await persistDataInBatches(usersToUpdate, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
    }
    
    // Step 5: Handle boundary mappings
    await handleUserBoundaryMappings(campaignNumber, tenantId, userBoundaryMappings);
    
    logger.info(`User processing completed: ${usersToSave.length} saved, ${usersToUpdate.length} updated, ${userBoundaryMappings.length} mappings processed`);
}

/**
 * Handle user boundary mappings - active users get mapped, inactive get demapped
 */
async function handleUserBoundaryMappings(
    campaignNumber: string,
    tenantId: string,
    newMappings: any[]
): Promise<void> {
    // Get existing mappings for this campaign
    const existingMappings = await getMappingDataRelatedToCampaign('user', campaignNumber, tenantId);
    const existingMappingSet = new Set(
        existingMappings.map((m: any) => `${m.uniqueIdentifierForData}#${m.boundaryCode}`)
    );
    
    // Prepare new mappings to be created
    const mappingsToCreate: any[] = [];
    const newMappingSet = new Set();
    
    newMappings.forEach(mapping => {
        const key = `${mapping.phoneNumber}#${mapping.boundaryCode}`;
        newMappingSet.add(key);
        
        if (!existingMappingSet.has(key)) {
            mappingsToCreate.push({
                campaignNumber,
                type: 'user',
                uniqueIdentifierForData: mapping.phoneNumber,
                boundaryCode: mapping.boundaryCode,
                mappingId: null,
                status: mappingStatuses.toBeMapped
            });
        }
    });
    
    // Prepare mappings to be demapped (exist in DB but not in new mappings)
    const mappingsToDemap: any[] = [];
    existingMappings.forEach((existing: any) => {
        const key = `${existing.uniqueIdentifierForData}#${existing.boundaryCode}`;
        if (!newMappingSet.has(key) && existing.status !== mappingStatuses.toBeDeMapped) {
            mappingsToDemap.push({
                ...existing,
                status: mappingStatuses.toBeDeMapped
            });
        }
    });
    
    // Persist mapping changes
    if (mappingsToCreate.length > 0) {
        logger.info(`Creating ${mappingsToCreate.length} new user-boundary mappings`);
        await persistDataInBatches(mappingsToCreate, config.kafka.KAFKA_SAVE_MAPPING_DATA_TOPIC, tenantId);
    }
    
    if (mappingsToDemap.length > 0) {
        logger.info(`Demapping ${mappingsToDemap.length} user-boundary mappings`);
        await persistDataInBatches(mappingsToDemap, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
    }
}

/**
 * Trigger background resource creation flow after data processing
 */
async function triggerBackgroundResourceCreationFlow(
    tenantId: string,
    campaignDetails: any,
    parentCampaign: any,
    locale: string,
    createdByEmail? : string,
): Promise<void> {
    try {
        const useruuid = campaignDetails?.auditDetails?.createdBy;
        const campaignNumber = campaignDetails.campaignNumber;
        
        logger.info(`Triggering background resource creation for campaign: ${campaignDetails.campaignName} (${campaignNumber})`);
        if (parentCampaign) {
            logger.info(`With parent campaign: ${parentCampaign.campaignName}`);
        }

        // Prepare DB setup synchronously
        await prepareProcessesInDb(campaignNumber, tenantId, useruuid);
        
        // Use setImmediate to run resource creation in background without blocking
        setImmediate(async () => {
            try {
                logger.info('=== BACKGROUND RESOURCE CREATION FLOW STARTED ===');
                
                // Initialize campaign failure tracking variable
                const campaignAlreadyFailed = { value: false };
                
                // Create Projects, Facilities, and Users in parallel along with data completion polling
                logger.info('Creating projects, facilities, and users in parallel with data completion monitoring...');
                await Promise.all([
                    createProjectsFromBoundaryData(campaignDetails, tenantId),
                    createFacilitiesFromFacilityData(campaignDetails, tenantId),
                    createUsersFromUserData(campaignDetails, tenantId),
                    monitorCampaignDataCompletion(campaignDetails.campaignNumber, tenantId, campaignDetails.id, campaignAlreadyFailed, useruuid)
                ]);
                
                // Check if campaign failed during data creation
                if (campaignAlreadyFailed.value) {
                    logger.info('Campaign already failed during data creation. Stopping background flow.');
                    return;
                }
                
                // Wait 10 seconds before starting mapping process
                logger.info('=== WAITING 10 SECONDS BEFORE STARTING MAPPING PROCESS ===');
                await new Promise(resolve => setTimeout(resolve, 10000));
                
                // Start mapping process for all types in batches with monitoring
                logger.info('=== STARTING MAPPING PROCESS IN BATCHES WITH MONITORING ===');
                await Promise.all([
                    startAllMappingsInBatches(campaignDetails, useruuid, tenantId),
                    monitorCampaignMappingCompletion(campaignDetails.campaignNumber, tenantId, campaignDetails.id, campaignAlreadyFailed, useruuid)
                ]);
                
                // Check if campaign failed during mapping
                if (campaignAlreadyFailed.value) {
                    logger.info('Campaign already failed during mapping. Stopping background flow.');
                    return;
                }
                
                // Generate user credentials and trigger email flow
                logger.info('=== STARTING USER CREDENTIAL GENERATION ===');
                await userCredGeneration(campaignDetails, useruuid, locale);
                
                // Check if campaign failed during credential generation
                if (campaignAlreadyFailed.value) {
                    logger.info('Campaign already failed during credential generation. Stopping background flow.');
                    return;
                }
                
                logger.info('=== TRIGGERING USER CREDENTIAL EMAIL FLOW ===');
                const requestInfoObject = JSON.parse(JSON.stringify(defaultRequestInfo || {}));
                requestInfoObject.RequestInfo.userInfo.tenantId = tenantId;
                requestInfoObject.RequestInfo.userInfo.uuid = useruuid;
                const msgId = `${new Date().getTime()}|${locale || config?.localisation?.defaultLocale}`;
                requestInfoObject.RequestInfo.msgId = msgId;

                triggerUserCredentialEmailFlow({
                    RequestInfo: requestInfoObject.RequestInfo,
                    CampaignDetails: campaignDetails,
                    parentCampaign: parentCampaign
                },  createdByEmail
            );
                
                logger.info('=== BACKGROUND RESOURCE CREATION FLOW COMPLETED SUCCESSFULLY ===');
                
                // Mark campaign as completed if not failed
                await markCampaignCompletedConditionally(campaignDetails, parentCampaign, useruuid, tenantId);
                
            } catch (error) {
                logger.error('Error in background resource creation flow:', error);
                try {
                    const mockRequestBody = {
                        CampaignDetails: campaignDetails,
                        RequestInfo: { userInfo: { uuid: useruuid } }
                    };
                    await enrichAndPersistCampaignWithError(mockRequestBody, error);
                } catch (errorHandlingError) {
                    logger.error('Error in error handling:', errorHandlingError);
                }
            }
        });
        
        logger.info('Background resource creation flow triggered successfully');
        
    } catch (error) {
        logger.error('Error triggering background resource creation flow:', error);
        throw error;
    }
}

/**
 * Create projects from boundary data following the same pattern as boundary-processClass
 */
async function createProjectsFromBoundaryData(campaignDetails: any, tenantId: string): Promise<void> {
    try {
        const campaignNumber = campaignDetails.campaignNumber;
        
        // Get target configuration from MDMS 
        const MdmsCriteria = {
            MdmsCriteria: {
                tenantId,
                schemaCode: "HCM-ADMIN-CONSOLE.targetConfigs",
                uniqueIdentifiers: [campaignDetails.projectType]
            }
        };
        
        const response = await searchMDMSDataViaV2Api(MdmsCriteria, true);
        if (!response?.mdms?.[0]?.data) {
            logger.error(`Target Config not found for ${campaignDetails.projectType}`);
            return;
        }
        const targetConfig = response.mdms[0].data;
        
        // Get enriched boundaries
        const boundaryRelationshipResponse: any = await searchBoundaryRelationshipData(
            tenantId, campaignDetails.hierarchyType, true, true, false
        );
        const boundaries = campaignDetails?.boundaries || [];
        const boundaryChildren: Record<string, boolean> = boundaries.reduce((acc: any, boundary: any) => {
            acc[boundary.code] = boundary.includeAllChildren;
            return acc;
        }, {});
        const boundaryCodes: any = new Set(boundaries.map((b: any) => b.code));
        await populateBoundariesRecursively(
            boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary?.[0],
            boundaries,
            boundaryChildren[boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary?.[0]?.code],
            boundaryCodes,
            boundaryChildren
        );
        
        // Get current boundary data using the same pattern as process classes
        const currentBoundaryData = await getRelatedDataWithCampaign('boundary', campaignNumber, tenantId);
        logger.info(`Found ${currentBoundaryData.length} boundary records for project creation`);
        
        if (currentBoundaryData.length === 0) {
            logger.warn('No boundary data found for project creation');
            return;
        }
        
        // Create and update projects using the boundary data
        await createAndUpdateProjects(currentBoundaryData, campaignDetails, boundaries, targetConfig);
        
        logger.info('Project creation from boundary data completed successfully');
        
    } catch (error) {
        logger.error('Error creating projects from boundary data:', error);
        throw error;
    }
}

/**
 * Create and update projects following boundary-processClass pattern
 */
async function createAndUpdateProjects(currentBoundaryData: any[], campaignDetails: any, boundaries: any, targetConfig: any): Promise<void> {
    try {
        logger.info('Creating and updating projects...');
        
        // Get boundary children to type and parent map
        const boundaryChildrenToTypeAndParentMap: any = getBoundaryChildrenToTypeAndParentMap(boundaries, currentBoundaryData);
        
        // Prepare project creation context
        const { projectCreateBody, Projects } = await prepareProjectCreationContext(campaignDetails);
        
        // Topologically sort boundaries to ensure parent projects are created first
        const sortedBoundaryData = topologicallySortBoundaries(currentBoundaryData, boundaryChildrenToTypeAndParentMap);
        
        // Filter for creation (no uniqueIdAfterProcess) and updates (has uniqueIdAfterProcess)
        const sortedBoundaryDataForCreate = sortedBoundaryData.filter((d: any) => !d?.uniqueIdAfterProcess && (d?.status == dataRowStatuses.pending || d?.status == dataRowStatuses.failed));
        const sortedBoundaryDataForUpdate = sortedBoundaryData.filter((d: any) => d?.uniqueIdAfterProcess && (d?.status == dataRowStatuses.pending || d?.status == dataRowStatuses.failed));
        
        const useruuid = campaignDetails?.auditDetails?.createdBy;
        
        logger.info(`Processing ${sortedBoundaryDataForCreate.length} boundaries for project creation`);
        logger.info(`Processing ${sortedBoundaryDataForUpdate.length} boundaries for project updates`);
        
        // Process project creation in topological order (parents first)
        await processProjectCreationInOrder(sortedBoundaryDataForCreate, campaignDetails?.tenantId, campaignDetails?.campaignNumber, targetConfig, projectCreateBody, Projects, boundaryChildrenToTypeAndParentMap, useruuid);
        
        // Process project updates
        await processProjectUpdateInOrder(sortedBoundaryDataForUpdate, campaignDetails?.tenantId, campaignDetails?.campaignNumber, targetConfig, useruuid);
        
        logger.info('Project creation and updates completed successfully');
        
    } catch (error) {
        logger.error('Error in createAndUpdateProjects:', error);
        throw error;
    }
}

/**
 * Get boundary children to type and parent map from boundaries and current data
 */
function getBoundaryChildrenToTypeAndParentMap(boundaries: any[], currentBoundaryData: any[]) {
    const boundaryToProjectId = currentBoundaryData.reduce((acc: Record<string, string>, boundary: any) => {
        const code = boundary?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
        const id = boundary?.uniqueIdAfterProcess;

        if (code && id) {
            acc[code] = id;
        }

        return acc;
    }, {});
    
    const boundaryChildrenToTypeAndParentMap = boundaries.reduce(
        (acc: any, boundary: any) => {
            const code = boundary?.code;
            if (code) {
                acc[code] = {
                    type: boundary?.type || "",
                    parent: boundary?.parent || null,
                    projectId: boundaryToProjectId[code] || null
                };
            }
            return acc;
        },
        {}
    );
    return boundaryChildrenToTypeAndParentMap;
}

/**
 * Prepare project creation context with enriched project details
 */
async function prepareProjectCreationContext(campaignDetails: any) {
    const MdmsCriteria : any = {
        tenantId: campaignDetails?.tenantId,
        schemaCode: "HCM-PROJECT-TYPES.projectTypes",
        filters: {
            code: campaignDetails?.projectType
        }
    };

    const mdmsResponse = await searchMDMSDataViaV2Api(MdmsCriteria, true);
    if (!mdmsResponse?.mdms?.[0]?.data) {
        throw new Error(`Error in fetching project types from mdms`);
    }

    const Projects = enrichProjectDetailsFromCampaignDetails(campaignDetails, mdmsResponse?.mdms?.[0]?.data);
    const projectCreateBody = {
        RequestInfo: { ...defaultRequestInfo?.RequestInfo, userInfo: { uuid: campaignDetails?.auditDetails?.createdBy } },
        Projects
    };

    return { projectCreateBody, Projects };
}

/**
 * Topologically sort boundaries to ensure parent projects are created before child projects
 */
function topologicallySortBoundaries(currentBoundaryData: any[], boundaryMap: Record<string, { parent: string | null }>) {
    const graph: Record<string, string[]> = {};
    const inDegree: Record<string, number> = {};

    for (const bd of currentBoundaryData) {
        const code = bd?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
        const parent : any = boundaryMap?.[code]?.parent;

        if (!graph[parent]) graph[parent] = [];
        graph[parent].push(code);

        inDegree[code] = (inDegree[code] || 0) + 1;
        if (!(parent in inDegree)) inDegree[parent] = 0;
    }

    const queue = Object.entries(inDegree)
        .filter(([_, deg]) => deg === 0)
        .map(([code]) => code);

    const sortedCodes: string[] = [];
    while (queue.length) {
        const current = queue.shift();
        if (!current || current === "undefined") continue;
        sortedCodes.push(current);
        for (const neighbor of graph[current] || []) {
            inDegree[neighbor]--;
            if (inDegree[neighbor] === 0) queue.push(neighbor);
        }
    }

    const codeToDataMap = Object.fromEntries(
        currentBoundaryData.map(bd => [bd?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"], bd])
    );

    return sortedCodes.map(code => codeToDataMap[code]).filter(Boolean);
}

/**
 * Process project creation level-wise with batching for performance
 */
async function processProjectCreationInOrder(
    sortedBoundaryData: any[],
    tenantId: string,
    campaignNumber: string,
    targetConfig: any,
    projectCreateBody: any,
    Projects: any,
    boundaryMap: Record<string, { type: string; parent: string | null; projectId?: string }>,
    useruuid: string
) {
    logger.info("Processing project creation level-wise with batching");
    
    // Group boundaries by hierarchy level
    const boundariesByLevel = groupBoundariesByLevel(sortedBoundaryData, boundaryMap);
    
    logger.info(`Grouped boundaries into ${boundariesByLevel.length} levels`);
    
    // Process each level sequentially, but within each level process in batches with Promise.all
    for (let levelIndex = 0; levelIndex < boundariesByLevel.length; levelIndex++) {
        const levelBoundaries = boundariesByLevel[levelIndex];
        logger.info(`Processing level ${levelIndex + 1}: ${levelBoundaries.length} boundaries`);
        
        // Process this level in batches of 20 using Promise.all
        await processLevelInBatches(
            levelBoundaries, 
            tenantId, 
            campaignNumber, 
            targetConfig, 
            projectCreateBody, 
            Projects, 
            boundaryMap, 
            useruuid,
            levelIndex + 1
        );
        
        logger.info(`✅ Level ${levelIndex + 1} completed`);
    }
    
    logger.info("All levels project creation completed");
}

/**
 * Group boundaries by hierarchy level for parallel processing
 */
function groupBoundariesByLevel(
    sortedBoundaryData: any[], 
    boundaryMap: Record<string, { type: string; parent: string | null; projectId?: string }>
): any[][] {
    const boundariesByLevel: any[][] = [];
    const processedCodes = new Set<string>();
    
    // Get all boundary codes from current data
    const boundaryCodesInData = new Set(
        sortedBoundaryData.map(bd => bd?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"]).filter(Boolean)
    );
    
    // Process boundaries level by level
    // Find current level boundaries (top level in your current data set)
    let currentLevelBoundaries = sortedBoundaryData.filter(bd => {
        const code = bd?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
        const parent = boundaryMap?.[code]?.parent;
        // This is current level if parent doesn't exist OR parent is not in current data set
        return !parent || !boundaryCodesInData.has(parent);
    });
    
    while (currentLevelBoundaries.length > 0) {
        boundariesByLevel.push([...currentLevelBoundaries]);
        
        // Mark these as processed
        currentLevelBoundaries.forEach(bd => {
            const code = bd?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
            processedCodes.add(code);
        });
        
        // Find next level boundaries (whose parents are now processed)
        currentLevelBoundaries = sortedBoundaryData.filter(bd => {
            const code = bd?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
            const parent = boundaryMap?.[code]?.parent;
            
            return !processedCodes.has(code) && parent && processedCodes.has(parent);
        });
    }
    
    return boundariesByLevel;
}

/**
 * Process a level of boundaries in batches using Promise.all
 */
async function processLevelInBatches(
    levelBoundaries: any[],
    tenantId: string,
    campaignNumber: string,
    targetConfig: any,
    projectCreateBody: any,
    Projects: any,
    boundaryMap: Record<string, { type: string; parent: string | null; projectId?: string }>,
    useruuid: string,
    levelNumber: number
) {
    const BATCH_SIZE = 20;
    
    for (let i = 0; i < levelBoundaries.length; i += BATCH_SIZE) {
        const batch = levelBoundaries.slice(i, i + BATCH_SIZE);
        const batchNumber = Math.floor(i / BATCH_SIZE) + 1;
        const totalBatches = Math.ceil(levelBoundaries.length / BATCH_SIZE);
        
        logger.info(`Processing level ${levelNumber} batch ${batchNumber}/${totalBatches}: ${batch.length} projects`);
        
        // Create promises for parallel execution
        const batchPromises = batch.map(boundaryData => 
            createSingleProject(
                boundaryData,
                tenantId,
                campaignNumber,
                targetConfig,
                projectCreateBody,
                Projects,
                boundaryMap,
                useruuid
            )
        );
        
        // Execute batch in parallel
        const batchResults = await Promise.allSettled(batchPromises);
        
        // Process results
        let successCount = 0;
        let failureCount = 0;
        
        batchResults.forEach((result, index) => {
            if (result.status === 'fulfilled') {
                successCount++;
            } else {
                failureCount++;
                const boundaryCode = batch[index]?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
                logger.error(`Failed to create project for boundary ${boundaryCode}:`, result.reason);
            }
        });
        
        logger.info(`Level ${levelNumber} batch ${batchNumber}/${totalBatches} completed: ${successCount} success, ${failureCount} failed`);
        
        // Small delay between batches to avoid overwhelming the system
        if (i + BATCH_SIZE < levelBoundaries.length) {
            await new Promise(resolve => setTimeout(resolve, 100));
        }
    }
}

/**
 * Create a single project (extracted from the original loop)
 */
async function createSingleProject(
    boundaryData: any,
    tenantId: string,
    campaignNumber: string,
    targetConfig: any,
    projectCreateBody: any,
    Projects: any,
    boundaryMap: Record<string, { type: string; parent: string | null; projectId?: string }>,
    useruuid: string
): Promise<void> {
    const data = boundaryData?.data;
    const boundaryCode = data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
    
    try {
        // Clone project template for this boundary
        const projectTemplate = JSON.parse(JSON.stringify(Projects[0]));
        
        projectTemplate.address = {
            tenantId,
            boundary: boundaryCode,
            boundaryType: boundaryMap[boundaryCode]?.type,
        };

        const parent = boundaryMap?.[boundaryCode]?.parent;
        if (parent && boundaryMap?.[parent]?.projectId) {
            const parentProjectId = boundaryMap?.[parent]?.projectId;
            projectTemplate.parent = parentProjectId;
            if(!config.values.skipParentProjectConfirmation) {
                await confirmProjectParentCreation(tenantId, useruuid, parentProjectId);
            }
        }
        else if (parent && !boundaryMap?.[parent]?.projectId) {
            throw new Error(`Parent ${parent} of boundary ${boundaryCode} not found in boundaryMap`);
        }
        else {
            projectTemplate.parent = null;
        }

        projectTemplate.referenceID = campaignNumber;
        const targetMap: Record<string, number> = {};

        for (const beneficiary of targetConfig.beneficiaries) {
            for (const col of beneficiary.columns) {
                const value = data[col];
                if (value == 0 || value) {
                    targetMap[beneficiary.beneficiaryType] = (targetMap[beneficiary.beneficiaryType] || 0) + value;
                } else {
                    logger.warn(`Target missing for beneficiary ${beneficiary.beneficiaryType}, column ${col}, boundary ${boundaryCode}`);
                }
            }
        }

        projectTemplate.targets = Object.entries(targetMap).map(([key, val]) => ({
            beneficiaryType: key,
            targetNo: val
        }));

        // Clone request body for this project
        const requestBody = JSON.parse(JSON.stringify(projectCreateBody));
        requestBody.Projects = [projectTemplate];

        const response = await httpRequest(
            config.host.projectHost + config.paths.projectCreate,
            requestBody,
            undefined,
            undefined,
            undefined,
            undefined,
            undefined,
            true
        );

        const createdProjectId = response?.Project?.[0]?.id;
        if (createdProjectId) {
            logger.info(`✅ Project created: ${response?.Project[0]?.name} for boundary ${boundaryCode}`);
            boundaryMap[boundaryCode].projectId = createdProjectId;
            boundaryData.uniqueIdAfterProcess = createdProjectId;
            boundaryData.status = dataRowStatuses.completed;
            await produceModifiedMessages({ datas: [boundaryData] }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
        }
        else {
            throw new Error(`Failed to create project for boundary ${boundaryCode}`);
        }
    } catch (error) {
        logger.error(`Error creating project for boundary ${boundaryCode}:`, error);
        boundaryData.status = dataRowStatuses.failed;
        await produceModifiedMessages({ datas: [boundaryData] }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
        throw error;
    }
}

/**
 * Process project updates in order
 */
async function processProjectUpdateInOrder(
    sortedBoundaryData: any[],
    tenantId: string,
    campaignNumber: string,
    targetConfig: any,
    useruuid: string
) {
    logger.info("Processing project update in order");
    for (const boundaryData of sortedBoundaryData) {
        const data = boundaryData?.data;
        const boundaryCode = data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
        const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
        RequestInfo.userInfo.uuid = useruuid;
        try {
            const projectSearchResponse =
                await fetchProjectsWithBoundaryCodeAndReferenceId(
                    boundaryCode,
                    tenantId,
                    campaignNumber,
                    RequestInfo
                );
            const projectToUpdate = projectSearchResponse?.Project?.[0];
            const targetMap: Record<string, number> = {};

            for (const beneficiary of targetConfig.beneficiaries) {
                for (const col of beneficiary.columns) {
                    const value = data[col];
                    if (value == 0 || value) {
                        targetMap[beneficiary.beneficiaryType] = (targetMap[beneficiary.beneficiaryType] || 0) + value;
                    } else {
                        logger.warn(`Target missing for beneficiary ${beneficiary.beneficiaryType}, column ${col}, boundary ${boundaryCode}`);
                    }
                }
            }
            if(projectToUpdate?.targets?.length > 0) {
                for (const target of projectToUpdate?.targets) {
                    const beneficiaryType = target?.beneficiaryType;
                    if(targetMap[beneficiaryType]) {
                        target.targetNo = targetMap[beneficiaryType];
                    }
                }
            }

            for(const key in targetMap) {
                if(!projectToUpdate?.targets?.find((target: any) => target.beneficiaryType === key)) {
                    projectToUpdate.targets.push({
                        beneficiaryType: key,
                        targetNo: targetMap[key]
                    });
                }
            }
            const response = await httpRequest(
                config.host.projectHost + config.paths.projectUpdate,
                {
                    RequestInfo,
                    Projects: [projectToUpdate]
                },
                undefined,
                undefined,
                undefined,
                undefined,
                undefined,
                true
            );

            const updatedProjectId = response?.Project?.[0]?.id;
            if (updatedProjectId) {
                logger.info(`Project updated successfully for boundary ${boundaryCode}`);
                boundaryData.status = dataRowStatuses.completed;
                await produceModifiedMessages({ datas: [boundaryData] }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
            }
            else {
                throw new Error(`Failed to update project for boundary ${boundaryCode}`);
            }
        } catch (error) {
            console.error(`Error while updating project for boundary ${boundaryCode}: ${error}`);
            boundaryData.status = dataRowStatuses.failed;
            await produceModifiedMessages({ datas: [boundaryData] }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
            throw error;
        }
    }
    logger.info("Project update in order completed");
}

/**
 * Create facilities via Kafka batch processing
 */
async function createFacilitiesFromFacilityData(campaignDetails: any, tenantId: string): Promise<void> {
    try {
        const campaignNumber = campaignDetails.campaignNumber;
        const campaignId = campaignDetails.id;
        const parentCampaignId = campaignDetails.parentId;
        const userUuid = campaignDetails?.auditDetails?.createdBy;
        
        logger.info(`Creating facilities for campaign: ${campaignNumber} via Kafka batches`);
        
        // Get all existing facilities for this campaign from campaign data table
        const allCurrentFacilities = await getRelatedDataWithCampaign("facility", campaignNumber, tenantId);
        
        if (allCurrentFacilities.length === 0) {
            logger.info('No facility data found for facility creation');
            return;
        }
        
        logger.info(`Found ${allCurrentFacilities.length} facility records in campaign data`);
        
        // Filter facilities that need creation (pending or failed status)
        const facilitiesToCreate = allCurrentFacilities.filter(
            (f: any) => f?.status === dataRowStatuses.pending || f?.status === dataRowStatuses.failed
        );
        
        if (facilitiesToCreate.length === 0) {
            logger.info('No facilities require creation');
            return;
        }
        
        logger.info(`${facilitiesToCreate.length} facilities to create via Kafka batches`);
        
        // Send facility batches to Kafka topic for processing
        const BATCH_SIZE = 30;
        const totalBatches = Math.ceil(facilitiesToCreate.length / BATCH_SIZE);
        
        for (let i = 0; i < facilitiesToCreate.length; i += BATCH_SIZE) {
            const batch = facilitiesToCreate.slice(i, i + BATCH_SIZE);
            const batchNumber = Math.floor(i / BATCH_SIZE) + 1;
            
            // Create facility data map for this batch
            const facilityData: Record<string, any> = {};
            batch.forEach(facility => {
                const uniqueIdentifier = facility.uniqueIdentifier;
                facilityData[uniqueIdentifier] = facility; // campaignRecord
            });
            
            // Send batch to Kafka
            const batchMessage = {
                tenantId,
                campaignNumber,
                campaignId,
                parentCampaignId,
                useruuid: userUuid,
                facilityData,
                batchNumber,
                totalBatches
            };
            
            logger.info(`Sending facility batch ${batchNumber}/${totalBatches} to Kafka: ${batch.length} facilities`);
            
            // Use random UUID as partition key for load balancing across consumers
            const partitionKey = uuidv4();
            
            await produceModifiedMessages(
                batchMessage, 
                config.kafka.KAFKA_FACILITY_CREATE_BATCH_TOPIC, 
                tenantId,
                partitionKey
            );
        }
        
        logger.info(`All ${totalBatches} facility batches sent to Kafka for processing`);
        
    } catch (error) {
        logger.error('Error sending facilities to Kafka:', error);
        throw error;
    }
}

/**
 * Start all mappings (resource, facility, user) in batches via Kafka
 */
async function startAllMappingsInBatches(
    campaignDetails: any,
    useruuid: string,
    tenantId: string
): Promise<void> {
    try {
        const campaignNumber = campaignDetails.campaignNumber;
        logger.info(`Starting all mappings for campaign: ${campaignNumber}`);
        
        // Get all mappings for all types - both toBeMapped and toBeDeMapped
        const allMappings: any[] = [];
        
        // Resource mappings
        const resourceToBeMapped = await getMappingDataRelatedToCampaign('resource', campaignNumber, tenantId, mappingStatuses.toBeMapped);
        const resourceToBeDeMapped = await getMappingDataRelatedToCampaign('resource', campaignNumber, tenantId, mappingStatuses.toBeDeMapped);
        allMappings.push(...resourceToBeMapped, ...resourceToBeDeMapped);
        
        // Facility mappings
        const facilityToBeMapped = await getMappingDataRelatedToCampaign('facility', campaignNumber, tenantId, mappingStatuses.toBeMapped);
        const facilityToBeDeMapped = await getMappingDataRelatedToCampaign('facility', campaignNumber, tenantId, mappingStatuses.toBeDeMapped);
        allMappings.push(...facilityToBeMapped, ...facilityToBeDeMapped);
        
        // User mappings
        const userToBeMapped = await getMappingDataRelatedToCampaign('user', campaignNumber, tenantId, mappingStatuses.toBeMapped);
        const userToBeDeMapped = await getMappingDataRelatedToCampaign('user', campaignNumber, tenantId, mappingStatuses.toBeDeMapped);
        allMappings.push(...userToBeMapped, ...userToBeDeMapped);
        
        if (allMappings.length === 0) {
            logger.info('No mappings found to process');
            return;
        }
        
        logger.info(`Found ${allMappings.length} total mappings to process`);
        logger.info(`Resource: ${resourceToBeMapped.length + resourceToBeDeMapped.length}, Facility: ${facilityToBeMapped.length + facilityToBeDeMapped.length}, User: ${userToBeMapped.length + userToBeDeMapped.length}`);
        
        // Send mappings in batches of 30
        const BATCH_SIZE = 30;
        const totalBatches = Math.ceil(allMappings.length / BATCH_SIZE);
        
        for (let i = 0; i < allMappings.length; i += BATCH_SIZE) {
            const batch = allMappings.slice(i, i + BATCH_SIZE);
            const batchNumber = Math.floor(i / BATCH_SIZE) + 1;
            
            // Send batch to Kafka
            const batchMessage = {
                tenantId,
                campaignNumber,
                campaignId: campaignDetails.id,
                useruuid,
                mappings: batch,
                batchNumber,
                totalBatches
            };
            
            logger.info(`Sending mapping batch ${batchNumber}/${totalBatches} to Kafka: ${batch.length} mappings`);
            
            // Use random UUID as partition key for load balancing
            const partitionKey = uuidv4();
            
            await produceModifiedMessages(
                batchMessage,
                config.kafka.KAFKA_MAPPING_BATCH_TOPIC,
                tenantId,
                partitionKey
            );
        }
        
        logger.info(`All ${totalBatches} mapping batches sent to Kafka for processing`);
        
    } catch (error) {
        logger.error('Error starting mappings in batches:', error);
        throw error;
    }
}

/**
 * Create users via Kafka batch processing
 */
async function createUsersFromUserData(campaignDetails: any, tenantId: string): Promise<void> {
    try {
        const campaignNumber = campaignDetails.campaignNumber;
        const campaignId = campaignDetails.id;
        const parentCampaignId = campaignDetails.parentId;
        const userUuid = campaignDetails?.auditDetails?.createdBy;
        
        logger.info(`Creating users for campaign: ${campaignNumber} via Kafka batches`);
        
        // Get all existing users for this campaign from campaign data table
        const allCurrentUsers = await getRelatedDataWithCampaign("user", campaignNumber, tenantId);
        
        if (allCurrentUsers.length === 0) {
            logger.info('No user data found for user creation');
            return;
        }
        
        logger.info(`Found ${allCurrentUsers.length} user records in campaign data`);
        
        // Filter users that need creation (pending or failed status)
        const usersToCreate = allCurrentUsers.filter(
            (u: any) => u?.status === dataRowStatuses.pending || u?.status === dataRowStatuses.failed
        );
        
        if (usersToCreate.length === 0) {
            logger.info('No users require creation');
            return;
        }
        
        logger.info(`${usersToCreate.length} users to create via Kafka batches`);
        
        // Send user batches to Kafka topic for processing
        const BATCH_SIZE = 30;
        const totalBatches = Math.ceil(usersToCreate.length / BATCH_SIZE);
        
        for (let i = 0; i < usersToCreate.length; i += BATCH_SIZE) {
            const batch = usersToCreate.slice(i, i + BATCH_SIZE);
            const batchNumber = Math.floor(i / BATCH_SIZE) + 1;
            
            // Create user data map for this batch
            const userData: Record<string, any> = {};
            batch.forEach(user => {
                const uniqueIdentifier = user.uniqueIdentifier;
                userData[uniqueIdentifier] = user; // campaignRecord
            });
            
            // Send batch to Kafka
            const batchMessage = {
                tenantId,
                campaignNumber,
                campaignId,
                parentCampaignId,
                useruuid: userUuid,
                userData,
                batchNumber,
                totalBatches
            };
            
            logger.info(`Sending user batch ${batchNumber}/${totalBatches} to Kafka: ${batch.length} users`);
            
            // Use random UUID as partition key for load balancing across consumers
            const partitionKey = uuidv4();
            
            await produceModifiedMessages(
                batchMessage, 
                config.kafka.KAFKA_USER_CREATE_BATCH_TOPIC, 
                tenantId,
                partitionKey
            );
        }
        
        logger.info(`All ${totalBatches} user batches sent to Kafka for processing`);
        
    } catch (error) {
        logger.error('Error sending users to Kafka:', error);
        throw error;
    }
}

