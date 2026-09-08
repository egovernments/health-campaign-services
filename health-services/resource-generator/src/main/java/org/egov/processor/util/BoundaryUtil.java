package org.egov.processor.util;

import static org.egov.processor.config.ServiceConstants.HIERARCHYTYPE_REPLACER;
import static org.egov.processor.config.ServiceConstants.TENANTID_REPLACER;

import org.egov.processor.config.Configuration;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.boundary.BoundarySearchResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BoundaryUtil {

	private Configuration config;

	private ServiceRequestRepository serviceRequestRepository;

	private ObjectMapper mapper;

	public BoundaryUtil(Configuration config, ServiceRequestRepository serviceRequestRepository, ObjectMapper mapper) {
		this.config = config;
		this.serviceRequestRepository = serviceRequestRepository;
		this.mapper = mapper;
	}

	/**
	 * Searches for boundary relationships based on the given tenant ID and hierarchy type.
	 *
	 * @param tenantId                 The tenant ID for which the boundary search is performed.
	 * @param hierarchyType            The hierarchy type of the boundary.
	 * @param planConfigurationRequest The request object containing request info.
	 */
	public BoundarySearchResponse search(String tenantId, String hierarchyType,PlanConfigurationRequest planConfigurationRequest) {
		String boundaryRelationShipSearchLink = getBoundaryRelationShipSearchLink(tenantId, hierarchyType);
		Object response;
		try {
			response = serviceRequestRepository.fetchResult(new StringBuilder(boundaryRelationShipSearchLink),planConfigurationRequest.getRequestInfo());
			return mapper.convertValue(response, BoundarySearchResponse.class);

		} catch (Exception ex) {
			log.error("Boundary relationship response error!!", ex);
			throw new CustomException("BOUNDARY_SEARCH_EXCEPTION", "Exception occurs while searhing boundary or parsing search response of boundary relationship for tenantId: "+tenantId);
		}
	}

	/**
	 * Constructs the boundary relationship search URL by replacing placeholders with actual values.
	 *
	 * @param tenantId      The tenant ID to be included in the search URL.
	 * @param hierarchyType The hierarchy type to be included in the search URL.
	 * @return       		The constructed boundary relationship search URL.
	 */
	private String getBoundaryRelationShipSearchLink(String tenantId, String hierarchyType) {
		String fileStoreServiceLink = config.getEgovBoundaryServiceHost() + config.getEgovBoundaryRelationshipSearchEndpoint();
		fileStoreServiceLink = fileStoreServiceLink.replace(TENANTID_REPLACER, tenantId);
		fileStoreServiceLink = fileStoreServiceLink.replace(HIERARCHYTYPE_REPLACER, hierarchyType);
		return fileStoreServiceLink;
	}
}
