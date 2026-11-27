package org.egov.facility.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.facility.config.FacilityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoundaryDescendantServiceTest {

    @Mock
    private ServiceRequestClient serviceRequestClient;

    @Mock
    private FacilityConfiguration configuration;

    private BoundaryDescendantService service;

    @BeforeEach
    void setUp() {
        lenient().when(configuration.getBoundaryServiceHost()).thenReturn("http://boundary-service:8080");
        lenient().when(configuration.getBoundaryRelationshipSearchUrl()).thenReturn("/boundary-service/boundary-relationships/_search");
        lenient().when(configuration.getBoundaryHierarchySearchUrl()).thenReturn("/boundary-service/boundary-hierarchy-definition/_search");
        lenient().when(configuration.getFindHierarchyForDescendants()).thenReturn(true);
        service = new BoundaryDescendantService(serviceRequestClient, configuration);
    }

    private static Map<String, Object> node(String code, List<Object> children) {
        return Map.of("code", code, "children", children);
    }

    private static Map<String, Object> response(Object... roots) {
        return Map.of("TenantBoundary", List.of(Map.of("tenantId", "dev", "boundary", List.of(roots))));
    }

    @Test
    void returnsTheBoundaryAndEveryBoundaryBelowItWithoutAHierarchyType() {
        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(Map.class))).thenReturn(response(
                node("DISTRICT", List.of(node("VILLAGE_1", List.of()), node("VILLAGE_2", List.of(node("HAMLET", List.of())))))));

        List<String> codes = service.getCodesAtAndBelow("dev", "DISTRICT", RequestInfo.builder().build());

        assertEquals(List.of("DISTRICT", "VILLAGE_1", "VILLAGE_2", "HAMLET"), codes);
        ArgumentCaptor<StringBuilder> url = ArgumentCaptor.forClass(StringBuilder.class);
        verify(serviceRequestClient).fetchResult(url.capture(), any(), eq(Map.class));
        assertTrue(url.getValue().toString().contains("codes=DISTRICT"));
        assertTrue(url.getValue().toString().contains("includeChildren=true"));
        assertFalse(url.getValue().toString().contains("hierarchyType"));
    }

    @Test
    void isCachedPerTenantAndCode() {
        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(Map.class)))
                .thenReturn(response(node("DISTRICT", List.of(node("VILLAGE_1", List.of())))));

        service.getCodesAtAndBelow("dev", "DISTRICT", null);
        List<String> second = service.getCodesAtAndBelow("dev", "DISTRICT", null);

        assertEquals(List.of("DISTRICT", "VILLAGE_1"), second);
        verify(serviceRequestClient, times(1)).fetchResult(any(StringBuilder.class), any(), eq(Map.class));
    }

    @Test
    void whenBoundaryServiceFailsOnlyTheBoundaryItselfIsSearched() {
        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("boundary-service down"));

        assertEquals(List.of("DISTRICT"), service.getCodesAtAndBelow("dev", "DISTRICT", null));
        // not asked again on the next search right away
        assertEquals(List.of("DISTRICT"), service.getCodesAtAndBelow("dev", "DISTRICT", null));
        verify(serviceRequestClient, times(1)).fetchResult(any(StringBuilder.class), any(), eq(Map.class));
    }

    @Test
    void anEmptyAnswerStillSearchesTheBoundaryItself() {
        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(Map.class)))
                .thenReturn(Map.of("TenantBoundary", List.of(Map.of("tenantId", "dev", "boundary", List.of()))));

        assertEquals(List.of("UNKNOWN"), service.getCodesAtAndBelow("dev", "UNKNOWN", null));
    }

    private static final Map<String, Object> EMPTY = Map.of("TenantBoundary", List.of(Map.of("tenantId", "dev", "boundary", List.of())));

    private static Map<String, Object> hierarchies(int from, int to) {
        List<Object> list = new java.util.ArrayList<>();
        for (int i = from; i < to; i++) {
            list.add(Map.of("hierarchyType", "H" + i));
        }
        return Map.of("BoundaryHierarchy", list);
    }

    /** boundary-service as seen on dev: without hierarchyType it places only boundaries of its default order. */
    private void boundaryServiceWhere(String code, String hierarchy, Map<String, Object> tree, int hierarchyCount) {
        doAnswer(call -> {
            String url = call.getArgument(0).toString();
            if (url.contains("boundary-hierarchy-definition")) {
                Map<?, ?> criteria = (Map<?, ?>) ((Map<?, ?>) call.getArgument(1)).get("BoundaryTypeHierarchySearchCriteria");
                int offset = (Integer) criteria.get("offset");
                return hierarchies(offset, Math.min(offset + 100, hierarchyCount));
            }
            return url.contains("codes=" + code + "&") && url.endsWith("&hierarchyType=" + hierarchy) ? tree : EMPTY;
        }).when(serviceRequestClient).fetchResult(any(StringBuilder.class), any(), eq(Map.class));
    }

    private List<String> calledUrls(int times) {
        ArgumentCaptor<StringBuilder> url = ArgumentCaptor.forClass(StringBuilder.class);
        verify(serviceRequestClient, times(times)).fetchResult(url.capture(), any(), eq(Map.class));
        return url.getAllValues().stream().map(StringBuilder::toString).toList();
    }

    @Test
    void sendsTheHierarchyTypeWhenGiven() {
        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(Map.class)))
                .thenReturn(response(node("LOCALITY", List.of(node("VILLAGE", List.of())))));

        assertEquals(List.of("LOCALITY", "VILLAGE"), service.getCodesAtAndBelow("dev", "LOCALITY", "BM1807", null));

        assertTrue(calledUrls(1).get(0).endsWith("&hierarchyType=BM1807"));
    }

    @Test
    void findsTheHierarchyWhenBoundaryServiceCannotPlaceTheBoundaryWithoutOne() {
        boundaryServiceWhere("LOCALITY", "H2", response(node("LOCALITY", List.of(node("VILLAGE_1", List.of()), node("VILLAGE_2", List.of())))), 4);

        assertEquals(List.of("LOCALITY", "VILLAGE_1", "VILLAGE_2"), service.getCodesAtAndBelow("dev", "LOCALITY", null));

        List<String> urls = calledUrls(5);   // without a hierarchy, the hierarchy list, then H0, H1, H2
        assertFalse(urls.get(0).contains("hierarchyType"));
        assertTrue(urls.get(1).contains("boundary-hierarchy-definition"));
        assertTrue(urls.get(4).endsWith("&hierarchyType=H2"));
    }

    @Test
    void aHierarchyFoundOnceIsTriedFirstForTheNextBoundary() {
        boundaryServiceWhere("LOCALITY", "H3", response(node("LOCALITY", List.of())), 4);
        service.getCodesAtAndBelow("dev", "LOCALITY", null);
        boundaryServiceWhere("OTHER", "H3", response(node("OTHER", List.of(node("BELOW", List.of())))), 4);

        assertEquals(List.of("OTHER", "BELOW"), service.getCodesAtAndBelow("dev", "OTHER", null));

        List<String> urls = calledUrls(8);   // 6 for LOCALITY (none, list, H0..H3); OTHER: none, then H3 straight away (list cached)
        assertFalse(urls.get(6).contains("hierarchyType"));
        assertTrue(urls.get(7).endsWith("&hierarchyType=H3"));
    }

    @Test
    void readsTheHierarchyListInPages() {
        boundaryServiceWhere("LOCALITY", "H150", response(node("LOCALITY", List.of(node("VILLAGE", List.of())))), 160);

        assertEquals(List.of("LOCALITY", "VILLAGE"), service.getCodesAtAndBelow("dev", "LOCALITY", null));

        long listPages = calledUrls(1 + 2 + 151).stream().filter(u -> u.contains("boundary-hierarchy-definition")).count();
        assertEquals(2, listPages);
    }

    @Test
    void searchesOnlyTheBoundaryWhenNoHierarchyHasIt() {
        boundaryServiceWhere("LOCALITY", "NONE", response(node("LOCALITY", List.of())), 3);

        assertEquals(List.of("LOCALITY"), service.getCodesAtAndBelow("dev", "LOCALITY", null));
    }

    @Test
    void doesNotLookTheHierarchyUpWhenSwitchedOff() {
        when(configuration.getFindHierarchyForDescendants()).thenReturn(false);
        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(Map.class))).thenReturn(EMPTY);

        assertEquals(List.of("LOCALITY"), service.getCodesAtAndBelow("dev", "LOCALITY", null));

        calledUrls(1);
    }

    @Test
    void skipsAHierarchyThatCannotBeReadAndKeepsLooking() {
        doAnswer(call -> {
            String url = call.getArgument(0).toString();
            if (url.contains("boundary-hierarchy-definition")) {
                return hierarchies(0, 3);
            }
            if (url.endsWith("&hierarchyType=H0")) {
                throw new RuntimeException("bad hierarchy");
            }
            return url.endsWith("&hierarchyType=H1") ? response(node("LOCALITY", List.of(node("VILLAGE", List.of())))) : EMPTY;
        }).when(serviceRequestClient).fetchResult(any(StringBuilder.class), any(), eq(Map.class));

        assertEquals(List.of("LOCALITY", "VILLAGE"), service.getCodesAtAndBelow("dev", "LOCALITY", null));
    }

    @Test
    void passesAHierarchyNameWithASpaceUnencoded() {
        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(Map.class)))
                .thenReturn(response(node("LOCALITY", List.of())));

        service.getCodesAtAndBelow("dev", "LOCALITY", "MODFIXD K29", null);

        assertTrue(calledUrls(1).get(0).endsWith("&hierarchyType=MODFIXD K29"));
    }
}
