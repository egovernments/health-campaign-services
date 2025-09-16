import { logger } from './logger';
import { searchSheetData } from './excelIngestionUtils';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { getRelatedDataWithCampaign, getMappingDataRelatedToCampaign } from './genericUtils';
import { produceModifiedMessages } from '../kafka/Producer';
import { dataRowStatuses, mappingStatuses, usageColumnStatus } from '../config/constants';
import { searchMDMSDataViaV2Api, searchBoundaryRelationshipData } from '../api/coreApis';
import { populateBoundariesRecursively, getLocalizedName } from './campaignUtils';
import { getLocalisationModuleName } from './localisationUtils';
import Localisation from '../controllers/localisationController/localisation.controller';
import config from '../config';

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
        return {};
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
        
        // Log file information
        if (messageObject.fileStoreId) {
            logger.info(`Input File Store ID: ${messageObject.fileStoreId}`);
        }
        
        if (messageObject.processedFileStoreId) {
            logger.info(`Processed File Store ID: ${messageObject.processedFileStoreId}`);
        }
        
        // Log audit details
        if (messageObject.auditDetails) {
            logger.info(`Created Time: ${new Date(messageObject.auditDetails.createdTime).toISOString()}`);
            logger.info(`Modified Time: ${new Date(messageObject.auditDetails.lastModifiedTime).toISOString()}`);
            logger.info(`Created By: ${messageObject.auditDetails.createdBy}`);
            logger.info(`Modified By: ${messageObject.auditDetails.lastModifiedBy}`);
        }
        
        // Log additional details (includes error information if failed)
        if (messageObject.additionalDetails) {
            logger.info('Additional Details:');
            logger.info(JSON.stringify(messageObject.additionalDetails, null, 2));
            
            // Check validation status first
            const validationStatus = messageObject?.additionalDetails?.validationStatus;
            const totalRowsProcessed = messageObject?.additionalDetails?.totalRowsProcessed || 0;
            const totalErrors = messageObject?.additionalDetails?.totalErrors || 0;
            
            logger.info(`Validation Status: ${validationStatus}`);
            logger.info(`Total Rows Processed: ${totalRowsProcessed}`);
            logger.info(`Total Errors: ${totalErrors}`);
            
            // Only proceed if validation status is valid
            if (validationStatus !== 'valid') {
                logger.warn('=== VALIDATION STATUS IS NOT VALID - STOPPING PROCESSING ===');
                logger.warn(`Validation Status: ${validationStatus}, cannot proceed with campaign data processing`);
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
        } else {
            logger.warn('No additional details found in message object - cannot validate processing status');
            return;
        }
        
        // Fetch localization data first
        logger.info('=== FETCHING LOCALIZATION DATA ===');
        const locale = 'en_IN'; // Default locale
        const localizationMap = await fetchLocalizationData(messageObject.tenantId, messageObject.referenceId, locale);
        logger.info(`Localization data fetched with ${Object.keys(localizationMap).length} keys`);
        
        // Search temp data and process campaign boundaries
        logger.info('=== SEARCHING TEMP DATA AND PROCESSING CAMPAIGN BOUNDARIES ===');
        
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
                        messageObject.referenceId,
                        messageObject.fileStoreId,
                        localizationMap
                    ),
                    processCampaignFacilitiesFromExcelData(
                        messageObject.tenantId,
                        messageObject.referenceId,
                        messageObject.fileStoreId,
                        localizationMap
                    ),
                    processCampaignUsersFromExcelData(
                        messageObject.tenantId,
                        messageObject.referenceId,
                        messageObject.fileStoreId,
                        localizationMap
                    )
                ]);
                logger.info('=== ALL CAMPAIGN DATA PROCESSING COMPLETED ===');
            } else {
                logger.warn('No temp data found for the given referenceId and fileStoreId');
            }
        } else {
            logger.error('Missing required fields (referenceId, fileStoreId, tenantId) to search temp data');
        }
        
        logger.info('=== END OF HCM PROCESSING RESULT ===');
        
    } catch (error) {
        logger.error('Error handling HCM processing result:', error);
        throw error;
    }
}

/**
 * Process campaign boundaries from excel data using existing boundary processing logic
 */
