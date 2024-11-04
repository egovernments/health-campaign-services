package org.egov.processor.config;

import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Component
@Data
@Import({ TracerConfiguration.class })
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Configuration {

	// MDMS
	@Value("${egov.mdms.host}")
	private String mdmsHost;

	@Value("${egov.mdms.search.endpoint}")
	private String mdmsEndPoint;

	@Value("${egov.mdms.search.v2.endpoint}")
	private String mdmsV2EndPoint;

	@Value("${egov.plan.config.host}")
	private String planConfigHost;

	@Value("${egov.plan.config.endpoint}")
	private String planConfigEndPoint;

	// Filestore

	@Value("${egov.filestore.service.host}")
	private String fileStoreHost;

	@Value("${egov.filestore.endpoint}")
	private String fileStoreEndpoint;

	@Value("${egov.filestore.upload.endpoint}")
	private String fileStoreUploadEndpoint;

	@Value("${egov.plan.create.endpoint}")
	private String planCreateEndPoint;

	@Value("${egov.project.factory.search.endpoint}")
	private String campaignIntegrationSearchEndPoint;

	@Value("${egov.project.factory.update.endpoint}")
	private String campaignIntegrationUpdateEndPoint;

	@Value("${egov.project.factory.host}")
	private String projectFactoryHostEndPoint;

	@Value("${integrate.with.admin.console}")
	private boolean isIntegrateWithAdminConsole;

	@Value("${egov.boundary.service.host}")
	private String egovBoundaryServiceHost;

	@Value("${egov.boundary.relationship.search.endpoint}")
	private String egovBoundaryRelationshipSearchEndpoint;
	
	@Value("${egov.locale.service.host}")
	private String egovLocaleServiceHost;
		
	@Value("${egov.locale.search.endpoint}")
	private String egovLocaleSearchEndpoint;

	//trigger statuses
	@Value("${plan.config.trigger.plan.estimates.status}")
	private String planConfigTriggerPlanEstimatesStatus;

	@Value("${plan.config.trigger.census.records.status}")
	private String planConfigTriggerCensusRecordsStatus;

	//Kafka topics for creating or updating records in dependent microservices
	@Value("${resource.microplan.create.topic}")
	private String resourceMicroplanCreateTopic;

	@Value("${resource.update.plan.config.consumer.topic}")
	private String resourceUpdatePlanConfigConsumerTopic;

	@Value("${resource.census.create.topic}")
	private String resourceCensusCreateTopic;

	//Default
	@Value("${resource.default.offset}")
	private Integer defaultOffset;

	@Value("${resource.default.limit}")
	private Integer defaultLimit;

}
