package org.egov.facility.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.repository.FacilityStaffRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class FacilityAccessService {

    private final FacilityStaffRepository facilityStaffRepository;

    private final FacilityConfiguration configuration;

    public FacilityAccessService(FacilityStaffRepository facilityStaffRepository, FacilityConfiguration configuration) {
        this.facilityStaffRepository = facilityStaffRepository;
        this.configuration = configuration;
    }

    public boolean isRestricted(RequestInfo requestInfo) {
        User user = requestInfo == null ? null : requestInfo.getUserInfo();
        return user != null && user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> configuration.getRestrictedAccessRoleCode().equals(role.getCode()));
    }

    public Set<String> allowedFacilityIds(RequestInfo requestInfo, String tenantId) throws InvalidTenantIdException {
        String uuid = requestInfo.getUserInfo().getUuid();
        if (uuid == null) {
            return Set.of();
        }
        return facilityStaffRepository.findActiveFacilityIds(tenantId, uuid);
    }
}