async function processCampaignBoundariesFromExcelData(
    tenantId: string,
    campaignId: string,
    fileStoreId: string,
    localizationMap: Record<string, string>
): Promise<void> {
    try {
        logger.info('=== PROCESSING CAMPAIGN BOUNDARIES FROM EXCEL DATA ===');
        
        // Step 1: Search campaign details using referenceId as campaignNumber
        const campaignSearchCriteria = {
            tenantId,
            ids: [campaignId]
        };

        const campaignResponse = await searchProjectTypeCampaignService(campaignSearchCriteria);
        const campaignDetails = campaignResponse?.CampaignDetails?.[0];

        if (!campaignDetails) {
            logger.error(`No campaign found with campaignId: ${campaignId}`);
            return;
        }
        const campaignNumber = campaignDetails.campaignNumber;
        logger.info(`Found campaign: ${campaignDetails.campaignName} (${campaignDetails.id})`);

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

        // Step 5: Extract boundary data from boundary hierarchy sheet
        const boundaryDataList = extractBoundaryDataFromBoundarySheet(boundarySheetData, targetColumns);

        if (boundaryDataList.length === 0) {
            logger.warn('No boundary hierarchy data found in sheets');
            return;
        }

        // Step 5: Process boundary data and update eg_cm_campaign_data
        await processBoundaryDataInCampaignTable(campaignNumber, tenantId, boundaryDataList, targetColumns);

        logger.info('=== CAMPAIGN BOUNDARY PROCESSING COMPLETED ===');

    } catch (error) {
        logger.error('Error processing campaign boundaries from excel data:', error);
        throw error;
    }
}

/**
 * Extract boundary data from boundary hierarchy sheet records
 */
