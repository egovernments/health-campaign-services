import { logger } from '../utils/logger';
import { producer } from './Producer';



async function produceModifiedMessages(modifiedMessages: any[], topic: any) {
    return new Promise<void>((resolve, reject) => {
        const payloads = [
            {
                topic: topic,
                messages: JSON.stringify(modifiedMessages),
            },
        ];

        producer.send(payloads, (err) => {
            if (err) {
                logger.info(`Producer Error: ${JSON.stringify(err)}`);
                reject(err);
            } else {
                logger.info('Produced modified messages successfully.');
                resolve();
            }
        });
    });
}

export { produceModifiedMessages }
