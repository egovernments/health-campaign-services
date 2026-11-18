package org.egov.stock.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.stock.config.StockConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FacilityAccessService {

    private static final int STAFF_SEARCH_LIMIT = 1000;

    private final StockConfiguration stockConfiguration;

    private final ServiceRequestClient serviceRequestClient;

    public FacilityAccessService(StockConfiguration stockConfiguration, ServiceRequestClient serviceRequestClient) {
        this.stockConfiguration = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
    }

    public boolean isRestricted(RequestInfo requestInfo) {
        return requestInfo != null
                && requestInfo.getUserInfo() != null
                && requestInfo.getUserInfo().getRoles() != null
                && requestInfo.getUserInfo().getRoles().stream()
                .anyMatch(role -> stockConfiguration.getFacilityRestrictedAccessRoleCode().equals(role.getCode()));
    }

    public Set<String> allowedFacilityIds(RequestInfo requestInfo, String tenantId) {
        String staffId = requestInfo.getUserInfo().getUuid();
        Map<String, Object> request = new HashMap<>();
        request.put("RequestInfo", requestInfo);
        request.put("FacilityStaff", Collections.singletonMap("staffId", Collections.singletonList(staffId)));
        try {
            FacilityStaffSearchResponse response = serviceRequestClient.fetchResult(
                    new StringBuilder(stockConfiguration.getFacilityServiceHost()
                            + stockConfiguration.getFacilityStaffSearchUrl()
                            + "?tenantId=" + tenantId
                            + "&limit=" + STAFF_SEARCH_LIMIT
                            + "&offset=0"),
                    request,
                    FacilityStaffSearchResponse.class);
            if (response == null || response.getFacilityStaff() == null) {
                return Collections.emptySet();
            }
            return response.getFacilityStaff().stream()
                    .filter(staff -> !Boolean.TRUE.equals(staff.getIsDeleted()))
                    .map(FacilityStaffEntry::getFacilityId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("error while fetching facility staff mappings for tenant {}", tenantId, e);
            throw new CustomException("FACILITY_STAFF_SEARCH_FAILED",
                    "Unable to resolve facilities accessible to the user");
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class FacilityStaffSearchResponse {
        @JsonProperty("FacilityStaff")
        private List<FacilityStaffEntry> facilityStaff;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class FacilityStaffEntry {
        private String facilityId;
        private Boolean isDeleted;
    }
}
