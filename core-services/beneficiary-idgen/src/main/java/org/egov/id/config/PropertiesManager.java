package org.egov.id.config;

import lombok.extern.slf4j.Slf4j;
import org.egov.id.model.DispatchLimitConfig;
import org.egov.id.model.IdPoolConfig;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 *
 * @author Yosadhara
 *
 */
@Configuration
@Slf4j
public class PropertiesManager {

	private final Environment environment;

	public PropertiesManager(Environment environment) {
		this.environment = environment;
	}

	public String getInvalidInput() {
		return environment.getProperty("invalid.input");
	}

    public String getUserTimeZone(){
		return environment.getProperty("id.timezone");
	}

	public String getAppTimeZone(){
		return environment.getProperty("app.timezone");
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

	public String getMdmsDispatchLimitModule() {
		return environment.getProperty("mdms.dispatch.limit.module", "beneficiary-idgen");
	}

	public String getMdmsDispatchLimitMaster() {
		return environment.getProperty("mdms.dispatch.limit.master", "IdDispatchConfig");
	}

	public String getMdmsIdPoolModule() {
		return environment.getProperty("mdms.id.pool.module", "beneficiary-idgen");
	}

	public String getMdmsIdPoolMaster() {
		return environment.getProperty("mdms.id.pool.master", "IdPoolConfig");
	}

	public int getDispatchLimitCacheTtlMinutes() {
		return Integer.parseInt(environment.getProperty("dispatch.limit.cache.ttl.minutes", "30"));
	}

	public DispatchLimitConfig getDefaultDispatchLimitConfig() {
		return DispatchLimitConfig.builder()
				.perDayEnabled(isDispatchLimitUserDevicePerDayEnabled())
				.totalLimit(getDispatchLimitUserDeviceTotal())
				.perDayLimit(getDispatchLimitUserDevicePerDay())
				.perDayExpireDays(getDispatchUsageUserDevicePerDayExpireDays())
				.totalExpireDays(getDispatchUsageUserDeviceTotalExpireDays())
				.restrictToTodayEnabled(isIdDispatchRetrievalRestrictToTodayEnabled())
				.build();
	}

	/**
	 * Surfaces a missing service-level 'id.pool.seq.code' at startup as a loud, operator-visible warning.
	 *
	 * This is intentionally a warning and not a hard boot failure: a deployment may legitimately supply
	 * seqCode per-tenant via MDMS IdPoolConfig and omit the service-level default, in which case a hard
	 * fail would be wrong. But when neither is present, ID pool generation aborts per request (see
	 * {@code IdGenerationService.fetchIdFormat}), and that failure is otherwise only visible in a per-message
	 * error log. Emitting it at startup makes the misconfiguration visible where operators actually look.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void warnIfSeqCodeMissing() {
		String seqCode = environment.getProperty("id.pool.seq.code");
		if (seqCode == null || seqCode.isBlank()) {
			log.warn("Configuration 'id.pool.seq.code' is not set. ID pool generation will fail for any tenant "
					+ "that does not provide its own 'seqCode' via MDMS IdPoolConfig. Set 'id.pool.seq.code' at the "
					+ "service level unless every tenant defines seqCode in MDMS.");
		}
	}

	public IdPoolConfig getDefaultIdPoolConfig() {
		// seqCode has no code-level fallback on purpose: a genuinely unset 'id.pool.seq.code' must
		// surface as a loud configuration error (see IdGenerationService.fetchIdFormat) rather than
		// silently generating IDs from an unintended default sequence.
		return IdPoolConfig.builder()
				.seqCode(environment.getProperty("id.pool.seq.code"))
				.paddingLength(Integer.parseInt(environment.getProperty("id.pool.padding.length", "12")))
				.build();
	}
}
