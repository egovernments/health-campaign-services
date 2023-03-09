package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.upstream.FacilitySearch;
import org.egov.transformer.models.upstream.FacilitySearchRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
@Service
@Slf4j
public class FacilityService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;

    public FacilityService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
    }

    public JsonNode findFacilityById(String facilityId, String tenantId) {

        FacilitySearchRequest facilitySearchRequest = FacilitySearchRequest.builder()
                .facility(FacilitySearch.builder().id(Collections.singletonList(facilityId)).build())
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .build();

        try {
            JsonNode response = serviceRequestClient.fetchResult(
                    new StringBuilder(properties.getFacilityHost()
                            + properties.getFacilitySearchUrl()
                            + "?limit=1"
                            + "&offset=0&tenantId=" + tenantId),
                    facilitySearchRequest,
                    JsonNode.class);
            return response.get("Facilities");
        } catch (Exception e) {
            log.error("error while fetching facility", e);
            return null;
        }
    }
}
