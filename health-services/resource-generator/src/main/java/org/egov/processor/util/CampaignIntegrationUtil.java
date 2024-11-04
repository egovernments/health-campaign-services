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

	public CampaignIntegrationUtil(ServiceRequestRepository serviceRequestRepository, Configuration config,
			ObjectMapper mapper, FilestoreUtil filestoreUtil, ParsingUtil parsingUtil) {

		this.serviceRequestRepository = serviceRequestRepository;
		this.config = config;
		this.mapper = mapper;
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
		List<Map.Entry<String, Integer>> sortedColumnList = sortColumnByIndex(mapOfColumnNameAndIndex);
		indexValue = getIndexOfBoundaryCode(indexValue, sortedColumnList, mappedValues);
		prepareBoundary(indexOfType, indexValue, sortedColumnList, feature, boundary, mappedValues);
		if (isValidToAdd(boundaryList, resultMap, validToAdd, boundary))
			boundaryList.add(boundary);
	}

	/**
	 * Retrieves the index value of the boundary code from the sorted column list based on the mapped values.
	 *
	 * @param indexValue The initial index value.
	 * @param sortedColumnList The sorted list of column names and indices.
	 * @param mappedValues The map containing mapped values.
	 * @return The index value of the boundary code.
	 */
	public Integer getIndexOfBoundaryCode(Integer indexValue, List<Map.Entry<String, Integer>> sortedColumnList,Map<String, String> mappedValues) {
		for (Map.Entry<String, Integer> entry : sortedColumnList) {
			if (entry.getKey().equals(mappedValues.get(ServiceConstants.BOUNDARY_CODE))) {
				indexValue = entry.getValue();
			}
		}
		return indexValue;
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
	 * Sorts the column names and indices based on the provided map of column names and indices.
	 *
	 * @param mapOfColumnNameAndIndex The map containing column names and their corresponding indices.
	 * @return The sorted list of column names and indices.
	 */
	public List<Map.Entry<String, Integer>> sortColumnByIndex(Map<String, Integer> mapOfColumnNameAndIndex) {
		List<Map.Entry<String, Integer>> sortedColumnList = new ArrayList<>(mapOfColumnNameAndIndex.entrySet());
		Collections.sort(sortedColumnList, new Comparator<Map.Entry<String, Integer>>() {
			@Override
			public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
				return o1.getValue().compareTo(o2.getValue());
			}
		});
		return sortedColumnList;
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
