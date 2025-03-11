package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.models.Workflow;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.kafka.Producer;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.PlanResponse;
import org.egov.processor.web.PlanSearchRequest;
import org.egov.processor.web.models.*;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
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

	private FilestoreUtil filestoreUtil;

	public PlanUtil(ServiceRequestRepository serviceRequestRepository, Configuration config, Producer producer, ObjectMapper mapper, ParsingUtil parsingUtil, FilestoreUtil filestoreUtil) {
		this.serviceRequestRepository = serviceRequestRepository;
		this.config = config;
		this.producer = producer;
        this.mapper = mapper;
        this.parsingUtil = parsingUtil;
        this.filestoreUtil = filestoreUtil;
    }

	/**
	 * Creates a plan configuration request, builds a plan request from it, and pushes it to the messaging system for further processing.
	 * 
	 * @param planConfigurationRequest The plan configuration request.
	 * @param feature The feature JSON node.
	 * @param resultMap The result map.
	 * @param mappedValues The mapped values.
	 * @param boundaryCodeToCensusAdditionalDetails A Map of boundary code to censusAdditionalDetails for that boundary code.
	 */
	public void create(PlanConfigurationRequest planConfigurationRequest, JsonNode feature,
			Map<String, BigDecimal> resultMap, Map<String, String> mappedValues, Map<String, Object> boundaryCodeToCensusAdditionalDetails) {
		PlanRequest planRequest = buildPlanRequest(planConfigurationRequest, feature, resultMap, mappedValues, boundaryCodeToCensusAdditionalDetails);
		try {
			producer.push(config.getResourceMicroplanCreateTopic(), planRequest);
		} catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE_FOR_LOCALITY + LOG_PLACEHOLDER, planRequest.getPlan().getLocality(), e);
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
	 * @param boundaryCodeToCensusAdditionalDetails A Map of boundary code to censusAdditionalDetails for that boundary code.
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
						.additionalDetails(enrichAdditionalDetails(boundaryCodeToCensusAdditionalDetails, boundaryCodeValue))
						.build())
				.build();
	}

	/**
	 * Creates an additional details object. Extracts the required fields from census additional details for the given boundary.
	 * The extracted fields are then used to update the additional details object.
	 *
	 * @param boundaryCodeToCensusAdditionalDetails A map containing boundary codes mapped to their respective census additional details.
	 * @param boundaryCodeValue                     The boundary code for which additional details need to be enriched.
	 * @return An updated object containing extracted and enriched additional details, or null if no details were found or added.
	 */
	private Object enrichAdditionalDetails(Map<String, Object> boundaryCodeToCensusAdditionalDetails, String boundaryCodeValue) {
		if(!CollectionUtils.isEmpty(boundaryCodeToCensusAdditionalDetails)) {

			Object censusAdditionalDetails = boundaryCodeToCensusAdditionalDetails.get(boundaryCodeValue);

			// Return null value if censusAdditionalDetails is null
			if(ObjectUtils.isEmpty(censusAdditionalDetails))
				return null;

			// Extract required details from census additional details object.
			String facilityId = (String) parsingUtil.extractFieldsFromJsonObject(censusAdditionalDetails, FACILITY_ID);
			Object accessibilityDetails = (Object) parsingUtil.extractFieldsFromJsonObject(censusAdditionalDetails, ACCESSIBILITY_DETAILS);
			Object securityDetails = (Object) parsingUtil.extractFieldsFromJsonObject(censusAdditionalDetails, SECURITY_DETAILS);

			// Creating a map of fields to be added in plan additional details with their key.
			Map<String, Object> fieldsToBeUpdated = new HashMap<>();
			if(!ObjectUtils.isEmpty(facilityId))
				fieldsToBeUpdated.put(FACILITY_ID, facilityId);

			// Add fields from accessibilityDetails to fieldsToBeUpdated map if it's present in censusAdditionalDetails.
			if(!ObjectUtils.isEmpty(accessibilityDetails)) {
				extractNestedFields((Map<String, Object>) accessibilityDetails, ACCESSIBILITY_DETAILS, fieldsToBeUpdated);
			}

			// Add fields from securityDetails to fieldsToBeUpdated map if it's present in censusAdditionalDetails.
			if(!ObjectUtils.isEmpty(securityDetails)) {
				extractNestedFields((Map<String, Object>) securityDetails, SECURITY_DETAILS, fieldsToBeUpdated);
			}

			// If the fieldsToBeUpdated map is not empty, pass a new empty object to serve as the additional details object.
			if(!CollectionUtils.isEmpty(fieldsToBeUpdated))
				return parsingUtil.updateFieldInAdditionalDetails(new Object(), fieldsToBeUpdated);
		}
		return null;
	}

	/**
	 * Extracts nested fields from the given additionalDetails map and adds them to fieldsToBeUpdated in a structured format.
	 * If a nested map contains CODE, its value is stored with the key formatted as "prefix|key|CODE".
	 *
	 * @param details           The map containing nested key-value pairs to be processed.
	 * @param prefix            The prefix to be used for constructing the final key in fieldsToBeUpdated.
	 * @param fieldsToBeUpdated The map where extracted values will be stored with formatted keys.
	 */
	private void extractNestedFields(Map<String, Object> details, String prefix, Map<String, Object> fieldsToBeUpdated) {
		for (Map.Entry<String, Object> entry : details.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();

			if (value instanceof Map) {
				Map<String, Object> nestedMap = (Map<String, Object>) value;
				if (nestedMap.containsKey(CODE)) {
					fieldsToBeUpdated.put(prefix + "|" + key + "|" + CODE, nestedMap.get(CODE));
				}
			}
		}
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
			throw new CustomException(INPUT_NOT_FOUND_CODE, INPUT_NOT_FOUND_MESSAGE + input);
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

	public void setFileStoreIdForEstimationsInProgress(PlanConfigurationRequest planConfigurationRequest, String fileStoreId) {
		planConfigurationRequest.getPlanConfiguration().getFiles().stream()
				.filter(file -> FILE_TEMPLATE_IDENTIFIER_ESTIMATIONS_IN_PROGRESS.equals(file.getTemplateIdentifier()))
				.findFirst()
				.ifPresent(file -> file.setFilestoreId(fileStoreId));

		planConfigurationRequest.getPlanConfiguration().setWorkflow(null);
	}

	public void addFileForFinalEstimations(PlanConfigurationRequest planConfigurationRequest, String fileStoreId,
										   File.InputFileTypeEnum inputFileType) {
		File estimationFile = File.builder()
				.filestoreId(fileStoreId)
				.inputFileType(inputFileType)
				.templateIdentifier(FILE_TEMPLATE_IDENTIFIER_ESTIMATIONS)
				.active(true)
				.build();

		planConfigurationRequest.getPlanConfiguration().getFiles().add(estimationFile);
		planConfigurationRequest.getPlanConfiguration().setWorkflow(null);
	}

	/**
	 * Duplicates the population file and add a new file for estimations in plan configuration object.
	 *
	 * @param planConfigurationRequest the plan configuration request to be updated with new estimations file.
	 */
	public void addEstimationsFile(PlanConfigurationRequest planConfigurationRequest) {
		PlanConfiguration planConfiguration = planConfigurationRequest.getPlanConfiguration();

		// Retrieves the population file from the list of files in the given plan configuration.
		File populationFile = planConfiguration.getFiles().stream()
				.filter(file -> file.getTemplateIdentifier().equalsIgnoreCase(ServiceConstants.FILE_TEMPLATE_IDENTIFIER_POPULATION))
				.findFirst()
				.orElse(null);

		//If a population file exists, process it to create an estimation file.
		if (!ObjectUtils.isEmpty(populationFile)) {
			processAndAddEstimationFile(planConfigurationRequest, populationFile);
		}
	}

	/**
	 * Processes the population file by:
	 * 1. Fetching its byte data and converting it into a new file for estimations.
	 * 2. Uploading the estimations file to the fileStore.
	 * 3. Creating an estimations file entry and adding it to the plan configuration object.
	 * 4. Updating the plan configuration object.
	 *
	 * @param planConfigurationRequest The request containing the plan configuration to update.
	 * @param populationFile           The population file to duplicate.
	 */
	private void processAndAddEstimationFile(PlanConfigurationRequest planConfigurationRequest, File populationFile) {
		PlanConfiguration planConfiguration = planConfigurationRequest.getPlanConfiguration();

		// Fetch file data from fileStore
		byte[] byteArray = filestoreUtil.getFile(planConfiguration.getTenantId(), populationFile.getFilestoreId());
		java.io.File estimationsTempFile = parsingUtil.convertByteArrayToFile(byteArray, ServiceConstants.FILE_NAME);

		try (Workbook workbook = new XSSFWorkbook(estimationsTempFile)) {
			java.io.File estimationsFile = parsingUtil.convertWorkbookToXls(workbook);

			// Upload the new file and get the fileStore ID
			String estimationsFileStoreId = filestoreUtil.uploadFile(estimationsFile, planConfiguration.getTenantId());

			// Create a new estimation file entry
			File estimationFile = File.builder()
					.filestoreId(estimationsFileStoreId)
					.inputFileType(populationFile.getInputFileType())
					.templateIdentifier(FILE_TEMPLATE_IDENTIFIER_ESTIMATIONS_IN_PROGRESS)
					.active(true)
					.build();

			// Add the estimation file to the plan configuration
			planConfigurationRequest.getPlanConfiguration().getFiles().add(estimationFile);
			planConfigurationRequest.getPlanConfiguration().setWorkflow(null);

		} catch (FileNotFoundException e) {
			log.error(EXCEL_FILE_NOT_FOUND_MESSAGE + LOG_PLACEHOLDER, e.getMessage());
			throw new CustomException(EXCEL_FILE_NOT_FOUND_CODE, EXCEL_FILE_NOT_FOUND_MESSAGE);
		} catch (InvalidFormatException e) {
			log.error(INVALID_FILE_FORMAT_MESSAGE + LOG_PLACEHOLDER, e.getMessage());
			throw new CustomException(INVALID_FILE_FORMAT_CODE, INVALID_FILE_FORMAT_MESSAGE);
		} catch (IOException e) {
			log.error(ERROR_PROCESSING_EXCEL_FILE + LOG_PLACEHOLDER, e.getMessage());
			throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					ERROR_PROCESSING_EXCEL_FILE);
		}
	}
}
