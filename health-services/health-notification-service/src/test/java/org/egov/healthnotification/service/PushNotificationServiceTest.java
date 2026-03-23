package org.egov.healthnotification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.producer.HealthNotificationProducer;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @InjectMocks
    private PushNotificationService pushNotificationService;

    @Mock
    private HealthNotificationProducer producer;

    @Mock
    private HealthNotificationProperties properties;

    @Test
    void sendPushNotification_enabled_publishesToKafka() {
        when(properties.getPushNotificationEnabled()).thenReturn(true);
        when(properties.getPushNotificationTopic()).thenReturn("egov.core.notification.push");

        Map<String, String> data = Map.of("notificationType", "STOCK", "eventType", "STOCK_ISSUE_PUSH_NOTIFICATION");

        pushNotificationService.sendPushNotification(
                "Stock Issue", "50 ITN Nets issued", "facility-123", "tenant1", data);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(producer).push(eq("egov.core.notification.push"), captor.capture());

        Map<String, Object> payload = captor.getValue();
        assertEquals("Stock Issue", payload.get("title"));
        assertEquals("50 ITN Nets issued", payload.get("body"));
        assertEquals("facility-123", payload.get("facilityId"));
        assertEquals("tenant1", payload.get("tenantId"));
        assertEquals(data, payload.get("data"));
    }

    @Test
    void sendPushNotification_disabled_skips() {
        when(properties.getPushNotificationEnabled()).thenReturn(false);

        pushNotificationService.sendPushNotification(
                "Title", "Body", "fac-1", "tenant1", null);

        verify(producer, never()).push(any(), any());
    }

    @Test
    void sendPushNotification_nullFacilityId_skips() {
        when(properties.getPushNotificationEnabled()).thenReturn(true);

        pushNotificationService.sendPushNotification(
                "Title", "Body", null, "tenant1", null);

        verify(producer, never()).push(any(), any());
    }

    @Test
    void sendPushNotification_blankFacilityId_skips() {
        when(properties.getPushNotificationEnabled()).thenReturn(true);

        pushNotificationService.sendPushNotification(
                "Title", "Body", "  ", "tenant1", null);

        verify(producer, never()).push(any(), any());
    }

    @Test
    void sendPushNotification_nullData_doesNotIncludeDataField() {
        when(properties.getPushNotificationEnabled()).thenReturn(true);
        when(properties.getPushNotificationTopic()).thenReturn("push-topic");

        pushNotificationService.sendPushNotification(
                "Title", "Body", "fac-1", "tenant1", null);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(producer).push(eq("push-topic"), captor.capture());

        assertFalse(captor.getValue().containsKey("data"));
    }

    @Test
    void sendPushNotification_emptyData_doesNotIncludeDataField() {
        when(properties.getPushNotificationEnabled()).thenReturn(true);
        when(properties.getPushNotificationTopic()).thenReturn("push-topic");

        pushNotificationService.sendPushNotification(
                "Title", "Body", "fac-1", "tenant1", Map.of());

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(producer).push(eq("push-topic"), captor.capture());

        assertFalse(captor.getValue().containsKey("data"));
    }

    @Test
    void sendPushNotification_producerThrows_wrapsInCustomException() {
        when(properties.getPushNotificationEnabled()).thenReturn(true);
        when(properties.getPushNotificationTopic()).thenReturn("push-topic");
        org.mockito.Mockito.doThrow(new RuntimeException("Kafka down"))
                .when(producer).push(any(), any());

        assertThrows(CustomException.class, () ->
                pushNotificationService.sendPushNotification(
                        "Title", "Body", "fac-1", "tenant1", Map.of("key", "val")));
    }
}