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
	 * Determines if the retrieval of dispatched IDs is restricted to those dispatched only today.
	 * This behavior is controlled by the application property "id.dispatch.retrieval.restrict-to-today.enabled".
	 * If the property is not defined, it defaults to true.
	 *
	 * @return true if the retrieval is restricted to IDs dispatched only today, false otherwise
	 */
	public boolean isIdDispatchRetrievalRestrictToTodayEnabled() {
		return Boolean.parseBoolean(environment.getProperty("id.dispatch.retrieval.restrict-to-today.enabled", "true"));
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
	 * Retrieves the number of days after which the dispatch usage per user per day data will expire.
	 * This value is configured in the application properties using the key
	 * "limit.id.user.device.per.day.expire.days". If the property is not set, it defaults to 30.
	 *
	 * @return the number of days after which the dispatch usage per user per day data expires
	 */
	public int getDispatchUsageUserDevicePerDayExpireDays() {
		return Integer.parseInt(environment.getProperty("limit.id.user.device.per.day.expire.days", "30"));
	}

	/**
	 * Retrieves the total number of days after which the dispatch usage for a user device expires.
	 * This value is configured in the application's properties using the key
	 * "limit.id.user.device.total.expire.days". If the property is not set, it defaults to 30.
	 *
	 * @return the number of days after which the dispatch usage for a user device expires
	 */
	public int getDispatchUsageUserDeviceTotalExpireDays() {
		return Integer.parseInt(environment.getProperty("limit.id.user.device.total.expire.days", "30"));
	}

	public String getBulkIdUpdateTopic () {return  environment.getProperty("kafka.topics.consumer.bulk.update.topic");}

	public Boolean getIdValidationEnabled() {
		return Boolean.parseBoolean(environment.getProperty("id.validation.enabled", "false"));
	}
	public String getIdPoolBulkCreateTopic() {
		return environment.getProperty("kafka.topics.consumer.bulk.create.topic");
	}
}
