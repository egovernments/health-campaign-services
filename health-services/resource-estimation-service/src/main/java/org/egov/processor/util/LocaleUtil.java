package org.egov.processor.util;

import java.util.List;

import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.models.LocaleResponse;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.boundary.BoundarySearchResponse;
import org.egov.processor.web.models.campaignManager.Boundary;
import org.egov.processor.web.models.campaignManager.CampaignResources;
import org.egov.processor.web.models.campaignManager.CampaignResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for handling operations related to locale data retrieval and processing.
 * This class encapsulates methods to search for locale information based on plan configuration requests.
 * It utilizes a service request repository to interact with external services and ObjectMapper for JSON conversion.
 */
@Component
@Slf4j
public class LocaleUtil {

	private ServiceRequestRepository serviceRequestRepository;
	private Configuration config;
	private ObjectMapper mapper;

	   /**
     * Constructs a LocaleUtil instance with necessary dependencies.
     *
     * @param serviceRequestRepository The repository for making service requests.
     * @param config                   Configuration settings for the application.
     * @param mapper                   ObjectMapper for JSON serialization/deserialization.
     */
	public LocaleUtil(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper) {

		this.serviceRequestRepository = serviceRequestRepository;
		this.config = config;
		this.mapper = mapper;
	}

	/**
	 * Searches for locale information based on the provided plan configuration request.
	 * Retrieves locale-specific data using service request repository and converts the response
	 * into a LocaleResponse object.
	 *
	 * @param planConfigurationRequest The request containing configuration details including request info.
	 * @return LocaleResponse containing locale-specific information.
	 * @throws CustomException If an error occurs during the locale search process.
	 */
	public LocaleResponse searchLocale(PlanConfigurationRequest planConfigurationRequest) {
		Object response;
		String localeToUse = planConfigurationRequest.getRequestInfo().getMsgId().split("\\|")[1];
		String tenantId = planConfigurationRequest.getRequestInfo().getUserInfo().getTenantId();
		LocaleResponse localeResponse = null;
		try {

			String url = config.getEgovLocaleSearchEndpoint()
					.replace("{module}", ServiceConstants.MDMS_LOCALE_SEARCH_MODULE)
					.replace("{locale}", localeToUse)
					.replace("{tenantId}", tenantId);
			response = serviceRequestRepository.fetchResult(new StringBuilder(config.getEgovLocaleServiceHost() + url),
					planConfigurationRequest.getRequestInfo());
			localeResponse = mapper.convertValue(response, LocaleResponse.class);
			log.info("Locale Search successful.");
			return localeResponse;
		} catch (Exception e) {
			log.error(ServiceConstants.ERROR_WHILE_SEARCHING_LOCALE + localeToUse + " and tenantId" + tenantId, e);
			throw new CustomException(
					ServiceConstants.ERROR_WHILE_SEARCHING_LOCALE + localeToUse + " and tenantId" + tenantId,
					e.toString());
		}
	}
}
