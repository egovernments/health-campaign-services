package org.egov.project.service;

import org.egov.common.ds.Tuple;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.models.project.ProjectFacilityBulkResponse;
import org.egov.common.models.project.ProjectFacilitySearch;
import org.egov.common.models.project.ProjectFacilitySearchRequest;
import org.egov.project.repository.ProjectFacilityRepository;
import org.egov.project.validator.facility.PfAllowedFacilityValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityAccessRestrictionTest {

    private static final String TENANT = "default";

    @Mock
    private FacilityAccessService facilityAccessService;

    @Mock
    private ProjectFacilityRepository repository;

    @InjectMocks
    private ProjectFacilityService service;

    private final RequestInfo requestInfo = RequestInfo.builder().build();

    private ProjectFacilitySearchRequest searchRequest(List<String> facilityIds) {
        return ProjectFacilitySearchRequest.builder()
                .requestInfo(requestInfo)
                .projectFacility(ProjectFacilitySearch.builder().projectId(Collections.singletonList("p1"))
                        .facilityId(facilityIds).build())
                .build();
    }

    private ProjectFacilityBulkRequest bulkRequest(String... facilityIds) {
        List<ProjectFacility> items = Arrays.stream(facilityIds)
                .map(id -> ProjectFacility.builder().facilityId(id).tenantId(TENANT).build())
                .collect(java.util.stream.Collectors.toList());
        return ProjectFacilityBulkRequest.builder().requestInfo(requestInfo).projectFacilities(items).build();
    }

    @Test
    void should_not_lookup_and_return_no_errors_when_user_is_unrestricted() {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(false);

        Map<ProjectFacility, List<Error>> errors =
                new PfAllowedFacilityValidator(facilityAccessService).validate(bulkRequest("f1"));

        assertTrue(errors.isEmpty());
        verify(facilityAccessService, never()).allowedFacilityIds(any(), anyString());
    }

    @Test
    void should_flag_only_non_allowed_facility_when_user_is_restricted() {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(true);
        when(facilityAccessService.allowedFacilityIds(requestInfo, TENANT)).thenReturn(Set.of("f1"));
        ProjectFacilityBulkRequest request = bulkRequest("f1", "f2");

        Map<ProjectFacility, List<Error>> errors = new PfAllowedFacilityValidator(facilityAccessService).validate(request);

        assertEquals(1, errors.size());
        ProjectFacility denied = errors.keySet().iterator().next();
        assertEquals("f2", denied.getFacilityId());
        assertEquals("FACILITY_ACCESS_DENIED", errors.get(denied).get(0).getErrorCode());
    }

    @Test
    void should_return_empty_without_repository_query_when_restricted_user_has_no_mappings() throws Exception {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(true);
        when(facilityAccessService.allowedFacilityIds(requestInfo, TENANT)).thenReturn(Collections.emptySet());

        ProjectFacilityBulkResponse response = service.searchFacilities(searchRequest(null), 10, 0, TENANT, null, false);

        assertEquals(0L, response.getTotalCount());
        assertTrue(response.getProjectFacilities().isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void should_intersect_requested_facility_ids_with_allowed_when_user_is_restricted() throws Exception {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(true);
        when(facilityAccessService.allowedFacilityIds(requestInfo, TENANT)).thenReturn(Set.of("f1", "f2"));
        when(repository.findWithCount(any(), anyInt(), anyInt(), anyString(), any(), anyBoolean()))
                .thenReturn(new SearchResponse<>(1L, Collections.emptyList()));

        service.searchFacilities(searchRequest(Arrays.asList("f2", "f9")), 10, 0, TENANT, null, false);

        ArgumentCaptor<ProjectFacilitySearch> captor = ArgumentCaptor.forClass(ProjectFacilitySearch.class);
        verify(repository).findWithCount(captor.capture(), eq(10), eq(0), eq(TENANT), isNull(), eq(false));
        assertEquals(Collections.singletonList("f2"), captor.getValue().getFacilityId());
    }

    @Test
    void should_search_unchanged_for_unrestricted_user() throws Exception {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(false);
        when(repository.findWithCount(any(), anyInt(), anyInt(), anyString(), any(), anyBoolean()))
                .thenReturn(new SearchResponse<>(0L, Collections.emptyList()));
        ProjectFacilitySearchRequest request = searchRequest(Collections.singletonList("f9"));

        service.searchFacilities(request, 10, 5, TENANT, 7L, false);

        verify(repository).findWithCount(request.getProjectFacility(), 10, 5, TENANT, 7L, false);
        assertEquals(Collections.singletonList("f9"), request.getProjectFacility().getFacilityId());
        verify(facilityAccessService, never()).allowedFacilityIds(any(), anyString());
    }

    @Test
    void should_skip_restriction_for_internal_search() throws Exception {
        when(repository.findWithCount(any(), anyInt(), anyInt(), anyString(), any(), anyBoolean()))
                .thenReturn(new SearchResponse<>(0L, Collections.emptyList()));
        ProjectFacilitySearchRequest request = searchRequest(null);

        service.searchFacilities(request, 10, 0, TENANT, null, false, false);

        verify(repository).findWithCount(request.getProjectFacility(), 10, 0, TENANT, null, false);
        verifyNoInteractions(facilityAccessService);
    }
}
