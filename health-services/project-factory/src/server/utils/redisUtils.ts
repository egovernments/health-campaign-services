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

        // Give the new connection a moment to come up before the health ping.
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


redis = createRedisInstance();

export {
    redis,
    checkRedisConnection,
    reconnectRedis,
};
