package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.config.Configuration;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.models.File;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.campaignManager.*;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.processor.config.ErrorConstants.*;
import static org.egov.processor.config.ServiceConstants.*;

@Component
@Slf4j
public class CampaignIntegrationUtil {

	private ServiceRequestRepository serviceRequestRepository;
	private Configuration config;
	private ObjectMapper mapper;

	public CampaignIntegrationUtil(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper) {
		this.serviceRequestRepository = serviceRequestRepository;
		this.config = config;
		this.mapper = mapper;
	}

	/**
	 * Performs a campaign search based on the provided plan configuration request.
	 * This method builds a campaign search request using the integration utility,
	 * fetches the search result from the service request repository, and returns it.
	 *
	 * @param planConfigurationRequest The request object containing configuration details for the campaign search.
	 * @return The Campaign response object containing the result of the campaign search.
	 */
    public CampaignResponse performCampaignSearch(PlanConfigurationRequest planConfigurationRequest) {
		try {
			CampaignSearchRequest campaignRequest = buildCampaignRequestForSearch(planConfigurationRequest);
            Object response = serviceRequestRepository.fetchResult(new StringBuilder(
                    config.getProjectFactoryHostEndPoint() + config.getCampaignIntegrationSearchEndPoint()), campaignRequest);
            return mapper.convertValue(response, CampaignResponse.class);
		} catch (Exception e) {
			log.error(ERROR_FETCHING_CAMPAIGN_DETAILS_MESSAGE + planConfigurationRequest.getPlanConfiguration().getCampaignId(), e);
			throw new CustomException(ERROR_FETCHING_CAMPAIGN_DETAILS_CODE, ERROR_FETCHING_CAMPAIGN_DETAILS_MESSAGE);
		}
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
			log.debug("Updated resources file into project factory - " + fileStoreId);
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
			log.error(ERROR_WHILE_DATA_CREATE_CALL
					+ planConfigurationRequest.getPlanConfiguration().getCampaignId(), e);
			throw new CustomException("Failed to update campaign details in CampaignIntegration class within method updateCampaignDetails.", e.toString());
		}
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
				.orElseThrow(() -> new CustomException(FILE_NOT_FOUND_CODE, FILE_NOT_FOUND_TEMPLATE_IDENTIFIER_MESSAGE + FILE_TEMPLATE_IDENTIFIER_FACILITY)));

		ResourceDetails resourceDetails = ResourceDetails.builder()
				.type(TYPE_FACILITY)
				.hierarchyType(campaignResponse.getCampaign().get(0).getHierarchyType())
				.tenantId(planConfig.getTenantId())
				.fileStoreId(facilityFilestoreId)
				.action(ACTION_CREATE)
				.campaignId(planConfig.getCampaignId())
				.additionalDetails(createAdditionalDetailsForFacilityCreate(MICROPLAN_SOURCE_KEY, planConfig.getId()))
				.build();

		return ResourceDetailsRequest.builder()
				.requestInfo(planConfigurationRequest.getRequestInfo())
				.resourceDetails(resourceDetails)
				.build();

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
	 * Creates a JSON node containing additional details for facility creation.
	 *
	 * @param source       The source from which the facility creation request originates.
	 * @param microplanId  The microplan ID associated with the facility.
	 * @return 		       A JSON node representing the additional details.
	 */
	public JsonNode createAdditionalDetailsForFacilityCreate(String source, String microplanId) {
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
