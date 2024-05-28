package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.facility.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.egov.transformer.Constants.*;
import static org.egov.transformer.Constants.SATELLITE_WAREHOUSE;

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
            log.error("error while fetching facility {}", ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    public Long getFacilityTarget(Facility facility) {
        AdditionalFields facilityAdditionalFields = facility.getAdditionalFields();
        if (facilityAdditionalFields != null) {
            List<Field> fields = facilityAdditionalFields.getFields();
            Optional<Field> field = fields.stream().filter(field1 -> FACILITY_TARGET_KEY.equalsIgnoreCase(field1.getKey())).findFirst();
            if (field.isPresent() && field.get().getValue() != null) {
                return Long.valueOf(field.get().getValue());
            }
        }
        return null;
    }

    public String getFacilityLevel(Facility facility) {
        String facilityUsage = facility.getUsage();
        if (facilityUsage != null) {
            return WAREHOUSE.equalsIgnoreCase(facilityUsage) ?
                    (facility.getIsPermanent() ? DISTRICT_WAREHOUSE : SATELLITE_WAREHOUSE) : null;
        }
        return null;
    }
    public String getType(String transactingFacilityType, Facility transactingFacility) {
        AdditionalFields transactingFacilityAdditionalFields = transactingFacility.getAdditionalFields();
        if (transactingFacilityAdditionalFields != null) {
            List<Field> fields = transactingFacilityAdditionalFields.getFields();
            Optional<Field> field = fields.stream().filter(field1 -> TYPE_KEY.equalsIgnoreCase(field1.getKey())).findFirst();
            if (field.isPresent() && field.get().getValue() != null) {
                transactingFacilityType = field.get().getValue();
            }
        }
        return transactingFacilityType;
    }
}
