import { logger } from './logger';
import { searchSheetData, formatSheetDataForDisplay } from './excelIngestionUtils';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { getRelatedDataWithCampaign } from './genericUtils';
import { produceModifiedMessages } from '../kafka/Producer';
import { dataRowStatuses } from '../config/constants';
import { searchMDMSDataViaV2Api, searchBoundaryRelationshipData } from '../api/coreApis';
import { populateBoundariesRecursively } from './campaignUtils';
import config from '../config';

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
        }
        
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
                logger.info('=== TEMP DATA RETRIEVED SUCCESSFULLY ===');
                const formattedData = formatSheetDataForDisplay(tempData);
                console.log(formattedData);
                logger.info(formattedData);
                
                // Process campaign boundaries using existing logic
                await processCampaignBoundariesFromExcelData(
                    messageObject.tenantId,
                    messageObject.referenceId,
                    messageObject.fileStoreId
                );
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
    fileStoreId: string
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
        logger.info('Searching HCM_CONSOLE_BOUNDARY_HIERARCHY sheet data...');
        const boundarySheetData = await searchSheetData(
            tenantId, 
            campaignId, 
            fileStoreId,
            null,
            'HCM_CONSOLE_BOUNDARY_HIERARCHY'
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