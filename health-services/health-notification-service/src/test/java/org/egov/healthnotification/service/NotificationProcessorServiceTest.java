package org.egov.healthnotification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.web.models.NotificationEvent;
import org.egov.healthnotification.web.models.enums.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationProcessorServiceTest {

    @InjectMocks
    private NotificationProcessorService processorService;

    @Mock
    private LocalizationService localizationService;

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private HealthNotificationProperties properties;

    private NotificationEvent buildPushEvent() {
        return NotificationEvent.builder()
                .tenantId("tenant1")
                .eventType("STOCK_ISSUE_PUSH_NOTIFICATION")
                .entityType("STOCK")
                .entityId("stock-1")
                .templateCode("STOCK_ISSUE_TEMPLATE")
                .titleTemplateCode("STOCK_ISSUE_TITLE")
                .locale("en_NG")
                .recipientFacilityId("facility-123")
                .placeholders(Map.of(
                        "Sending_Facility_Name", "Warehouse A",
                        "quantity_of_sku", "50 ITN Nets"
                ))
                .data(Map.of("notificationType", "STOCK", "eventType", "STOCK_ISSUE_PUSH_NOTIFICATION"))
                .channel(NotificationChannel.PUSH)
                .build();
    }

    @Test
    void processAndSend_pushChannel_sendsNotification() {
        NotificationEvent event = buildPushEvent();

        when(properties.getNotificationTimezone()).thenReturn("UTC");
        when(localizationService.getMessageTemplate("STOCK_ISSUE_TEMPLATE", "en_NG", "tenant1"))
                .thenReturn("{Sending_Facility_Name} issued {quantity_of_sku}");
        when(localizationService.getMessageTemplate("STOCK_ISSUE_TITLE", "en_NG", "tenant1"))
                .thenReturn("Stock Issue");

        processorService.processAndSend(event);

        verify(pushNotificationService).sendPushNotification(
                eq("Stock Issue"),
                eq("Warehouse A issued 50 ITN Nets"),
                eq("facility-123"),
                eq("tenant1"),
                eq(event.getData()));
    }

    @Test
    void processAndSend_noTitleTemplate_usesEventTypeAsTitle() {
        NotificationEvent event = buildPushEvent();
        event.setTitleTemplateCode(null);

        when(properties.getNotificationTimezone()).thenReturn("UTC");
        when(localizationService.getMessageTemplate("STOCK_ISSUE_TEMPLATE", "en_NG", "tenant1"))
                .thenReturn("Body text");

        processorService.processAndSend(event);

        verify(pushNotificationService).sendPushNotification(
                eq("STOCK_ISSUE_PUSH_NOTIFICATION"),
                eq("Body text"),
                eq("facility-123"),
                eq("tenant1"),
                any());
    }

    @Test
    void processAndSend_nullLocale_defaultsToEnNG() {
        NotificationEvent event = buildPushEvent();
        event.setLocale(null);

        when(properties.getNotificationTimezone()).thenReturn("UTC");
        when(localizationService.getMessageTemplate(eq("STOCK_ISSUE_TEMPLATE"), eq("en_NG"), any()))
                .thenReturn("Body");
        when(localizationService.getMessageTemplate(eq("STOCK_ISSUE_TITLE"), eq("en_NG"), any()))
                .thenReturn("Title");

        processorService.processAndSend(event);

        verify(localizationService).getMessageTemplate("STOCK_ISSUE_TEMPLATE", "en_NG", "tenant1");
    }

    @Test
    void processAndSend_smsChannel_logsWarningDoesNotSend() {
        NotificationEvent event = buildPushEvent();
        event.setChannel(NotificationChannel.SMS);

        when(properties.getNotificationTimezone()).thenReturn("UTC");
        when(localizationService.getMessageTemplate(any(), any(), any())).thenReturn("Body");

        processorService.processAndSend(event);

        verify(pushNotificationService, never()).sendPushNotification(any(), any(), any(), any(), any());
    }

    @Test
    void processAndSendBatch_nullOrEmpty_doesNothing() {
        processorService.processAndSendBatch(null);
        processorService.processAndSendBatch(Collections.emptyList());

        verify(pushNotificationService, never()).sendPushNotification(any(), any(), any(), any(), any());
    }

    @Test
    void processAndSendBatch_multipleEvents_processesAll() {
        NotificationEvent event1 = buildPushEvent();
        NotificationEvent event2 = buildPushEvent();
        event2.setEntityId("stock-2");

        when(properties.getNotificationTimezone()).thenReturn("UTC");
        when(localizationService.getMessageTemplate(any(), any(), any())).thenReturn("Body");

        processorService.processAndSendBatch(Arrays.asList(event1, event2));

        verify(pushNotificationService, times(2))
                .sendPushNotification(any(), any(), any(), any(), any());
    }

    @Test
    void processAndSendBatch_oneEventFails_continuesWithRest() {
        NotificationEvent event1 = buildPushEvent();
        NotificationEvent event2 = buildPushEvent();
        event2.setEntityId("stock-2");

        when(properties.getNotificationTimezone()).thenReturn("UTC");
        when(localizationService.getMessageTemplate(eq("STOCK_ISSUE_TEMPLATE"), any(), any()))
                .thenThrow(new RuntimeException("Localization error"))
                .thenReturn("Body for stock-2");
        when(localizationService.getMessageTemplate(eq("STOCK_ISSUE_TITLE"), any(), any()))
                .thenReturn("Title");

        assertDoesNotThrow(() ->
                processorService.processAndSendBatch(Arrays.asList(event1, event2)));
    }
}