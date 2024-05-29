package org.egov.processor.util;

import static org.egov.processor.config.ServiceConstants.ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE_FOR_LOCALITY;
import static org.egov.processor.config.ServiceConstants.PROPERTIES;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.kafka.Producer;
import org.egov.processor.web.models.Activity;
import org.egov.processor.web.models.Plan;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.PlanConfigurationResponse;
import org.egov.processor.web.models.PlanRequest;
import org.egov.processor.web.models.Resource;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PlanUtil {
	private ServiceRequestRepository serviceRequestRepository;

	private Configuration config;
	
	private Producer producer;

	public PlanUtil(ServiceRequestRepository serviceRequestRepository, Configuration config, Producer producer) {
		this.serviceRequestRepository = serviceRequestRepository;
		this.config = config;
		this.producer = producer;
	}

	public void create(PlanConfigurationRequest planConfigurationRequest, JsonNode feature,
			Map<String, BigDecimal> resultMap, Map<String, String> mappedValues,
			Map<String, BigDecimal> assumptionValueMap) {
		PlanRequest planRequest = buildPlanRequest(planConfigurationRequest, feature, resultMap, mappedValues,
				assumptionValueMap);
		try {			
			producer.push(config.getResourceMicroplanCreateTopic(), planRequest);
		} catch (Exception e) {
			log.error(ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE_FOR_LOCALITY + planRequest.getPlan().getLocality(), e); 
		}
	}

	private PlanRequest buildPlanRequest(PlanConfigurationRequest planConfigurationRequest, JsonNode feature,
			Map<String, BigDecimal> resultMap, Map<String, String> mappedValues,
			Map<String, BigDecimal> assumptionValueMap) {

		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
		return PlanRequest.builder()
				.requestInfo(planConfigurationRequest.getRequestInfo())
				.plan(Plan.builder()
						.tenantId(planConfig.getTenantId())
						.executionPlanId(planConfig.getExecutionPlanId())
						.locality(getBoundaryCodeValue(ServiceConstants.BOUNDARY_CODE,
								feature, mappedValues))
						.resources(resultMap.entrySet().stream().map(result -> {
							Resource res = new Resource();
							res.setResourceType(result.getKey());
							res.setEstimatedNumber(result.getValue());
							return res;
						}).collect(Collectors.toList()))
						.activities(new ArrayList())
						.targets(new ArrayList())
						.build())
				.build();

	}
	
	private String getBoundaryCodeValue(String input, JsonNode feature, Map<String, String> mappedValues) {
		if (feature.get(PROPERTIES).get(mappedValues.get(input)) != null) {
			String value = String.valueOf(feature.get(PROPERTIES).get(mappedValues.get(input)));
			return ((value!=null && value.length()>2)?value.substring(1, value.length()-1):value);
		}
		else {
			throw new CustomException("INPUT_VALUE_NOT_FOUND", "Input value not found: " + input);
		}
	}
}
