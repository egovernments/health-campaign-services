package org.egov.referralmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkResponse;
import org.egov.common.models.facility.FacilitySearch;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;

/**
 * Facility Service that validates facility IDs using an API call.
 */
@Service
@Slf4j
public class FacilityService {

    private final ReferralManagementConfiguration referralManagementConfiguration;

    private final ServiceRequestClient serviceRequestClient;

    @Autowired
    public FacilityService(ReferralManagementConfiguration referralManagementConfiguration, ServiceRequestClient serviceRequestClient) {
        this.referralManagementConfiguration = referralManagementConfiguration;
        this.serviceRequestClient = serviceRequestClient;
    }

    /**
     * Validate a list of facility IDs by making an API call.
     *
     * @param entityIds        List of facility IDs to validate.
     * @param entities         List of entities associated with the facility IDs.
     * @param tenantId         Tenant ID for filtering facilities.
     * @param errorDetailsMap  A map to store error details for each entity.
     * @param requestInfo      Request information for the API call.
     * @return List of valid facility IDs.
     */
	public <T> List<String> validateFacilityIds(List<String> entityIds,
                                             List<T> entities,
                                             String tenantId,
                                             Map<T, List<Error>> errorDetailsMap,
                                             RequestInfo requestInfo) {
        // Check if the entityIds list is empty, return an empty list if so.
		if (CollectionUtils.isEmpty(entityIds))
			return Collections.emptyList();

        // Create a FacilitySearchRequest to fetch facility information for the given IDs.
        FacilitySearchRequest facilitySearchRequest = FacilitySearchRequest.builder()
                .facility(FacilitySearch.builder().id(entityIds).build())
                .requestInfo(requestInfo)
                .build();

        try {
            // Make an API call to fetch facilities based on entity IDs.
            FacilityBulkResponse response = serviceRequestClient.fetchResult(
                    new StringBuilder(referralManagementConfiguration.getFacilityHost())
                            .append(referralManagementConfiguration.getFacilitySearchUrl())
                            .append("?limit=").append(entityIds.size())
                            .append("&offset=0&tenantId=").append(tenantId),
                    facilitySearchRequest,
                    FacilityBulkResponse.class);

            // Extract and return valid facility IDs from the response.
            return response.getFacilities().stream().map(Facility::getId).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("error while fetching facility list: {}", ExceptionUtils.getStackTrace(e));

            // Handle errors by associating errors with the respective entities.
            entities.forEach( entity -> {
                Error error = getErrorForEntityWithNetworkError();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
        }

        // Return an empty list in case of an error.
        return Collections.emptyList();
    }
}
