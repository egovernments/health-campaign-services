package org.egov.referralmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkResponse;
import org.egov.common.models.facility.FacilitySearch;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;

@Service
@Slf4j
public class FacilityService {

    private final ReferralManagementConfiguration referralManagementConfiguration;

    private final ServiceRequestClient serviceRequestClient;

    public FacilityService(ReferralManagementConfiguration referralManagementConfiguration, ServiceRequestClient serviceRequestClient) {
        this.referralManagementConfiguration = referralManagementConfiguration;
        this.serviceRequestClient = serviceRequestClient;
    }

	public <T> List<String> validateFacilityIds(List<String> entityIds,
                                             List<T> entities,
                                             String tenantId,
                                             Map<T, List<Error>> errorDetailsMap,
                                             RequestInfo requestInfo) {

		if (CollectionUtils.isEmpty(entityIds))
			return Collections.emptyList();
		
        FacilitySearchRequest facilitySearchRequest = FacilitySearchRequest.builder()
                .facility(FacilitySearch.builder().id(entityIds).build())
                .requestInfo(requestInfo)
                .build();

        try {
            FacilityBulkResponse response = serviceRequestClient.fetchResult(
                    new StringBuilder(referralManagementConfiguration.getFacilityHost()
                            + referralManagementConfiguration.getFacilitySearchUrl()
                            + "?limit=" + entityIds.size()
                            + "&offset=0&tenantId=" + tenantId),
                    facilitySearchRequest,
                    FacilityBulkResponse.class);
            return response.getFacilities().stream().map(Facility::getId).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("error while fetching facility list", e);
            entities.forEach( entity -> {
                Error error = getErrorForEntityWithNetworkError();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
            return Collections.emptyList();
        }
    }
}
