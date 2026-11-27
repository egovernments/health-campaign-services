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

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The template's facility read: by the campaign's root boundary with includeDescendants, and the whole-tenant read
 * when there is no root boundary, the root search fails, or it finds nothing below the root. Both read large pages.
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

    private static Map<String, Object> facility(String id, String boundaryCode) {
        return Map.of("id", id, "address", Map.of("locality", Map.of("code", boundaryCode)));
    }

    /** A page of facilities on a boundary below the root, as a search that read the boundaries below the root returns. */
    private static Map<String, Object> page(int size) {
        List<Map<String, Object>> facilities = IntStream.range(0, size)
                .mapToObj(i -> facility("F-" + i, "CHILD")).collect(Collectors.toList());
        return Map.of("Facilities", facilities);
    }

    private List<String> calledUrls() {
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(serviceRequestRepository, atLeastOnce()).fetchResultFromUri(uri.capture(), any());
        return uri.getAllValues().stream().map(URI::toString).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sentFilters() {
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(serviceRequestRepository, atLeastOnce()).fetchResultFromUri(any(), body.capture());
        List<Map<String, Object>> filters = new ArrayList<>();
        for (Object b : body.getAllValues()) {
            filters.add((Map<String, Object>) ((Map<String, Object>) b).get("Facility"));
        }
        return filters;
    }

    @Test
    void searchesByRootBoundaryWithDescendantsAndHierarchy() {
        when(serviceRequestRepository.fetchResultFromUri(any(), any())).thenReturn(page(1));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz.a", "CLF", "ROOT", "ADMIN", REQUEST_INFO);

        assertEquals(1, facilities.size());
        assertEquals(List.of(SEARCH_URL + "?limit=2&offset=0&tenantId=mz&includeDescendants=true&hierarchyType=ADMIN"), calledUrls());
        assertEquals(Map.of("isPermanent", true, "usage", "CLF", "boundaryCode", "ROOT"), sentFilters().get(0));
    }

    @Test
    void readsEveryPageOfTheRootSearch() {
        when(serviceRequestRepository.fetchResultFromUri(any(), any())).thenReturn(page(2), page(2), page(1));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz", null, "ROOT", "ADMIN", REQUEST_INFO);

        assertEquals(5, facilities.size());
        List<String> urls = calledUrls();
        assertEquals(3, urls.size());
        assertTrue(urls.get(2).contains("limit=2&offset=4"), urls.get(2));
        assertFalse(sentFilters().get(0).containsKey("usage"));
    }

    @Test
    void leavesOutHierarchyTypeWhenBlank() {
        when(serviceRequestRepository.fetchResultFromUri(any(), any())).thenReturn(page(0));

        facilityService.fetchPermanentFacilitiesAtAndBelow("mz", null, "ROOT", " ", REQUEST_INFO);

        assertEquals(SEARCH_URL + "?limit=2&offset=0&tenantId=mz&includeDescendants=true", calledUrls().get(0));
    }

    @Test
    void sendsHierarchyTypeWholeEvenWithReservedCharacters() {
        when(serviceRequestRepository.fetchResultFromUri(any(), any())).thenReturn(page(0));

        facilityService.fetchPermanentFacilitiesAtAndBelow("mz", null, "ROOT", "A&B#C+D E", REQUEST_INFO);

        URI uri = URI.create(calledUrls().get(0));
        assertNull(uri.getRawFragment(), "a # in the value must not start a fragment");
        Map<String, String> query = new java.util.HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        assertEquals("A&B#C+D E", query.get("hierarchyType"));
        assertEquals("true", query.get("includeDescendants"));
        assertEquals("mz", query.get("tenantId"));
    }

    @Test
    void readsEveryPermanentFacilityWhenThereIsNoRootBoundary() {
        when(serviceRequestRepository.fetchResultFromUri(any(), any())).thenReturn(page(2), page(1));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz", null, null, "ADMIN", REQUEST_INFO);

        assertEquals(3, facilities.size());
        assertEquals(List.of(SEARCH_URL + "?limit=2&offset=0&tenantId=mz", SEARCH_URL + "?limit=2&offset=2&tenantId=mz"), calledUrls());
        assertFalse(sentFilters().get(0).containsKey("boundaryCode"));
    }

    @Test
    void fallsBackToEveryPermanentFacilityWhenTheRootSearchFails() {
        when(serviceRequestRepository.fetchResultFromUri(any(), any()))
                .thenThrow(new RuntimeException("facility search failed"))
                .thenReturn(page(1));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz", "CLF", "ROOT", "ADMIN", REQUEST_INFO);

        assertEquals(1, facilities.size());
        List<String> urls = calledUrls();
        assertEquals(SEARCH_URL + "?limit=2&offset=0&tenantId=mz", urls.get(1));
        assertEquals(Map.of("isPermanent", true, "usage", "CLF"), sentFilters().get(1));
    }

    @Test
    void fallsBackToEveryPermanentFacilityWhenTheRootSearchFindsNothing() {
        // The facility search answers this way when it could not read the boundaries below the root
        when(serviceRequestRepository.fetchResultFromUri(any(), any())).thenReturn(page(0), page(1));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz", "CLF", "ROOT", "ADMIN", REQUEST_INFO);

        assertEquals(1, facilities.size());
        List<String> urls = calledUrls();
        assertEquals(2, urls.size());
        assertEquals(SEARCH_URL + "?limit=2&offset=0&tenantId=mz", urls.get(1));
        assertEquals(Map.of("isPermanent", true, "usage", "CLF"), sentFilters().get(1));
    }

    @Test
    void fallsBackToEveryPermanentFacilityWhenTheRootSearchFindsOnlyFacilitiesOnTheRoot() {
        Map<String, Object> onRoot = Map.of("Facilities", List.of(facility("F-ROOT", "ROOT")));
        when(serviceRequestRepository.fetchResultFromUri(any(), any())).thenReturn(onRoot, page(1));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz", null, "ROOT", "ADMIN", REQUEST_INFO);

        assertEquals(List.of("F-0"), facilities.stream().map(f -> f.get("id")).collect(Collectors.toList()));
        assertEquals(2, calledUrls().size());
        assertFalse(sentFilters().get(1).containsKey("boundaryCode"));
    }

    @Test
    void keepsTheRootSearchWhenAFacilityIsBelowTheRoot() {
        Map<String, Object> rootAndBelow = Map.of("Facilities", List.of(facility("F-ROOT", "ROOT"), facility("F-CHILD", "CHILD")));
        when(serviceRequestRepository.fetchResultFromUri(any(), any())).thenReturn(rootAndBelow, page(0));

        List<Map<String, Object>> facilities = facilityService.fetchPermanentFacilitiesAtAndBelow("mz", null, "ROOT", "ADMIN", REQUEST_INFO);

        assertEquals(2, facilities.size());
        assertTrue(calledUrls().stream().allMatch(url -> url.contains("includeDescendants=true")));
    }

    @Test
    void wholeTenantReadStillReturnsAnEmptyListOnError() {
        when(serviceRequestRepository.fetchResultFromUri(any(), any())).thenThrow(new RuntimeException("down"));

        assertTrue(facilityService.fetchAllPermanentFacilities("mz", null, REQUEST_INFO).isEmpty());
    }
}
