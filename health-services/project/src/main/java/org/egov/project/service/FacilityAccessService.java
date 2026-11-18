package org.egov.project.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.project.config.ProjectConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FacilityAccessService {

    private static final String FACILITY_ACCESS_LOOKUP_FAILED = "FACILITY_ACCESS_LOOKUP_FAILED";

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    public FacilityAccessService(ServiceRequestClient serviceRequestClient, ProjectConfiguration projectConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
    }

    public boolean isRestricted(RequestInfo requestInfo) {
        if (requestInfo == null || requestInfo.getUserInfo() == null || requestInfo.getUserInfo().getRoles() == null) {
            return false;
        }
        String restrictedRoleCode = projectConfiguration.getFacilityRestrictedAccessRoleCode();
        return requestInfo.getUserInfo().getRoles().stream()
                .anyMatch(role -> role != null && restrictedRoleCode.equals(role.getCode()));
    }

    public Set<String> allowedFacilityIds(RequestInfo requestInfo, String tenantId) {
        Map<String, Object> body = Map.of(
                "RequestInfo", requestInfo,
                "FacilityStaff", Map.of("staffId", Collections.singletonList(requestInfo.getUserInfo().getUuid())));
        StringBuilder url = new StringBuilder(projectConfiguration.getFacilityServiceHost()
                + projectConfiguration.getFacilityStaffSearchUrl()
                + "?limit=1000&offset=0&tenantId=" + tenantId);
        try {
            FacilityStaffSearchResponse response = serviceRequestClient.fetchResult(url, body, FacilityStaffSearchResponse.class);
            if (response == null || response.getFacilityStaff() == null) {
                return Collections.emptySet();
            }
            return response.getFacilityStaff().stream()
                    .map(FacilityStaffRow::getFacilityId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("error while fetching facility staff mappings for tenant {}", tenantId, e);
            throw new CustomException(FACILITY_ACCESS_LOOKUP_FAILED, "Unable to resolve facility access: " + e.getMessage());
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class FacilityStaffSearchResponse {
        @JsonProperty("FacilityStaff")
        private List<FacilityStaffRow> facilityStaff;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class FacilityStaffRow {
        private String facilityId;
    }
}
