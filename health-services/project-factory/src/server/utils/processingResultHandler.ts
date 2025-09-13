import { logger } from './logger';

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
        
        // Log complete message for debugging
        logger.debug('Complete Processing Result:');
        logger.debug(JSON.stringify(messageObject, null, 2));
        
        logger.info('=== END OF HCM PROCESSING RESULT ===');
        
        // TODO: Add actual processing logic here based on requirements
        // For now, this handler just logs the received data
        
    } catch (error) {
        logger.error('Error handling HCM processing result:', error);
        throw error;
    }
}