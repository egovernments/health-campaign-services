package org.egov.web.notification.push.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.service.DeviceTokenService;
import org.egov.web.notification.push.service.PushMetrics;
import org.egov.web.notification.push.service.PushNotificationService;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationListenerTest {

    private static final String TOPIC = "ba-egov.core.notification.push";

    @InjectMocks
    private PushNotificationListener listener;

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private DeviceTokenService deviceTokenService;

    @Mock
    private PushMetrics metrics;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void processPushNotification_withDeviceTokens_sendsDirectly() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("title", "Test Title");
        record.put("body", "Test Body");
        record.put("tenantId", "tenant1");
        record.put("deviceTokens", Arrays.asList("token1", "token2"));

        listener.processPushNotification(record, TOPIC, 2, 7L);

        verify(pushNotificationService).sendPushNotification(any(PushNotificationRequest.class));
        verify(deviceTokenService, never()).getTokensByFacilityId(any(), any());
    }

    @Test
    void processPushNotification_withFacilityId_resolvesTokensFromDb() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("title", "Stock Issue");
        record.put("body", "50 ITN Nets issued");
        record.put("tenantId", "tenant1");
        record.put("facilityId", "facility-123");
        Map<String, String> data = new HashMap<>();
        data.put("notificationType", "STOCK");
        data.put("eventType", "STOCK_ISSUE_PUSH_NOTIFICATION");
        record.put("data", data);

        List<DeviceToken> tokens = Arrays.asList(
                DeviceToken.builder().deviceToken("resolved-token-1").build(),
                DeviceToken.builder().deviceToken("resolved-token-2").build()
        );
        when(deviceTokenService.getTokensByFacilityId("facility-123", "tenant1")).thenReturn(tokens);

        listener.processPushNotification(record, TOPIC, 2, 7L);

        verify(deviceTokenService).getTokensByFacilityId("facility-123", "tenant1");
        ArgumentCaptor<PushNotificationRequest> captor = ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(pushNotificationService).sendPushNotification(captor.capture());

        PushNotificationRequest sent = captor.getValue();
        assertEquals(2, sent.getDeviceTokens().size());
        assertEquals("resolved-token-1", sent.getDeviceTokens().get(0));
        assertEquals("STOCK", sent.getData().get("notificationType"));
    }

    @Test
    void processPushNotification_withFacilityId_noTokensFound_skips() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("title", "Test");
        record.put("body", "Body");
        record.put("facilityId", "facility-empty");

        when(deviceTokenService.getTokensByFacilityId("facility-empty", null))
                .thenReturn(Collections.emptyList());

        listener.processPushNotification(record, TOPIC, 2, 7L);

        verify(deviceTokenService).getTokensByFacilityId("facility-empty", null);
        verify(pushNotificationService, never()).sendPushNotification(any());
    }

    @Test
    void processPushNotification_noTokensNoFacilityId_stillCallsSend() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("title", "Test");
        record.put("body", "Body");
        record.put("tenantId", "tenant1");

        listener.processPushNotification(record, TOPIC, 2, 7L);

        verify(pushNotificationService).sendPushNotification(any(PushNotificationRequest.class));
        verify(deviceTokenService, never()).getTokensByFacilityId(any(), any());
    }

    @Test
    void processPushNotification_exceptionDuringProcessing_propagates() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("title", "Test");
        record.put("body", "Body");
        record.put("facilityId", "facility-err");

        when(deviceTokenService.getTokensByFacilityId("facility-err", null))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> listener.processPushNotification(record, TOPIC, 2, 7L));
    }

    @Test
    void processPushNotification_dataMapPassedThrough() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("title", "Referral");
        record.put("body", "New referral");
        record.put("deviceTokens", List.of("tok1"));
        Map<String, String> data = new HashMap<>();
        data.put("notificationType", "REFERRAL");
        data.put("eventType", "HF_REFERRAL_PUSH_NOTIFICATION");
        data.put("screen", "REFERRAL_SCREEN");
        record.put("data", data);

        listener.processPushNotification(record, TOPIC, 2, 7L);

        ArgumentCaptor<PushNotificationRequest> captor = ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(pushNotificationService).sendPushNotification(captor.capture());

        Map<String, String> sentData = captor.getValue().getData();
        assertEquals("REFERRAL", sentData.get("notificationType"));
        assertEquals("HF_REFERRAL_PUSH_NOTIFICATION", sentData.get("eventType"));
        assertEquals("REFERRAL_SCREEN", sentData.get("screen"));
    }

    @Test
    void processPushNotification_withUserIds_resolvesTokensByUserBeforeFacility() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("title", "T");
        record.put("tenantId", "ng.kano");
        record.put("userIds", Arrays.asList("u1", "u2"));
        record.put("facilityId", "F1");
        when(deviceTokenService.getActiveTokensForUsers(Arrays.asList("u1", "u2"), "ng.kano")).thenReturn(Arrays.asList(
                DeviceToken.builder().deviceToken("t1").build(),
                DeviceToken.builder().deviceToken("t1").build(),
                DeviceToken.builder().deviceToken("t2").build()));

        listener.processPushNotification(record, TOPIC, 2, 7L);

        ArgumentCaptor<PushNotificationRequest> captor = ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(pushNotificationService).sendPushNotification(captor.capture());
        assertEquals(Arrays.asList("t1", "t2"), captor.getValue().getDeviceTokens());
        verify(deviceTokenService, never()).getTokensByFacilityId(any(), any());
    }

    @Test
    void processPushNotification_withUserIds_noTokens_skips() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("title", "T");
        record.put("tenantId", "ng");
        record.put("userIds", Arrays.asList("u1"));
        when(deviceTokenService.getActiveTokensForUsers(Arrays.asList("u1"), "ng")).thenReturn(Collections.emptyList());

        listener.processPushNotification(record, TOPIC, 2, 7L);

        verify(pushNotificationService, never()).sendPushNotification(any());
    }

    @Test
    void processPushNotification_missingEventId_usesRecordCoordinates() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("tenantId", "ba");
        record.put("deviceTokens", Arrays.asList("token1"));

        listener.processPushNotification(record, TOPIC, 2, 7L);

        ArgumentCaptor<PushNotificationRequest> captor = ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(pushNotificationService).sendPushNotification(captor.capture());
        assertEquals(TOPIC + "-2-7", captor.getValue().getEventId());
    }

    @Test
    void processPushNotification_givenEventId_isKept() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("tenantId", "ba");
        record.put("eventId", "stock-1");
        record.put("deviceTokens", Arrays.asList("token1"));

        listener.processPushNotification(record, TOPIC, 2, 7L);

        ArgumentCaptor<PushNotificationRequest> captor = ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(pushNotificationService).sendPushNotification(captor.capture());
        assertEquals("stock-1", captor.getValue().getEventId());
    }
}
