package org.egov.healthnotification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalizationServiceTest {

    @InjectMocks
    private LocalizationService localizationService;

    @Mock
    private ServiceRequestClient serviceRequestClient;

    @Mock
    private HealthNotificationProperties properties;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode buildLocalizationResponse(String code, String message) {
        ObjectNode response = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();
        ObjectNode msgNode = mapper.createObjectNode();
        msgNode.put("code", code);
        msgNode.put("message", message);
        messages.add(msgNode);
        response.set("messages", messages);
        return response;
    }

    @Test
    void fetchLocalizationMessage_validResponse_returnsMessage() throws Exception {
        when(properties.getLocalizationHost()).thenReturn("http://localization");
        when(properties.getLocalizationContextPath()).thenReturn("/localization");
        when(properties.getLocalizationSearchEndpoint()).thenReturn("/messages/v1/_search");
        when(properties.getLocalizationStateLevel()).thenReturn(true);
        when(properties.getLocalizationNotificationModule()).thenReturn("health-notification");

        JsonNode response = buildLocalizationResponse(
                "STOCK_ISSUE_TEMPLATE", "{Sending_Facility_Name} issued {quantity_of_sku}");

        when(serviceRequestClient.fetchResult(any(), any(), eq(JsonNode.class)))
                .thenReturn(response);

        String result = localizationService.fetchLocalizationMessage(
                "STOCK_ISSUE_TEMPLATE", "en_NG", "tenant1.sub");

        assertEquals("{Sending_Facility_Name} issued {quantity_of_sku}", result);
    }

    @Test
    void fetchLocalizationMessage_noMessages_throwsCustomException() throws Exception {
        when(properties.getLocalizationHost()).thenReturn("http://localization");
        when(properties.getLocalizationContextPath()).thenReturn("/localization");
        when(properties.getLocalizationSearchEndpoint()).thenReturn("/messages/v1/_search");
        when(properties.getLocalizationStateLevel()).thenReturn(false);
        when(properties.getLocalizationNotificationModule()).thenReturn("health-notification");

        ObjectNode emptyResponse = mapper.createObjectNode();
        emptyResponse.set("messages", mapper.createArrayNode());

        when(serviceRequestClient.fetchResult(any(), any(), eq(JsonNode.class)))
                .thenReturn(emptyResponse);

        assertThrows(CustomException.class, () ->
                localizationService.fetchLocalizationMessage(
                        "MISSING_TEMPLATE", "en_NG", "tenant1"));
    }

    @Test
    void fetchLocalizationMessages_multipleTemplates_returnsMap() throws Exception {
        when(properties.getLocalizationHost()).thenReturn("http://localization");
        when(properties.getLocalizationContextPath()).thenReturn("/localization");
        when(properties.getLocalizationSearchEndpoint()).thenReturn("/messages/v1/_search");
        when(properties.getLocalizationStateLevel()).thenReturn(true);
        when(properties.getLocalizationNotificationModule()).thenReturn("health-notification");

        when(serviceRequestClient.fetchResult(any(), any(), eq(JsonNode.class)))
                .thenReturn(buildLocalizationResponse("TEMPLATE_A", "Message A"))
                .thenReturn(buildLocalizationResponse("TEMPLATE_B", "Message B"));

        Map<String, String> result = localizationService.fetchLocalizationMessages(
                List.of("TEMPLATE_A", "TEMPLATE_B"), "en_NG", "tenant1.sub");

        assertEquals(2, result.size());
        assertEquals("Message A", result.get("TEMPLATE_A"));
        assertEquals("Message B", result.get("TEMPLATE_B"));
    }

    @Test
    void fetchLocalizationMessages_allFail_throwsCustomException() throws Exception {
        when(properties.getLocalizationHost()).thenReturn("http://localization");
        when(properties.getLocalizationContextPath()).thenReturn("/localization");
        when(properties.getLocalizationSearchEndpoint()).thenReturn("/messages/v1/_search");
        when(properties.getLocalizationStateLevel()).thenReturn(true);
        when(properties.getLocalizationNotificationModule()).thenReturn("health-notification");

        when(serviceRequestClient.fetchResult(any(), any(), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("Service down"));

        assertThrows(CustomException.class, () ->
                localizationService.fetchLocalizationMessages(
                        List.of("TEMPLATE_A"), "en_NG", "tenant1.sub"));
    }

    @Test
    void getMessageTemplate_cachedMessage_returnsCached() throws Exception {
        // First call populates cache via API
        when(properties.getLocalizationHost()).thenReturn("http://localization");
        when(properties.getLocalizationContextPath()).thenReturn("/localization");
        when(properties.getLocalizationSearchEndpoint()).thenReturn("/messages/v1/_search");
        when(properties.getLocalizationStateLevel()).thenReturn(true);
        when(properties.getLocalizationNotificationModule()).thenReturn("health-notification");

        when(serviceRequestClient.fetchResult(any(), any(), eq(JsonNode.class)))
                .thenReturn(buildLocalizationResponse("CACHED_CODE", "Cached message"));

        String first = localizationService.getMessageTemplate("CACHED_CODE", "en_NG", "tenant1.sub");
        String second = localizationService.getMessageTemplate("CACHED_CODE", "en_NG", "tenant1.sub");

        assertEquals("Cached message", first);
        assertEquals("Cached message", second);
    }
}