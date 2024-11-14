package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.models.File;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.campaignManager.*;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;

import static org.egov.processor.config.ServiceConstants.*;

@Component
@Slf4j
public class CampaignIntegrationUtil {

	private ServiceRequestRepository serviceRequestRepository;
	private Configuration config;
	private ObjectMapper mapper;
	private ParsingUtil parsingUtil;

	public CampaignIntegrationUtil(ServiceRequestRepository serviceRequestRepository, Configuration config,
			ObjectMapper mapper, FilestoreUtil filestoreUtil, ParsingUtil parsingUtil) {

		this.serviceRequestRepository = serviceRequestRepository;
		this.config = config;
		this.mapper = mapper;
		this.parsingUtil= parsingUtil;
	}

	/**
	 * Updates resources in the Project Factory by calling an external API with the given plan configuration
	 * request and file store ID. Logs the operation status.
	 *
	 * @param planConfigurationRequest The plan configuration request details.
	 * @param fileStoreId              The file store ID to update.
	 * @throws CustomException if the API call fails.
	 */
	public void updateResourcesInProjectFactory(PlanConfigurationRequest planConfigurationRequest, String fileStoreId) {
		try {
			serviceRequestRepository.fetchResult(
					new StringBuilder(config.getProjectFactoryHostEndPoint() + config.getCampaignIntegrationFetchFromMicroplanEndPoint()),
					buildMicroplanDetailsForUpdate(planConfigurationRequest, fileStoreId));
			log.info("Updated resources file into project factory - " + fileStoreId);
		} catch (Exception e) {
			log.error(ERROR_WHILE_CALLING_MICROPLAN_API + planConfigurationRequest.getPlanConfiguration().getId(), e);
			throw new CustomException(ERROR_WHILE_CALLING_MICROPLAN_API, e.toString());
		}

	}

	/**
	 * Builds a campaign request object for updating campaign details based on the provided plan configuration request and campaign response.
	 *
	 * @param planConfigurationRequest The plan configuration request containing necessary information for updating the campaign.
	 * @param fileStoreId The filestoreId with calculated resources
	 * @return The microplan details request object built for updating resource filestore id.
	 */
	private MicroplanDetailsRequest buildMicroplanDetailsForUpdate(PlanConfigurationRequest planConfigurationRequest, String fileStoreId) {
		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();

		MicroplanDetails microplanDetails = MicroplanDetails.builder()
				.tenantId(planConfig.getTenantId())
				.planConfigurationId(planConfig.getId())
				.campaignId(planConfig.getCampaignId())
				.resourceFilestoreId(fileStoreId).build();

		return MicroplanDetailsRequest.builder()
				.microplanDetails(microplanDetails)
				.requestInfo(planConfigurationRequest.getRequestInfo()).build();

	}

	/**
	 * Updates campaign details based on the provided plan configuration request and response data.
	 * This method integrates the campaign details obtained from the response into the provided plan configuration request.
	 * It also updates the campaign boundaries and resources accordingly.
	 *
	 * @param planConfigurationRequest The plan configuration request containing the execution plan details.
	 * @param response The response object containing campaign details.
	 * @param campaignBoundaryList The list of campaign boundaries.
	 * @param campaignResourcesList The list of campaign resources.
	 */
	public void updateCampaignDetails(PlanConfigurationRequest planConfigurationRequest,Object response,List<Boundary> campaignBoundaryList,List<CampaignResources> campaignResourcesList) {
		CampaignResponse campaignResponse = null;
		try {
			campaignResponse = mapper.convertValue(response, CampaignResponse.class);
			campaignResponse.getCampaign().get(0).setResources(campaignResourcesList);
			Boundary[] array = campaignBoundaryList.toArray(new Boundary[0]);
			campaignResponse.getCampaign().get(0).setBoundaries(campaignBoundaryList.toArray(new Boundary[0]));
			serviceRequestRepository.fetchResult(
					new StringBuilder(config.getProjectFactoryHostEndPoint() + config.getCampaignIntegrationUpdateEndPoint()),
					buildCampaignRequestForUpdate(planConfigurationRequest, campaignResponse));
			log.info("Campaign Integration successful.");
		} catch (Exception e) {
			log.error(ServiceConstants.ERROR_WHILE_SEARCHING_CAMPAIGN
					+ planConfigurationRequest.getPlanConfiguration().getCampaignId(), e);
			throw new CustomException("Failed to update campaign details in CampaignIntegration class within method updateCampaignDetails.", e.toString());
		}
	}

