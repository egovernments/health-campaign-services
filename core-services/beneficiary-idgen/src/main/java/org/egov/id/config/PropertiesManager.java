package org.egov.id.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 
 * @author Yosadhara
 *
 */
@Configuration
public class PropertiesManager {

	private final Environment environment;

	public PropertiesManager(Environment environment) {
		this.environment = environment;
	}

	public String getInvalidInput() {
		return environment.getProperty("invalid.input");
	}

    public String getTimeZone(){
		return environment.getProperty("id.timezone");
	}

	public String getSaveIdPoolTopic() {return environment.getProperty("kafka.topics.save.in.id.pool");}

	public String getUpdateIdPoolStatusTopic() {return environment.getProperty("kafka.topics.update.id.pool.status");}

	public String getSaveIdDispatchLogTopic() {return environment.getProperty("kafka.topics.save.in.dispatch.log");}

	/**
	 * Checks whether the dispatch limit per user per day feature is enabled in the application properties.
	 * This setting is determined by the property "limit.id.user.device.per.day.enabled".
	 * If the property is not specified, it defaults to true.
	 *
	 * @return true if the dispatch limit per user per day is enabled, false otherwise
	 */
	public boolean isDispatchLimitUserDevicePerDayEnabled() {
		return Boolean.parseBoolean(environment.getProperty("limit.id.user.device.per.day.enabled", "true"));
	}

	/**
	 * Retrieves the dispatch limit per user as specified in the application properties.
	 * This limit is fetched using the key "limit.id.user.device.total". If the property is not set,
	 * a default value of 1000 is returned.
	 *
	 * @return the maximum number of dispatches allowed per user
	 */
	public int getDispatchLimitUserDeviceTotal() {
		return Integer.parseInt(environment.getProperty("limit.id.user.device.total", "10000"));
	}

	/**
	 * Retrieves the daily dispatch limit per user as defined in the application properties.
	 * The value is fetched using the key "limit.id.user.device.per.day". If the property is not set,
	 * a default value of 100 is returned.
	 *
	 * @return the maximum number of dispatches allowed per user per day
	 */
	public int getDispatchLimitUserDevicePerDay() {
		return Integer.parseInt(environment.getProperty("limit.id.user.device.per.day", "100"));
	}

	/**
	 * Retrieves the database fetch limit for ID pool operations.
	 * This value is fetched from the application properties file using the key "idpool.fetch.limit.from.db".
	 * If the property is not set, a default value of 1000 is returned.
	 *
	 * @return the maximum number of IDs to fetch from the database
	 */
	public int getDbFetchLimit() {
		return Integer.parseInt(environment.getProperty("idpool.fetch.limit.from.db", "1000"));
	}

	/**
	 * Retrieves the maximum number of retry attempts for acquiring a Redisson lock.
	 * This value is fetched from the application properties file using the key "idpool.redisson.lock.acquire.retry".
	 * If the property is not set, a default value of 3 is returned.
	 *
	 * @return the maximum number of retry attempts for acquiring a Redisson lock
	 */
	public int getRedissonLockAcquireRetryCount() {
		return Integer.parseInt(environment.getProperty("multi.lock.acquire.retries", "5"));
	}

	/**
	 * Fetches the wait time for acquiring a Redisson lock, as defined in the application properties.
	 * If the property is not set, a default value of 30 seconds is returned.
	 *
	 * @return the wait time in seconds for acquiring a Redisson lock
	 */
	public int getRedissonLockWaitTime() {
		return Integer.parseInt(environment.getProperty("multi.lock.wait.time", "30"));
	}

	/**
	 * Retrieves the lease time for a Redisson lock as configured in the application properties.
	 * If the property is not set, a default value of -1 seconds is returned.
	 *
	 * @return the lease time in seconds for the Redisson lock
	 */
	public int getRedissonLockLeaseTime() {
		return Integer.parseInt(environment.getProperty("multi.lock.lease.time", "-1"));
	}

	/**
	 * Retrieves the cache time for processed IDs, as defined in the application properties.
	 * This value is fetched using the key "idpool.processing.id.cache.time". If the property
	 * is not set, a default value of 120 seconds is returned.
	 *
	 * @return the cache time for processed IDs in seconds
	 */
	public int getProcessedIDCacheTime() {
		return Integer.parseInt(environment.getProperty("idpool.processing.id.cache.time", "120"));
	}

	public String getBulkIdUpdateTopic () {return  environment.getProperty("kafka.topics.consumer.bulk.update.topic");}

	public Boolean getIdValidationEnabled() {
		return Boolean.parseBoolean(environment.getProperty("id.validation.enabled", "false"));
	}
	public String getIdPoolAsyncCreateTopic() {
		return environment.getProperty("kafka.topics.consumer.bulk.create.topic");
	}
}
