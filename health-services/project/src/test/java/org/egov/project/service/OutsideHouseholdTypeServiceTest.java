package org.egov.project.service;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.project.config.ProjectConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Site types are the active codes of MDMS HCM.OUTSIDE_HOUSEHOLD_TYPES, read at the state tenant and cached. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class OutsideHouseholdTypeServiceTest {

    @Mock private ServiceRequestClient serviceRequestClient;
    @Mock private ProjectConfiguration configuration;

    // Test inputs: the MDMS host and the v2 search endpoint each environment configures (egov.mdms.host, egov.mdms.search.v2.endpoint)
    private static final String MDMS_HOST = "http://mdms.test:8080/";
    private static final String MDMS_V2_SEARCH_ENDPOINT = "/mdms-service.test/v2/_search";

    private OutsideHouseholdTypeService service;

    @BeforeEach
    void setUp() {
        lenient().when(configuration.getMdmsHost()).thenReturn(MDMS_HOST);
        lenient().when(configuration.getMdmsV2EndPoint()).thenReturn(MDMS_V2_SEARCH_ENDPOINT);
        service = new OutsideHouseholdTypeService(serviceRequestClient, configuration);
    }

    @Test
    void readsOnlyActiveCodesAtTheStateTenant() {
        when(serviceRequestClient.fetchResult(any(), any(), eq(Map.class))).thenReturn(Map.of("mdms", List.of(
                row("OUTSIDE_HOUSEHOLD_PLACES", true, true), row("TRANSIT_POST", true, true),
                row("OLD_TYPE", false, true), row("DELETED_ROW", true, false))));

        assertEquals(List.of("OUTSIDE_HOUSEHOLD_PLACES", "TRANSIT_POST"), service.getTypes("mz.citya"));

        ArgumentCaptor<StringBuilder> url = ArgumentCaptor.forClass(StringBuilder.class);
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(serviceRequestClient).fetchResult(url.capture(), body.capture(), eq(Map.class));
        assertEquals(MDMS_HOST + MDMS_V2_SEARCH_ENDPOINT.substring(1), url.getValue().toString());
        Map<String, Object> criteria = (Map<String, Object>) ((Map<String, Object>) body.getValue()).get("MdmsCriteria");
        assertEquals("mz", criteria.get("tenantId"));
        assertEquals("HCM.OUTSIDE_HOUSEHOLD_TYPES", criteria.get("schemaCode"));
    }

    @Test
    void cachesTheListPerStateTenant() {
        when(serviceRequestClient.fetchResult(any(), any(), eq(Map.class)))
                .thenReturn(Map.of("mdms", List.of(row("TRANSIT_POST", true, true))));

        service.getTypes("mz.citya");
        service.getTypes("mz.cityb");

        verify(serviceRequestClient, times(1)).fetchResult(any(), any(), eq(Map.class));
    }

    @Test
    void mdmsFailureBeforeAnyReadUsesTheFallbackListAndDoesNotRetryOnEveryCall() {
        when(configuration.getOutsideHouseholdFallbackTypes()).thenReturn(List.of("outside_household_places", " TRANSIT_POST ", ""));
        when(serviceRequestClient.fetchResult(any(), any(), eq(Map.class))).thenThrow(new RuntimeException("MDMS down"));

        assertEquals(List.of("OUTSIDE_HOUSEHOLD_PLACES", "TRANSIT_POST"), service.getTypes("mz"));
        assertEquals(List.of("OUTSIDE_HOUSEHOLD_PLACES", "TRANSIT_POST"), service.getTypes("mz"));

        verify(serviceRequestClient, times(1)).fetchResult(any(), any(), eq(Map.class));
    }

    @Test
    void mdmsFailureWithNoFallbackListConfiguredGivesAnEmptyList() {
        when(configuration.getOutsideHouseholdFallbackTypes()).thenReturn(null);
        when(serviceRequestClient.fetchResult(any(), any(), eq(Map.class))).thenThrow(new RuntimeException("MDMS down"));

        assertTrue(service.getTypes("mz").isEmpty());
    }

    @Test
    void returnsTheCodesInUpperCase() {
        when(serviceRequestClient.fetchResult(any(), any(), eq(Map.class))).thenReturn(Map.of("mdms", List.of(row(" transit_post ", true, true))));

        assertEquals(List.of("TRANSIT_POST"), service.getTypes("mz"));
    }

    @Test
    void blankTenantGivesAnEmptyListWithoutCallingMdms() {
        assertTrue(service.getTypes(" ").isEmpty());
        verify(serviceRequestClient, times(0)).fetchResult(any(), any(), any());
    }

    private static Map<String, Object> row(String code, boolean active, boolean rowActive) {
        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("active", active);
        Map<String, Object> row = new HashMap<>();
        row.put("data", data);
        row.put("isActive", rowActive);
        return row;
    }
}
