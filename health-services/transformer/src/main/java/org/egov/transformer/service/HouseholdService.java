package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkResponse;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.models.household.HouseholdSearchRequest;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class HouseholdService {
    private final TransformerProperties transformerProperties;

    private final ServiceRequestClient serviceRequestClient;

    public HouseholdService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
    }

    public List<Household> searchHousehold(String clientRefId, String tenantId) {
        HouseholdSearchRequest request = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .household(HouseholdSearch.builder().
                        clientReferenceId(Collections.singletonList(clientRefId)).build())
                .build();
        HouseholdBulkResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getHouseholdHost())
                    .append(transformerProperties.getHouseholdSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    HouseholdBulkResponse.class);
        } catch (Exception e) {
            log.error("error while fetching household", e);
            throw new CustomException("HOUSEHOLD_FETCH_ERROR",
                    "error while fetching household details for id: " + clientRefId);
        }
        return response.getHouseholds();
    }
}