	/**
	 * Sends a data creation request to the Project Factory service using the provided
	 * plan and campaign details.
	 *
	 * @param planConfigurationRequest the plan configuration request containing campaign data
	 * @param campaignResponse         the response with additional campaign information
	 * @throws CustomException if the data creation call fails
	 */
	public void createProjectFactoryDataCall(PlanConfigurationRequest planConfigurationRequest, CampaignResponse campaignResponse) {
		try {
			serviceRequestRepository.fetchResult(
					new StringBuilder(config.getProjectFactoryHostEndPoint() + config.getCampaignIntegrationDataCreateEndPoint()),
					buildResourceDetailsObjectForFacilityCreate(planConfigurationRequest, campaignResponse));
			log.info("Campaign Data create successful.");
		} catch (Exception e) {
			log.error(ServiceConstants.ERROR_WHILE_DATA_CREATE_CALL
					+ planConfigurationRequest.getPlanConfiguration().getCampaignId(), e);
			throw new CustomException("Failed to update campaign details in CampaignIntegration class within method updateCampaignDetails.", e.toString());
		}
	}

	/**
	 * Updates the campaign resources in the given campaign response based on the files specified in the plan configuration request.
	 *
	 * @param campaignResponse The campaign response object to be updated with resources.
	 * @param planConfigurationRequest The plan configuration request containing file information.
	 * @param fileStoreId The file store ID.
	 */
	public void updateResources(CampaignResponse campaignResponse, PlanConfigurationRequest planConfigurationRequest,
			String fileStoreId) {
		List<CampaignResources> campaignResourcesList = new ArrayList<>();
		List<File> files = planConfigurationRequest.getPlanConfiguration().getFiles();
		for (File file : files) {
			CampaignResources campaignResource = new CampaignResources();
			campaignResource.setFilename(ServiceConstants.FILE_NAME);			
			campaignResource.setFilestoreId(fileStoreId);
			campaignResource.setType(ServiceConstants.FILE_TYPE);
			campaignResourcesList.add(campaignResource);
		}
		campaignResponse.getCampaign().get(0).setResources(campaignResourcesList);
	}

	/**
	 * Builds a campaign request object for updating campaign details based on the provided plan configuration request and campaign response.
	 *
	 * @param planConfigurationRequest The plan configuration request containing necessary information for updating the campaign.
	 * @param campaignResponse The campaign response containing the updated campaign details.
	 * @return The campaign request object built for updating campaign details.
	 */
	private CampaignRequest buildCampaignRequestForUpdate(PlanConfigurationRequest planConfigurationRequest,
			CampaignResponse campaignResponse) {
		return CampaignRequest.builder().requestInfo(planConfigurationRequest.getRequestInfo())
				.campaignDetails(campaignResponse.getCampaign().get(0)).build();

	}

