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

@SuppressWarnings("unused")
public class PropertiesManager {

	@Autowired
	Environment environment;

	private String invalidInput;

	private String dbUrl;

	private String dbUserName;

	private String dbPassword;

	private String idGenerationTable;

	private String idSequenceOverflow;

	private String idSequenceNotFound;

	private String invalidIdFormat;

	private String success;

	private String failed;

	private String serverContextpath;

	private String timeZone;

	private String saveIdPoolTopic;

	private String  updateIdPoolStatusTopic;

	private String saveIdDispatchLogTopic;

	public String getInvalidInput() {
		return environment.getProperty("invalid.input");
	}

	public String getDbUrl() {
		return environment.getProperty("spring.datasource.url");
	}

	public String getDbUserName() {
		return environment.getProperty("spring.datasource.username");
	}

	public String getDbPassword() {
		return environment.getProperty("spring.datasource.password");
	}

	public String getIdGenerationTable() {
		return environment.getProperty("id.generation.table");
	}

	public String getIdSequenceOverflow() {
		return environment.getProperty("id.sequence.overflow");
	}

	public String getIdSequenceNotFound() {
		return environment.getProperty("id.sequence.notfound");
	}

	public String getInvalidIdFormat() {
		return environment.getProperty("id.invalid.format");
	}

	public String getSuccess() {
		return environment.getProperty("success");
	}

	public String getFailed() {
		return environment.getProperty("failed");
	}

	public String getServerContextpath() {
		return environment.getProperty("server.context-path");
	}

    public String getCityCodeNotFound() {
        return environment.getProperty("city.code.notfound");
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

}
