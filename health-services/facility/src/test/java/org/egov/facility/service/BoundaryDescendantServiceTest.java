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
}
