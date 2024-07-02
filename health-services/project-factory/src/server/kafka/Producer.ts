import config from '../config'; // Importing configuration settings
import { Producer, KafkaClient } from 'kafka-node'; // Importing Producer and KafkaClient from 'kafka-node' library
import { logger } from "../utils/logger";

// Creating a new Kafka client instance using the configured Kafka broker host
const kafkaClient = new KafkaClient({
    kafkaHost: config?.host?.KAFKA_BROKER_HOST, // Configuring Kafka broker host
    connectRetryOptions: { retries: 1 }, // Configuring connection retry options
});

// Creating a new Kafka producer instance using the Kafka client
const producer = new Producer(kafkaClient, { partitionerType: 2 }); // Using partitioner type 2

// Event listener for 'ready' event, indicating that the producer is ready to send messages
producer.on('ready', () => {
    logger.info('Producer is ready'); // Log message indicating producer is ready
});

// Event listener for 'error' event, indicating that the producer encountered an error
producer.on('error', (err) => {
    logger.error('Producer is in error state'); // Log message indicating producer is in error state
    console.error(err.stack || err); // Log the error stack or message
});

export { producer }; // Exporting the producer instance for external use