function extractBoundaryDataFromBoundarySheet(sheetData: any[], targetColumns: string[]): any[] {
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
    campaignId: string,
    fileStoreId: string,
    localizationMap: Record<string, string>
): Promise<void> {
    try {
        logger.info('=== PROCESSING CAMPAIGN FACILITIES FROM EXCEL DATA ===');
        
        // Search campaign details
        const campaignSearchCriteria = {
            tenantId,
            ids: [campaignId]
        };

        const campaignResponse = await searchProjectTypeCampaignService(campaignSearchCriteria);
        const campaignDetails = campaignResponse?.CampaignDetails?.[0];

        if (!campaignDetails) {
            logger.error(`No campaign found with campaignId: ${campaignId}`);
            return;
        }
        
        const campaignNumber = campaignDetails.campaignNumber;
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
        if (usage === 'Active' && boundaryCodes) {
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
    campaignId: string,
    fileStoreId: string,
    localizationMap: Record<string, string>
): Promise<void> {
    try {
        logger.info('=== PROCESSING CAMPAIGN USERS FROM EXCEL DATA ===');
        
        // Search campaign details
        const campaignSearchCriteria = {
            tenantId,
            ids: [campaignId]
        };

        const campaignResponse = await searchProjectTypeCampaignService(campaignSearchCriteria);
        const campaignDetails = campaignResponse?.CampaignDetails?.[0];

        if (!campaignDetails) {
            logger.error(`No campaign found with campaignId: ${campaignId}`);
            return;
        }
        
        const campaignNumber = campaignDetails.campaignNumber;
        logger.info(`Processing users for campaign: ${campaignDetails.campaignName}`);

        // Search users sheet data
        const userSheetName = getLocalizedSheetName('HCM_ADMIN_CONSOLE_USER_LIST', localizationMap);
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

        // Process users and their mappings
        await processUserDataAndMappings(campaignNumber, tenantId, userSheetData);

        logger.info('=== CAMPAIGN USERS PROCESSING COMPLETED ===');

    } catch (error) {
        logger.error('Error processing campaign users from excel data:', error);
        throw error;
    }
}

/**
 * Process user data and update campaign data and mapping tables
 */
async function processUserDataAndMappings(
    campaignNumber: string,
    tenantId: string,
    sheetData: any[]
): Promise<void> {
    const PHONE_KEY = 'HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER';
    const BOUNDARY_KEY = 'HCM_ADMIN_CONSOLE_BOUNDARY_CODE';
    const USAGE_KEY = 'HCM_ADMIN_CONSOLE_USER_USAGE';
    const USERNAME_KEY = 'UserName';
    const PASSWORD_KEY = 'Password';
    
    // Get existing user data
    const existingUsers = await getRelatedDataWithCampaign('user', campaignNumber, tenantId);
    logger.info(`Found ${existingUsers.length} existing user records in eg_cm_campaign_data`);
    
    // Create map for existing users
    const existingUserMap = new Map(
        existingUsers.map((u: any) => [String(u?.data?.[PHONE_KEY]), u])
    );
    
    // Process users from sheet
    const newUsers: any[] = [];
    const updatedUsers: any[] = [];
    const userBoundaryMappings: any[] = [];
    
    sheetData.forEach(record => {
        const rowJson = record.rowjson || record.rowJson || {};
        const phoneNumber = String(rowJson[PHONE_KEY]);
        const boundaryCodes = rowJson[BOUNDARY_KEY];
        const usage = rowJson[USAGE_KEY];
        
        if (!phoneNumber || phoneNumber === 'undefined') {
            logger.warn('No phone number found in row');
            return;
        }
        
        const existingEntry = existingUserMap.get(phoneNumber);
        
        if (!existingEntry) {
            // New user
            newUsers.push({
                campaignNumber,
                data: rowJson,
                type: 'user',
                uniqueIdentifier: phoneNumber,
                uniqueIdAfterProcess: null,
                status: dataRowStatuses.pending
            });
            logger.info(`New user entry: ${phoneNumber}`);
        } else {
            // Check if data has changed
            let hasChanges = false;
            const existingData = existingEntry.data;
            
            // Update boundary codes if different
            if (boundaryCodes !== existingData[BOUNDARY_KEY]) {
                existingData[BOUNDARY_KEY] = boundaryCodes;
                hasChanges = true;
            }
            
            // Update usage if different
            if (usage !== existingData[USAGE_KEY]) {
                existingData[USAGE_KEY] = usage;
                hasChanges = true;
            }
            
            // Update username/password if provided and different
            if (rowJson[USERNAME_KEY] && rowJson[USERNAME_KEY] !== existingData[USERNAME_KEY]) {
                existingData[USERNAME_KEY] = rowJson[USERNAME_KEY];
                hasChanges = true;
            }
            
            if (rowJson[PASSWORD_KEY] && rowJson[PASSWORD_KEY] !== existingData[PASSWORD_KEY]) {
                existingData[PASSWORD_KEY] = rowJson[PASSWORD_KEY];
                hasChanges = true;
            }
            
            if (hasChanges) {
                updatedUsers.push(existingEntry);
                logger.info(`Updated user entry: ${phoneNumber}`);
            }
        }
        
        // Prepare boundary mappings for active users
        if (usage === usageColumnStatus.active && boundaryCodes) {
            const boundaryList = boundaryCodes.split(',').map((b: string) => b.trim()).filter(Boolean);
            boundaryList.forEach((boundaryCode: string) => {
                userBoundaryMappings.push({
                    phoneNumber,
                    boundaryCode,
                    active: true
                });
            });
        }
    });
    
    // Persist user data changes
    if (newUsers.length > 0) {
        logger.info(`Persisting ${newUsers.length} new user entries`);
        await persistDataInBatches(newUsers, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC, tenantId);
    }
    
    if (updatedUsers.length > 0) {
        logger.info(`Updating ${updatedUsers.length} existing user entries`);
        await persistDataInBatches(updatedUsers, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
    }
    
    // Process user-boundary mappings
    if (userBoundaryMappings.length > 0) {
        await processUserBoundaryMappings(campaignNumber, tenantId, userBoundaryMappings);
    }
    
    logger.info(`User processing completed: ${newUsers.length} new, ${updatedUsers.length} updated`);
}

/**
 * Process user-boundary mappings
 */
async function processUserBoundaryMappings(
    campaignNumber: string,
    tenantId: string,
    mappings: any[]
): Promise<void> {
    // Get existing mappings
    const existingMappings = await getMappingDataRelatedToCampaign('user', campaignNumber, tenantId);
    const existingMappingSet = new Set(
        existingMappings.map((m: any) => `${m.uniqueIdentifierForData}#${m.boundaryCode}`)
    );
    
    // Identify new mappings
    const newMappings: any[] = [];
    const toBeDemapped: any[] = [];
    
    // Check for new mappings from sheet
    mappings.forEach(mapping => {
        const key = `${mapping.phoneNumber}#${mapping.boundaryCode}`;
        if (!existingMappingSet.has(key)) {
            newMappings.push({
                campaignNumber,
                type: 'user',
                uniqueIdentifierForData: mapping.phoneNumber,
                boundaryCode: mapping.boundaryCode,
                mappingId: null,
                status: mappingStatuses.toBeMapped
            });
        }
    });
    
    // Check for mappings to be demapped (exist in DB but not in sheet)
    const sheetMappingSet = new Set(
        mappings.map(m => `${m.phoneNumber}#${m.boundaryCode}`)
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
        logger.info(`Creating ${newMappings.length} new user-boundary mappings`);
        await persistDataInBatches(newMappings, config.kafka.KAFKA_SAVE_MAPPING_DATA_TOPIC, tenantId);
    }
    
    if (toBeDemapped.length > 0) {
        logger.info(`Marking ${toBeDemapped.length} user-boundary mappings for demapping`);
        await persistDataInBatches(toBeDemapped, config.kafka.KAFKA_UPDATE_MAPPING_DATA_TOPIC, tenantId);
    }
}