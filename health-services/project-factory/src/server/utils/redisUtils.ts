import Redis from "ioredis";
import config from "../config";
import { logger } from "./logger";

let redis: Redis;

function createRedisInstance(): Redis {
    const client = new Redis({
        host: config.host.redisHost,
        port: parseInt(config.cacheValues.redisPort),
        retryStrategy() {
            return null;
        },
        maxRetriesPerRequest: 1,
        reconnectOnError() {
            return false;
        },
    });

    client.on("connect", () => {
        logger.info(`✅ Connected to Redis — Host: ${config.host.redisHost}, Port: ${config.cacheValues.redisPort}`);
    });

    client.on("error", (err) => {
        logger.error(`❌ Redis error — Host: ${config.host.redisHost}, Port: ${config.cacheValues.redisPort}`);
        logger.error("Details:", err);
    });

    return client;
}

async function reconnectRedis(): Promise<void> {
    try {
        logger.info("🔄 Re-establishing Redis connection...");
        if (redis) {
            try {
                await redis.quit();
            } catch (err) {
                logger.warn("⚠️ Failed to quit old Redis connection:", err);
            }
        }
        redis = createRedisInstance();

        // Wait a bit before pinging to give Redis time to come up
        await new Promise((resolve) => setTimeout(resolve, 1000));
        await redis.ping();

        logger.info("✅ Redis reconnection successful.");
    } catch (error) {
        logger.error("❌ Redis reconnection failed:", error);
    }
}


async function checkRedisConnection(): Promise<boolean> {
    try {
        if (config?.cacheValues?.cacheEnabled) {
            await redis.ping();
        }
        return true;
    } catch (error) {
        logger.error("❌ Redis ping failed:", error);
        return false;
    }
}

async function deleteRedisCacheKeysWithPrefix(prefix: string): Promise<void> {
    try {
        let isConnected = await checkRedisConnection();
        if (!isConnected) {
            await reconnectRedis();
            isConnected = await checkRedisConnection();
        }

        if (!isConnected) {
            logger.error("❌ Redis still not connected. Skipping cache deletion.");
            return;
        }

        let cursor = '0';
        const keysToDelete: string[] = [];

        do {
            const [nextCursor, keys] = await redis.scan(cursor, 'MATCH', `${prefix}*`, 'COUNT', '100');
            cursor = nextCursor;
            if (keys.length > 0) {
                keysToDelete.push(...keys);
                logger.info(`🧹 Found cache keys to delete: ${keys}`);
            }
        } while (cursor !== '0');

        if (keysToDelete.length > 0) {
            await redis.del(...keysToDelete);
            logger.info(`✅ Deleted keys with prefix "${prefix}":`, keysToDelete);
        } else {
            logger.info(`ℹ️ No keys found with prefix "${prefix}"`);
        }
    } catch (error) {
        logger.warn("⚠️ Error deleting Redis keys:", error);
    }
}

// Start
redis = createRedisInstance();

export {
    redis,
    checkRedisConnection,
    reconnectRedis,
    deleteRedisCacheKeysWithPrefix,
};
