import { logger } from './logger';
import { produceModifiedMessages } from '../kafka/Producer';
import config from '../config';
import { enrichAndPersistCampaignWithError } from './campaignUtils';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';

/**
 * Send campaign failure message to Kafka topic
 */
export async function sendCampaignFailureMessage(
    campaignId: string,
    tenantId: string,
    error: any
): Promise<void> {
    try {
        const failureMessage = {
            campaignId,
            tenantId,
            error: error.message || error.toString(),
            timestamp: new Date().toISOString()
        };
        
        logger.info(`Sending campaign failure message for campaign: ${campaignId}`);
        
        await produceModifiedMessages(
            failureMessage,
            config.kafka.KAFKA_CAMPAIGN_MARK_FAILED_TOPIC,
            tenantId
        );
        
        logger.info(`Campaign failure message sent successfully for campaign: ${campaignId}`);
        
    } catch (sendError) {
        logger.error('Error sending campaign failure message:', sendError);
        // Don't throw here as we don't want to fail the original batch processing
    }
}

/**
 * Handle campaign failure message from Kafka - marks campaign as failed and parent as active
 */
export async function handleCampaignFailure(messageObject: any) {
    try {
        logger.info('=== PROCESSING CAMPAIGN FAILURE MESSAGE ===');
        
        const { campaignId, tenantId, error } = messageObject;
        
        logger.info(`Marking campaign as failed: ${campaignId}`);
        
        // Search for campaign details
        logger.info(`Searching for campaign details: ${campaignId}`);
        const campaignSearchCriteria = {
            tenantId: tenantId,
            ids: [campaignId]
        };
        const campaignResponse = await searchProjectTypeCampaignService(campaignSearchCriteria);
        const campaignDetails = campaignResponse?.CampaignDetails?.[0];
        
        if (!campaignDetails) {
            logger.error(`Campaign not found with ID: ${campaignId}`);
            return;
        }
        
        logger.info(`Found campaign: ${campaignDetails.campaignName}`);
        
        // Create mock request body with campaign details
        const mockRequestBody = {
            CampaignDetails: campaignDetails,
            RequestInfo: { 
                userInfo: { uuid: campaignDetails?.auditDetails?.createdBy }
            }
        };
        
        // Use the existing error handling function to mark campaign as failed
        const campaignError = new Error(`${error}`);
        await enrichAndPersistCampaignWithError(mockRequestBody, campaignError);
        
        logger.info(`Campaign ${campaignId} marked as failed successfully`);
        
    } catch (handlingError) {
        logger.error('Error handling campaign failure message:', handlingError);
    }
}