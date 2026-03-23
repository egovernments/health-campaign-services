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

    @InjectMocks
    private PushNotificationListener listener;

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private DeviceTokenService deviceTokenService;

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

        listener.processPushNotification(record);

        verify(pushNotificationService).sendPushNotification(any(PushNotificationRequest.class));
        verify(deviceTokenService, never()).getTokensByFacilityId(any());
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
        when(deviceTokenService.getTokensByFacilityId("facility-123")).thenReturn(tokens);

        listener.processPushNotification(record);

        verify(deviceTokenService).getTokensByFacilityId("facility-123");
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

        when(deviceTokenService.getTokensByFacilityId("facility-empty"))
                .thenReturn(Collections.emptyList());

        listener.processPushNotification(record);

        verify(deviceTokenService).getTokensByFacilityId("facility-empty");
        verify(pushNotificationService, never()).sendPushNotification(any());
    }

    @Test
    void processPushNotification_noTokensNoFacilityId_stillCallsSend() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("title", "Test");
        record.put("body", "Body");
        record.put("tenantId", "tenant1");

        listener.processPushNotification(record);

        verify(pushNotificationService).sendPushNotification(any(PushNotificationRequest.class));
        verify(deviceTokenService, never()).getTokensByFacilityId(any());
    }

    @Test
    void processPushNotification_exceptionDuringProcessing_doesNotThrow() {
        HashMap<String, Object> record = new HashMap<>();
        record.put("title", "Test");
        record.put("body", "Body");
        record.put("facilityId", "facility-err");

        when(deviceTokenService.getTokensByFacilityId("facility-err"))
                .thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> listener.processPushNotification(record));
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

        listener.processPushNotification(record);

        ArgumentCaptor<PushNotificationRequest> captor = ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(pushNotificationService).sendPushNotification(captor.capture());

        Map<String, String> sentData = captor.getValue().getData();
        assertEquals("REFERRAL", sentData.get("notificationType"));
        assertEquals("HF_REFERRAL_PUSH_NOTIFICATION", sentData.get("eventType"));
        assertEquals("REFERRAL_SCREEN", sentData.get("screen"));
    }
}
