package org.egov.facility.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.models.facility.FacilitySearch;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.repository.FacilityRepository;
import org.egov.facility.repository.FacilityStaffRepository;
import org.egov.facility.validator.FAllowedFacilityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FacilityAccessRestrictionTest {

    private static final String ROLE = "FACILITY_RESTRICTED_ACCESS";

    private FacilityStaffRepository staffRepository;
    private FacilityRepository facilityRepository;
    private FacilityAccessService accessService;
    private FacilityService facilityService;
    private FAllowedFacilityValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        staffRepository = mock(FacilityStaffRepository.class);
        facilityRepository = mock(FacilityRepository.class);
        FacilityConfiguration configuration = new FacilityConfiguration();
        configuration.setRestrictedAccessRoleCode(ROLE);
        accessService = new FacilityAccessService(staffRepository, configuration);
        facilityService = new FacilityService(facilityRepository, List.of(), configuration, null, accessService);
        validator = new FAllowedFacilityValidator(accessService);
        when(staffRepository.findActiveFacilityIds("ng", "u1")).thenReturn(Set.of("F1"));
    }

    private RequestInfo requestInfo(String... roles) {
        List<Role> roleList = new ArrayList<>();
        for (String role : roles) {
            roleList.add(Role.builder().code(role).tenantId("ng").build());
        }
        return RequestInfo.builder().userInfo(User.builder().uuid("u1").roles(roleList).build()).build();
    }

    private Facility facility(String id) {
        return Facility.builder().id(id).name("name-" + id).tenantId("ng").build();
    }

    @Test
    void unrestrictedUserHasNoValidationErrorsAndNoLookup() throws Exception {
        FacilityBulkRequest request = FacilityBulkRequest.builder().requestInfo(requestInfo("WAREHOUSE_MANAGER"))
                .facilities(List.of(facility("F2"))).build();
        assertTrue(validator.validate(request).isEmpty());
        verify(staffRepository, never()).findActiveFacilityIds(anyString(), anyString());
    }

    @Test
    void restrictedUserIsDeniedOnNonAllowedAndNewFacilities() {
        Facility allowed = facility("F1");
        Facility other = facility("F2");
        Facility created = facility(null);
        FacilityBulkRequest request = FacilityBulkRequest.builder().requestInfo(requestInfo(ROLE))
                .facilities(List.of(allowed, other, created)).build();
        var errors = validator.validate(request);
        assertEquals(2, errors.size());
        assertTrue(errors.containsKey(other) && errors.containsKey(created));
    }

    @Test
    void restrictedSearchPassesAllowedIdsToQuery() throws Exception {
        FacilitySearch search = FacilitySearch.builder().build();
        when(facilityRepository.find(any(), any(), any(), any(), any(), any(), eq(Set.of("F1"))))
                .thenReturn(SearchResponse.<Facility>builder().response(List.of(facility("F1"))).totalCount(1L).build());
        SearchResponse<Facility> response = facilityService.search(FacilitySearchRequest.builder()
                .requestInfo(requestInfo(ROLE)).facility(search).build(), 10, 0, "ng", null, false);
        assertEquals(1, response.getResponse().size());
    }

    @Test
    void restrictedUserWithoutMappingGetsEmptySearchWithoutQuery() throws Exception {
        when(staffRepository.findActiveFacilityIds("ng", "u1")).thenReturn(Set.of());
        SearchResponse<Facility> response = facilityService.search(FacilitySearchRequest.builder()
                .requestInfo(requestInfo(ROLE)).facility(FacilitySearch.builder().build()).build(), 10, 0, "ng", null, false);
        assertTrue(response.getResponse().isEmpty());
        assertEquals(0L, response.getTotalCount());
        verify(facilityRepository, never()).find(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void internalSearchSkipsRestriction() throws Exception {
        when(facilityRepository.find(any(), any(), any(), any(), any(), any(), eq(null)))
                .thenReturn(SearchResponse.<Facility>builder().response(List.of(facility("F2"))).totalCount(1L).build());
        SearchResponse<Facility> response = facilityService.search(FacilitySearchRequest.builder()
                .requestInfo(requestInfo(ROLE)).facility(FacilitySearch.builder().build()).build(),
                10, 0, "ng", null, false, false);
        assertEquals("F2", response.getResponse().get(0).getId());
    }

    @Test
    void unrestrictedSearchIsUnchanged() throws Exception {
        FacilitySearch search = FacilitySearch.builder().build();
        when(facilityRepository.find(search, 10, 0, "ng", null, false, null))
                .thenReturn(SearchResponse.<Facility>builder().response(List.of(facility("F2"))).totalCount(1L).build());
        SearchResponse<Facility> response = facilityService.search(FacilitySearchRequest.builder()
                .requestInfo(requestInfo("WAREHOUSE_MANAGER")).facility(search).build(), 10, 0, "ng", null, false);
        assertEquals(1L, response.getTotalCount());
        verify(facilityRepository).find(search, 10, 0, "ng", null, false, null);
        verify(staffRepository, never()).findActiveFacilityIds(anyString(), anyString());
    }
}
