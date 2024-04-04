import config from '../config';
import { Producer, KafkaClient } from 'kafka-node';

const kafkaClient = new KafkaClient({
    kafkaHost: config.KAFKA_BROKER_HOST,
    connectRetryOptions: { retries: 1 },
});

const producer = new Producer(kafkaClient, { partitionerType: 2 });

producer.on('ready', () => {
    console.log('Producer is ready');
});

producer.on('error', (err) => {
    console.log('Producer is in error state');
    console.log(err.stack || err);
});

export { producer };