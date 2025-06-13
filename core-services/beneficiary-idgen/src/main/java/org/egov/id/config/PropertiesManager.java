package org.egov.id.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 
 * @author Yosadhara
 *
 */
@Configuration
@ToString
@NoArgsConstructor
public class PropertiesManager {

	@Autowired
	Environment environment;

	public String getInvalidInput() {
		return environment.getProperty("invalid.input");
	}

    public String getTimeZone(){
		return environment.getProperty("id.timezone");
	}

	public String getSaveIdPoolTopic() {return environment.getProperty("kafka.topics.save.in.id.pool");}

	public String getUpdateIdPoolStatusTopic() {return environment.getProperty("kafka.topics.update.id.pool.status");}

	public String getSaveIdDispatchLogTopic() {return environment.getProperty("kafka.topics.save.in.dispatch.log");}

	public int getDispatchLimitPerUser() {
		return Integer.parseInt(environment.getProperty("limit.per.user", "100"));
	}

	public int getDbFetchLimit() {
		return Integer.parseInt(environment.getProperty("idpool.fetch.limit.from.db", "100"));
	}

	public String getBulkIdUpdateTopic () {return  environment.getProperty("kafka.topics.consumer.bulk.update.topic");}

	public Boolean getIdValidationEnabled() {
		return Boolean.parseBoolean(environment.getProperty("id.validation.enabled", "false"));
	}
}
