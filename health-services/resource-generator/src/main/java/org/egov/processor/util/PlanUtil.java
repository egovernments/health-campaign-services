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

	/**
	 * Creates a plan configuration request, builds a plan request from it, and pushes it to the messaging system for further processing.
	 * 
	 * @param planConfigurationRequest The plan configuration request.
	 * @param feature The feature JSON node.
	 * @param resultMap The result map.
	 * @param mappedValues The mapped values.
	 */
	public void create(PlanConfigurationRequest planConfigurationRequest, JsonNode feature,
			Map<String, BigDecimal> resultMap, Map<String, String> mappedValues) {
		PlanRequest planRequest = buildPlanRequest(planConfigurationRequest, feature, resultMap, mappedValues);
		try {			
			producer.push(config.getResourceMicroplanCreateTopic(), planRequest);
		} catch (Exception e) {
			log.error(ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE_FOR_LOCALITY + planRequest.getPlan().getLocality(), e); 
		}
	}

	/**
	 * Builds a PlanRequest object using the provided plan configuration request, feature JSON node,
	 * result map, mapped values, and assumption value map.
	 * 
	 * @param planConfigurationRequest The plan configuration request.
	 * @param feature The feature JSON node.
	 * @param resultMap The result map.
	 * @param mappedValues The mapped values.
	 * @return The constructed PlanRequest object.
	 */
	private PlanRequest buildPlanRequest(PlanConfigurationRequest planConfigurationRequest, JsonNode feature,
			Map<String, BigDecimal> resultMap, Map<String, String> mappedValues) {

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
	
	/**
	 * Retrieves the boundary code value from the feature JSON node using the mapped value for the given input.
	 * 
	 * @param input The input value.
	 * @param feature The feature JSON node.
	 * @param mappedValues The mapped values.
	 * @return The boundary code value.
	 * @throws CustomException if the input value is not found in the feature JSON node.
	 */
	private String getBoundaryCodeValue(String input, JsonNode feature, Map<String, String> mappedValues) {
		if (feature.get(PROPERTIES).get(mappedValues.get(input)) != null) {
			String value = String.valueOf(feature.get(PROPERTIES).get(mappedValues.get(input)));
			return ((value!=null && value.length()>2)?value.substring(1, value.length()-1):value);
		}
		else {
			throw new CustomException("INPUT_VALUE_NOT_FOUND", "Input value not found: " + input);
		}
	}
	
	/**
	 * Updates the plan configuration request by pushing it to the messaging system for further processing.
	 * 
	 * @param planConfigurationRequest The plan configuration request to be updated.
	 */
	public void update(PlanConfigurationRequest planConfigurationRequest) {
		
		try {			
			producer.push(config.getResourceUpdatePlanConfigConsumerTopic(), planConfigurationRequest);
			log.info("Plan Config updated because of Invalid data.");
		} catch (Exception e) {
			log.error(ServiceConstants.ERROR_WHILE_UPDATING_PLAN_CONFIG); 
		}
	}
}
