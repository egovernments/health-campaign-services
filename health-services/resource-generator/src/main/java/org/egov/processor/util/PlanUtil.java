package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.Workflow;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.kafka.Producer;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.PlanResponse;
import org.egov.processor.web.PlanSearchRequest;
import org.egov.processor.web.models.*;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.egov.processor.config.ServiceConstants.*;

@Component
@Slf4j
public class PlanUtil {
	private ServiceRequestRepository serviceRequestRepository;

	private Configuration config;
	
	private Producer producer;

	private ObjectMapper mapper;

	private ParsingUtil parsingUtil;

	public PlanUtil(ServiceRequestRepository serviceRequestRepository, Configuration config, Producer producer, ObjectMapper mapper, ParsingUtil parsingUtil) {
		this.serviceRequestRepository = serviceRequestRepository;
		this.config = config;
		this.producer = producer;
        this.mapper = mapper;
        this.parsingUtil = parsingUtil;
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
			Map<String, BigDecimal> resultMap, Map<String, String> mappedValues, Optional<Map<String, Object>> boundaryCodeToCensusAdditionalDetails) {
		PlanRequest planRequest = buildPlanRequest(planConfigurationRequest, feature, resultMap, mappedValues, boundaryCodeToCensusAdditionalDetails.orElse(Collections.emptyMap()));
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
			Map<String, BigDecimal> resultMap, Map<String, String> mappedValues, Map<String, Object> boundaryCodeToCensusAdditionalDetails) {

		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
		String boundaryCodeValue = getBoundaryCodeValue(ServiceConstants.BOUNDARY_CODE, feature, mappedValues);

		return PlanRequest.builder()
				.requestInfo(planConfigurationRequest.getRequestInfo())
				.plan(Plan.builder()
						.tenantId(planConfig.getTenantId())
						.planConfigurationId(planConfig.getId())
						.campaignId(planConfig.getCampaignId())
						.locality(boundaryCodeValue)
						.resources(resultMap.entrySet().stream().map(result -> {
							Resource res = new Resource();
							res.setResourceType(result.getKey());
							res.setEstimatedNumber(result.getValue());
							return res;
						}).collect(Collectors.toList()))
						.activities(new ArrayList())
						.targets(new ArrayList())
						.workflow(Workflow.builder().action(WORKFLOW_ACTION_INITIATE).build())
						.isRequestFromResourceEstimationConsumer(true)
						.additionalDetails(enrichAdditionalDetials(boundaryCodeToCensusAdditionalDetails, boundaryCodeValue))
						.build())
				.build();
	}

	private Object enrichAdditionalDetials(Map<String, Object> boundaryCodeToCensusAdditionalDetails, String boundaryCodeValue) {
		if(!CollectionUtils.isEmpty(boundaryCodeToCensusAdditionalDetails)) {

			Object censusAdditionalDetails = boundaryCodeToCensusAdditionalDetails.get(boundaryCodeValue);

			// Extract required details from census additional details object.
			String facilityId = (String) parsingUtil.extractFieldsFromJsonObject(censusAdditionalDetails, FACILITY_ID);
			Object accessibilityDetails = (Object) parsingUtil.extractFieldsFromJsonObject(censusAdditionalDetails, ACCESSIBILITY_DETAILS);
			Object securityDetials = (Object) parsingUtil.extractFieldsFromJsonObject(censusAdditionalDetails, SECURITY_DETAILS);

			// Creating a map of fields to be added in plan additional details with their key.
			Map<String, Object> fieldsToBeUpdated = new HashMap<>();
			if(facilityId != null && !facilityId.isEmpty())
				fieldsToBeUpdated.put(FACILITY_ID, facilityId);

			if(accessibilityDetails != null)
				fieldsToBeUpdated.put(ACCESSIBILITY_DETAILS, accessibilityDetails);

			if(securityDetials != null)
				fieldsToBeUpdated.put(SECURITY_DETAILS, securityDetials);

			if(!CollectionUtils.isEmpty(fieldsToBeUpdated))
				return parsingUtil.updateFieldInAdditionalDetails(new Object(), fieldsToBeUpdated);
		}
		return null;
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
			log.info("Plan Config updated after processing.");
		} catch (Exception e) {
			log.error(ServiceConstants.ERROR_WHILE_UPDATING_PLAN_CONFIG); 
		}
	}


	public PlanResponse search(PlanSearchRequest planSearchRequest) {

		PlanResponse planResponse = null;
		try {
			Object response = serviceRequestRepository.fetchResult(getPlanSearchUri(), planSearchRequest);
			planResponse = mapper.convertValue(response, PlanResponse.class);
		} catch (Exception e) {
			log.error(ServiceConstants.ERROR_WHILE_SEARCHING_PLAN);
		}

		if (CollectionUtils.isEmpty(planResponse.getPlan())) {
			throw new CustomException(NO_PLAN_FOUND_FOR_GIVEN_DETAILS_CODE, NO_PLAN_FOUND_FOR_GIVEN_DETAILS_MESSAGE);
		}

		return planResponse;
	}

	private StringBuilder getPlanSearchUri() {
		return new StringBuilder().append(config.getPlanConfigHost()).append(config.getPlanSearchEndPoint());
	}

	public void setFileStoreIdForPopulationTemplate(PlanConfigurationRequest planConfigurationRequest, String fileStoreId) {
		planConfigurationRequest.getPlanConfiguration().getFiles().stream()
				.filter(file -> FILE_TEMPLATE_IDENTIFIER_POPULATION.equals(file.getTemplateIdentifier()))
				.findFirst()
				.ifPresent(file -> file.setFilestoreId(fileStoreId));

		planConfigurationRequest.getPlanConfiguration().setWorkflow(null);
	}


}
