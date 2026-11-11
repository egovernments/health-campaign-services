package org.egov.excelingestion.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The template's facility read: by the campaign's root boundary with includeDescendants (large pages), and the
 * whole-tenant read (pages of 50) when there is no root boundary or the root search fails.
 */
@ExtendWith(MockitoExtension.class)
class FacilityServiceTest {

    private static final String SEARCH_URL = "http://facility/facility/v1/_search";
    private static final RequestInfo REQUEST_INFO = RequestInfo.builder().build();

    @Mock private ServiceRequestRepository serviceRequestRepository;
    @Mock private ExcelIngestionConfig config;

    private FacilityService facilityService;

    @BeforeEach
    void setUp() {
        lenient().when(config.getFacilitySearchUrl()).thenReturn(SEARCH_URL);
        lenient().when(config.getFacilitySearchDescendantsPageSize()).thenReturn(2);
        facilityService = new FacilityService(serviceRequestRepository, config);
    }

    private static Map<String, Object> page(int size) {
        List<Map<String, Object>> facilities = IntStream.range(0, size)
                .mapToObj(i -> Map.<String, Object>of("id", "F-" + i)).collect(Collectors.toList());
        return Map.of("Facilities", facilities);
    }

    private List<String> calledUrls() {
        ArgumentCaptor<StringBuilder> uri = ArgumentCaptor.forClass(StringBuilder.class);
        verify(serviceRequestRepository, atLeastOnce()).fetchResult(uri.capture(), any());
        return uri.getAllValues().stream().map(StringBuilder::toString).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sentFilters() {
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(serviceRequestRepository, atLeastOnce()).fetchResult(any(), body.capture());
        List<Map<String, Object>> filters = new ArrayList<>();
        for (Object b : body.getAllValues()) {
            filters.add((Map<String, Object>) ((Map<String, Object>) b).get("Facility"));
        }
        return filters;
    }

    @Test
    void searchesByRootBoundaryWithDescendantsAndHierarchy() {
        when(serviceRequestRepository.fetchResult(any(), any())).thenReturn(page(1));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz.a", "CLF", "ROOT", "ADMIN", REQUEST_INFO);

        assertEquals(1, facilities.size());
        assertEquals(List.of(SEARCH_URL + "?limit=2&offset=0&tenantId=mz&includeDescendants=true&hierarchyType=ADMIN"), calledUrls());
        assertEquals(Map.of("isPermanent", true, "usage", "CLF", "boundaryCode", "ROOT"), sentFilters().get(0));
    }

    @Test
    void readsEveryPageOfTheRootSearch() {
        when(serviceRequestRepository.fetchResult(any(), any())).thenReturn(page(2), page(2), page(1));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz", null, "ROOT", "ADMIN", REQUEST_INFO);

        assertEquals(5, facilities.size());
        List<String> urls = calledUrls();
        assertEquals(3, urls.size());
        assertTrue(urls.get(2).contains("limit=2&offset=4"), urls.get(2));
        assertFalse(sentFilters().get(0).containsKey("usage"));
    }

    @Test
    void leavesOutHierarchyTypeWhenBlank() {
        when(serviceRequestRepository.fetchResult(any(), any())).thenReturn(page(0));

        facilityService.fetchPermanentFacilitiesAtAndBelow("mz", null, "ROOT", " ", REQUEST_INFO);

        assertEquals(List.of(SEARCH_URL + "?limit=2&offset=0&tenantId=mz&includeDescendants=true"), calledUrls());
    }

    @Test
    void readsEveryPermanentFacilityWhenThereIsNoRootBoundary() {
        when(serviceRequestRepository.fetchResult(any(), any())).thenReturn(page(3));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz", null, null, "ADMIN", REQUEST_INFO);

        assertEquals(3, facilities.size());
        assertEquals(List.of(SEARCH_URL + "?limit=50&offset=0&tenantId=mz"), calledUrls());
        assertFalse(sentFilters().get(0).containsKey("boundaryCode"));
    }

    @Test
    void fallsBackToEveryPermanentFacilityWhenTheRootSearchFails() {
        when(serviceRequestRepository.fetchResult(any(), any()))
                .thenThrow(new RuntimeException("facility search failed"))
                .thenReturn(page(3));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz", "CLF", "ROOT", "ADMIN", REQUEST_INFO);

        assertEquals(3, facilities.size());
        List<String> urls = calledUrls();
        assertEquals(SEARCH_URL + "?limit=50&offset=0&tenantId=mz", urls.get(1));
        assertEquals(Map.of("isPermanent", true, "usage", "CLF"), sentFilters().get(1));
    }

    @Test
    void wholeTenantReadStillReturnsAnEmptyListOnError() {
        when(serviceRequestRepository.fetchResult(any(), any())).thenThrow(new RuntimeException("down"));

        assertTrue(facilityService.fetchAllPermanentFacilities("mz", null, REQUEST_INFO).isEmpty());
    }
}
