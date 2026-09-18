package org.egov.healthnotification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.stock.SenderReceiverType;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FacilityUserServiceTest {

    @InjectMocks
    private FacilityUserService facilityUserService;

    @Mock
    private ServiceRequestClient serviceRequestClient;

    @Mock
    private HealthNotificationProperties properties;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolveUserUuids_staffType_returnsEntityIdDirectly() {
        List<String> result = facilityUserService.resolveUserUuids(
                "staff-uuid-1", SenderReceiverType.STAFF, "tenant1");

        assertEquals(List.of("staff-uuid-1"), result);
    }

    @Test
    void resolveUserUuids_nullEntityId_returnsEmpty() {
        List<String> result = facilityUserService.resolveUserUuids(
                null, SenderReceiverType.WAREHOUSE, "tenant1");

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveUserUuids_blankEntityId_returnsEmpty() {
        List<String> result = facilityUserService.resolveUserUuids(
                "  ", SenderReceiverType.STAFF, "tenant1");

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveFacilityName_staffType_returnsEntityId() {
        String result = facilityUserService.resolveFacilityName(
                "staff-id", SenderReceiverType.STAFF, "tenant1");

        assertEquals("staff-id", result);
    }

    @Test
    void resolveFacilityName_nullEntityId_returnsEmpty() {
        String result = facilityUserService.resolveFacilityName(
                null, SenderReceiverType.WAREHOUSE, "tenant1");

        assertEquals("", result);
    }

    @Test
    void resolveFacilityName_warehouseType_fetchesFacilityName() throws Exception {
        when(properties.getFacilityServiceHost()).thenReturn("http://facility-service");
        when(properties.getFacilitySearchUrl()).thenReturn("/facility/v1/_search");

        ObjectNode response = mapper.createObjectNode();
        ArrayNode facilities = mapper.createArrayNode();
        ObjectNode facility = mapper.createObjectNode();
        facility.put("name", "Central Warehouse");
        facilities.add(facility);
        response.set("Facilities", facilities);

        when(serviceRequestClient.fetchResult(any(), any(), eq(JsonNode.class)))
                .thenReturn(response);

        String result = facilityUserService.resolveFacilityName(
                "warehouse-1", SenderReceiverType.WAREHOUSE, "tenant1");

        assertEquals("Central Warehouse", result);
    }

    @Test
    void resolveFacilityName_warehouseType_apiError_returnsEntityId() throws Exception {
        when(properties.getFacilityServiceHost()).thenReturn("http://facility-service");
        when(properties.getFacilitySearchUrl()).thenReturn("/facility/v1/_search");

        when(serviceRequestClient.fetchResult(any(), any(), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("Service down"));

        String result = facilityUserService.resolveFacilityName(
                "warehouse-err", SenderReceiverType.WAREHOUSE, "tenant1");

        assertEquals("warehouse-err", result);
    }

    @Test
    void resolveFacilityName_warehouseType_noFacilitiesFound_returnsEntityId() throws Exception {
        when(properties.getFacilityServiceHost()).thenReturn("http://facility-service");
        when(properties.getFacilitySearchUrl()).thenReturn("/facility/v1/_search");

        ObjectNode response = mapper.createObjectNode();
        response.set("Facilities", mapper.createArrayNode());

        when(serviceRequestClient.fetchResult(any(), any(), eq(JsonNode.class)))
                .thenReturn(response);

        String result = facilityUserService.resolveFacilityName(
                "warehouse-missing", SenderReceiverType.WAREHOUSE, "tenant1");

        assertEquals("warehouse-missing", result);
    }
}