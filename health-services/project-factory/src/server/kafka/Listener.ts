import { ConsumerGroup, ConsumerGroupOptions, Message } from 'kafka-node';
import config from '../config';
import { getFormattedStringForDebug, logger } from '../utils/logger';
import { shutdownGracefully } from '../utils/genericUtils';
import { handleCampaignMapping } from '../utils/campaignMappingUtils';

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
    config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC,
    config.kafka.KAFKA_TEST_TOPIC
];

// Consumer Group Initialization
const consumerGroup = new ConsumerGroup(kafkaConfig, topicNames);

// Kafka Listener
export function listener() {
    consumerGroup.on('message', async (message: Message) => {
        try {
            const messageObject = JSON.parse(message.value?.toString() || '{}');

            switch (message.topic) {
                case config.kafka.KAFKA_START_CAMPAIGN_MAPPING_TOPIC:
                    await handleCampaignMapping(messageObject);
                    break;
                // case config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC:
                //     await handleTaskForCampaign(messageObject);
                //     break;
                default:
                    logger.warn(`Unhandled topic: ${message.topic}`);
            }

            logger.info(`KAFKA :: LISTENER :: Received a message from topic ${message.topic}`);
            logger.debug(`KAFKA :: LISTENER :: Message: ${getFormattedStringForDebug(messageObject)}`);
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