	/**
	 * Builds a {@link ResourceDetailsRequest} object for facility creation using the provided
	 * plan configuration and campaign details.
	 *
	 * @param planConfigurationRequest the request containing plan configuration data
	 * @param campaignResponse the campaign response with additional data
	 * @return a {@link ResourceDetailsRequest} for facility creation
	 * @throws CustomException if the required facility file is not found
	 */
	private ResourceDetailsRequest buildResourceDetailsObjectForFacilityCreate(PlanConfigurationRequest planConfigurationRequest,
																			   CampaignResponse campaignResponse) {
		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();

		String facilityFilestoreId = String.valueOf(planConfig.getFiles().stream()
				.filter(file -> FILE_TEMPLATE_IDENTIFIER_FACILITY.equals(file.getTemplateIdentifier()))
				.map(File::getFilestoreId)
				.findFirst()
				.orElseThrow(() -> new CustomException(FILE_NOT_FOUND_CODE, FILE_NOT_FOUND_MESSAGE + FILE_TEMPLATE_IDENTIFIER_FACILITY)));

		ResourceDetails resourceDetails = ResourceDetails.builder()
				.type(TYPE_FACILITY)
				.hierarchyType(campaignResponse.getCampaign().get(0).getHierarchyType())
				.tenantId(planConfig.getTenantId())
				.fileStoreId(facilityFilestoreId)
				.action(ACTION_CREATE)
				.campaignId(planConfig.getCampaignId())
				.additionalDetails(createAdditionalDetailsforFacilityCreate(MICROPLAN_SOURCE_KEY, planConfig.getId()))
				.build();

		return ResourceDetailsRequest.builder()
				.requestInfo(planConfigurationRequest.getRequestInfo())
				.resourceDetails(resourceDetails)
				.build();

	}

	/**
	 * Updates campaign boundary based on the provided plan configuration, feature, assumption values, mapped values, column index map, boundary list, and result map.
	 *
	 * @param planConfig The plan configuration containing relevant details.
	 * @param feature The JSON node representing the feature.
	 * @param assumptionValueMap The map containing assumption values.
	 * @param mappedValues The map containing mapped values.
	 * @param mapOfColumnNameAndIndex The map containing column names and their indices.
	 * @param boundaryList The list of campaign boundaries to update.
	 * @param resultMap The map containing result values.
	 * @throws IOException If an I/O error occurs.
	 */
	public void updateCampaignBoundary(PlanConfiguration planConfig, JsonNode feature,
			Map<String, BigDecimal> assumptionValueMap, Map<String, String> mappedValues,
			Map<String, Integer> mapOfColumnNameAndIndex, List<Boundary> boundaryList,
			Map<String, BigDecimal> resultMap) {
		Integer indexOfType = null;
		boolean validToAdd = false;
		Integer indexValue = 0;
		Boundary boundary = new Boundary();
		List<Map.Entry<String, Integer>> sortedColumnList = parsingUtil.sortColumnByIndex(mapOfColumnNameAndIndex);
		indexValue = parsingUtil.getIndexOfBoundaryCode(indexValue, sortedColumnList, mappedValues);
		prepareBoundary(indexOfType, indexValue, sortedColumnList, feature, boundary, mappedValues);
		if (isValidToAdd(boundaryList, resultMap, validToAdd, boundary))
			boundaryList.add(boundary);
	}


	/**
	 * Prepares a campaign boundary based on the provided index values, sorted column list, feature, and mapped values.
	 *
	 * @param indexOfType The index of the boundary type.
	 * @param indexValue The index value.
	 * @param sortedColumnList The sorted list of column names and indices.
	 * @param feature The JSON node representing the feature.
	 * @param boundary The boundary object to be prepared.
	 * @param mappedValues The map containing mapped values.
	 * @return The index of the boundary type after preparation.
	 */
	private Integer prepareBoundary(Integer indexOfType, Integer indexValue,
			List<Map.Entry<String, Integer>> sortedColumnList, JsonNode feature, Boundary boundary,Map<String, String> mappedValues) {
		String codeValue = getBoundaryCodeValue(ServiceConstants.BOUNDARY_CODE, feature, mappedValues);
		boundary.setCode(codeValue);
		for (int j = 0; j < indexValue; j++) {
			Map.Entry<String, Integer> entry = sortedColumnList.get(j);
			String value = String.valueOf(feature.get(PROPERTIES).get(entry.getKey()));
			if (StringUtils.isNotBlank(value) && value.length() > 2) {
				boundary.setType(entry.getKey());
				indexOfType = entry.getValue();
			}
		}
		if (indexOfType == 0) {
			boundary.setRoot(true);
			boundary.setIncludeAllChildren(true);
		}
		return indexOfType;
	}
 
