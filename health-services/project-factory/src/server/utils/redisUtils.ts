import Redis from "ioredis";
import config from "../config";
import { logger } from "./logger";

let redis: Redis;

function createRedisInstance(): Redis {
    const client = new Redis({
        host: config.host.redisHost,
        port: parseInt(config.cacheValues.redisPort),
        retryStrategy(times) {
            if (times > 1) {
                return null;
            }
            return 500;
        },
        maxRetriesPerRequest: 1,
        reconnectOnError(err) {
            return false;
        },
    });

    client.on("connect", () => {
        logger.info(`Successfully connected to Redis!  host :: ${config.host.redisHost} & port :: ${config.cacheValues.redisPort}`);
    });

    client.on("error", (err) => {
        logger.info(`Failed connecting to Redis!  host :: ${config.host.redisHost} & port :: ${config.cacheValues.redisPort}`);
        logger.error("Redis connection error:", err);
    });

    return client;
}

// Initial Redis connection
redis = createRedisInstance();

async function checkRedisConnection(): Promise<boolean> {
    try {
        if (config?.cacheValues?.cacheEnabled) {
            await redis.ping();
        }
        return true;
    } catch (error) {
        console.error("Redis connection error:", error);
        return false;
    }
}

async function reconnectRedis(): Promise<void> {
    try {
        logger.info("Attempting to re-establish Redis connection...");
        if (redis) {
            await redis.quit(); // Close old connection
        }
        redis = createRedisInstance();
        await redis.ping(); // Test connection
        logger.info("Redis reconnection successful.");
    } catch (error) {
        logger.error("Redis reconnection failed:", error);
    }
}

async function deleteRedisCacheKeysWithPrefix(prefix: string): Promise<void> {
    try {
        let isRedisConnected = await checkRedisConnection();
        if (!isRedisConnected) {
            await reconnectRedis();
            isRedisConnected = await checkRedisConnection();
        }

        if (!isRedisConnected) {
            logger.error("Redis is still not connected. Skipping cache deletion.");
            return;
        }

        let cursor = '0';
        let keysToDelete: string[] = [];

        do {
            const result = await redis.scan(cursor, 'MATCH', `${prefix}*`, 'COUNT', '100');
            cursor = result[0];
            const keys = result[1];

            if (keys.length > 0) {
                keysToDelete = keysToDelete.concat(keys);
                logger.info("Cache keys found to be deleted: " + keys);
            }
        } while (cursor !== '0');

        if (keysToDelete.length > 0) {
            await redis.del(...keysToDelete);
            logger.info(`Deleted keys with prefix "${prefix}":`, keysToDelete);
        } else {
            logger.info(`No keys found with prefix "${prefix}"`);
        }
    } catch (error) {
        logger.warn("Error deleting keys from Redis:", error);
        return;
    }
}


export {
    redis,
    checkRedisConnection,
    reconnectRedis,
    deleteRedisCacheKeysWithPrefix,
};
