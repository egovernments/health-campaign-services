import { ConsumerGroup, ConsumerGroupOptions, Message } from 'kafka-node';
import config from '../config';
import { getFormattedStringForDebug, logger } from '../utils/logger';
import { shutdownGracefully } from '../utils/genericUtils';
import { handleCampaignMapping } from '../utils/campaignMappingUtils';
import { handleCampaignProcessing, handleCampaignSubProcessing } from '../utils/campaignUtils';

// Kafka Configuration
const kafkaConfig: ConsumerGroupOptions = {
    kafkaHost: config?.host?.KAFKA_BROKER_HOST,
    groupId: 'project-factory',
    autoCommit: true,
    autoCommitIntervalMs: 5000,
    fromOffset: 'latest',
};

// Topic Names
const topicNames = [
    config.kafka.KAFKA_START_CAMPAIGN_MAPPING_TOPIC,
    config.kafka.KAFKA_PROCESS_HANDLER_TOPIC,
    config.kafka.KAFKA_SUB_PROCESS_HANDLER_TOPIC
];

// Consumer Group Initialization
const consumerGroup = new ConsumerGroup(kafkaConfig, topicNames);

// Kafka Listener
export function listener() {
    consumerGroup.on('message', async (message: Message) => {
        try {
            const messageObject = JSON.parse(message.value?.toString() || '{}');
            logger.info(`KAFKA :: LISTENER :: Received a message from topic ${message.topic}`);
            logger.debug(`KAFKA :: LISTENER :: Message: ${getFormattedStringForDebug(messageObject)}`);

            switch (message.topic) {
                case config.kafka.KAFKA_START_CAMPAIGN_MAPPING_TOPIC:
                    handleCampaignMapping(messageObject);
                    break;
                case config.kafka.KAFKA_PROCESS_HANDLER_TOPIC:
                    handleCampaignProcessing(messageObject);
                    break;
                case config.kafka.KAFKA_SUB_PROCESS_HANDLER_TOPIC:
                    handleCampaignSubProcessing(messageObject);
                    break;
                default:
                    logger.warn(`Unhandled topic: ${message.topic}`);
            }
        } catch (error) {
            logger.error(`KAFKA :: LISTENER :: Error processing message: ${error}`);
            console.error(error);
        }
    });

    consumerGroup.on('error', (err) => {
        logger.error(`Consumer Error: ${err}`);
        shutdownGracefully();
    });

    consumerGroup.on('offsetOutOfRange', (err) => {
        logger.error(`Offset out of range error: ${err}`);
    });
}