	/**
	 * Checks if the provided boundary is valid to add to the boundary list based on the result map.
	 *
	 * @param boundaryList The list of existing boundaries.
	 * @param resultMap The map containing result values.
	 * @param validToAdd The flag indicating whether the boundary is valid to add.
	 * @param boundary The boundary to be checked for validity.
	 * @return True if the boundary is valid to add, false otherwise.
	 */
	private boolean isValidToAdd(List<Boundary> boundaryList, Map<String, BigDecimal> resultMap, boolean validToAdd,
			Boundary boundary) {
		for (Entry<String, BigDecimal> entry : resultMap.entrySet()) {
			if (entry.getValue().compareTo(new BigDecimal(0)) > 0) {
				validToAdd = true;
			} else {
				validToAdd = false;
				break;
			}
		}		
		return validToAdd;
	}



	/**
	 * Retrieves the value of the boundary code from the feature JSON node based on the mapped values.
	 *
	 * @param input The input key.
	 * @param feature The JSON node representing the feature.
	 * @param mappedValues The map containing mapped values.
	 * @return The value of the boundary code.
	 * @throws CustomException If the input value is not found in the feature JSON node.
	 */
	private String getBoundaryCodeValue(String input, JsonNode feature, Map<String, String> mappedValues) {
		if (feature.get(PROPERTIES).get(mappedValues.get(input)) != null) {
			String value = String.valueOf(feature.get(PROPERTIES).get(mappedValues.get(input)));
			return ((value != null && value.length() > 2) ? value.substring(1, value.length() - 1) : value);
		} else {
			throw new CustomException("INPUT_VALUE_NOT_FOUND", "Input value not found: " + input);
		}
	}
	
	/**
	 * Updates campaign resources with the provided file store ID.
	 *
	 * @param fileStoreId The file store ID.
	 * @param campaignResourcesList The list of campaign resources to update.
	 */
	public void updateCampaignResources(String fileStoreId,List<CampaignResources> campaignResourcesList,String fileName) {
			CampaignResources campaignResource = new CampaignResources();
			campaignResource.setFilename(fileName);
			campaignResource.setFilestoreId(fileStoreId);
			campaignResource.setType(ServiceConstants.FILE_TYPE);
			campaignResourcesList.add(campaignResource);
				
	}
	
	/**
	 * Builds a campaign search request based on the provided plan configuration request.
	 *
	 * @param planConfigurationRequest The plan configuration request containing necessary information for building the search request.
	 * @return The campaign search request object built for searching campaigns.
	 */
	public CampaignSearchRequest buildCampaignRequestForSearch(PlanConfigurationRequest planConfigurationRequest) {

		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
		List<String> id = new ArrayList<String>();
		id.add(planConfig.getCampaignId());
		return CampaignSearchRequest.builder().requestInfo(planConfigurationRequest.getRequestInfo())
				.campaignDetails(CampaignDetails.builder().ids(id).tenantId(planConfig.getTenantId()).build()).build();

	}

	/**
	 * Parses an object representing campaign response into a CampaignResponse object.
	 *
	 * @param campaignResponse The object representing campaign response to be parsed.
	 * @return CampaignResponse object parsed from the campaignResponse.
	 */
    public CampaignResponse parseCampaignResponse(Object campaignResponse) {
		CampaignResponse campaign = null;
		campaign = mapper.convertValue(campaignResponse, CampaignResponse.class);
		return campaign;
	}

	public JsonNode createAdditionalDetailsforFacilityCreate(String source, String microplanId) {
		try {
			// Create a map to hold the additional details
			Map<String, String> additionalDetailsMap = new HashMap<>();
			additionalDetailsMap.put(SOURCE_KEY, source);
			additionalDetailsMap.put(MICROPLAN_ID_KEY, microplanId);

			// Convert the map to a JsonNode
			return mapper.valueToTree(additionalDetailsMap);
		} catch (Exception e) {
			throw new CustomException(UNABLE_TO_CREATE_ADDITIONAL_DETAILS_CODE, UNABLE_TO_CREATE_ADDITIONAL_DETAILS_MESSAGE);// Or throw a custom exception
		}
	}
}
