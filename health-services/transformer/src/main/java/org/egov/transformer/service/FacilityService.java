package org.egov.transformer.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilitySearch;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.transformer.config.TransformerProperties;
import org.egov.common.http.client.ServiceRequestClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FacilityService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;

    private static final Map<String, Facility> facilityMap = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public FacilityService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, ObjectMapper objectMapper) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
    }

    public void updateFacilitiesInCache(List<Facility> facilities) {
        facilities.forEach(facility -> facilityMap.put(facility.getId(), facility));
    }

    public Facility findFacilityById(String facilityId, String tenantId) {

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
            List<Facility> facilities = Arrays.asList(objectMapper.convertValue(response.get("Facilities"), Facility[].class));
            updateFacilitiesInCache(facilities);
            return facilities.isEmpty() ? null : facilities.get(0);
        } catch (Exception e) {
            log.error("error while fetching facility", e);
            return null;
        }
    }
}
