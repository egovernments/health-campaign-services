package org.egov.web.notification.push.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.egov.tracer.model.CustomException;
import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.egov.web.notification.push.web.contract.PushNotificationApiRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationApiServiceTest {

    @InjectMocks
    private PushNotificationApiService apiService;

    @Mock
    private DeviceTokenService deviceTokenService;

    @Mock
    private PushNotificationService pushNotificationService;

    @Test
    void sendNotification_withUserUuids_resolvesTokensAndSends() {
        List<String> userUuids = Arrays.asList("uuid-1", "uuid-2");
        List<DeviceToken> resolved = Arrays.asList(
                DeviceToken.builder().deviceToken("tok-1").build(),
                DeviceToken.builder().deviceToken("tok-2").build()
        );
        when(deviceTokenService.getActiveTokensForUsers(userUuids)).thenReturn(resolved);

        PushNotificationApiRequest request = PushNotificationApiRequest.builder()
                .title("Title")
                .body("Body")
                .userUuids(userUuids)
                .data(Map.of("notificationType", "STOCK"))
                .tenantId("tenant1")
                .build();

        int count = apiService.sendNotification(request);

        assertEquals(2, count);
        ArgumentCaptor<PushNotificationRequest> captor = ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(pushNotificationService).sendPushNotification(captor.capture());

        PushNotificationRequest sent = captor.getValue();
        assertEquals("Title", sent.getTitle());
        assertEquals("Body", sent.getBody());
        assertEquals("STOCK", sent.getData().get("notificationType"));
        assertEquals(2, sent.getDeviceTokens().size());
    }

    @Test
    void sendNotification_withDirectDeviceTokens_sendsDirectly() {
        PushNotificationApiRequest request = PushNotificationApiRequest.builder()
                .title("Title")
                .body("Body")
                .deviceTokens(List.of("direct-token"))
                .tenantId("tenant1")
                .build();

        int count = apiService.sendNotification(request);

        assertEquals(1, count);
        verify(deviceTokenService, never()).getActiveTokensForUsers(any());
        verify(pushNotificationService).sendPushNotification(any(PushNotificationRequest.class));
    }

    @Test
    void sendNotification_withBothUuidsAndTokens_deduplicates() {
        List<DeviceToken> resolved = List.of(
                DeviceToken.builder().deviceToken("shared-token").build()
        );
        when(deviceTokenService.getActiveTokensForUsers(List.of("uuid-1"))).thenReturn(resolved);

        PushNotificationApiRequest request = PushNotificationApiRequest.builder()
                .title("Title")
                .body("Body")
                .userUuids(List.of("uuid-1"))
                .deviceTokens(List.of("shared-token", "extra-token"))
                .tenantId("tenant1")
                .build();

        int count = apiService.sendNotification(request);

        // "shared-token" appears in both resolved and direct, should be deduplicated
        assertEquals(2, count); // shared-token + extra-token
    }

    @Test
    void sendNotification_noTokensResolved_returnsZero() {
        when(deviceTokenService.getActiveTokensForUsers(List.of("uuid-no-tokens")))
                .thenReturn(Collections.emptyList());

        PushNotificationApiRequest request = PushNotificationApiRequest.builder()
                .title("Title")
                .body("Body")
                .userUuids(List.of("uuid-no-tokens"))
                .tenantId("tenant1")
                .build();

        int count = apiService.sendNotification(request);

        assertEquals(0, count);
        verify(pushNotificationService, never()).sendPushNotification(any());
    }

    @Test
    void sendNotification_missingTitle_throwsCustomException() {
        PushNotificationApiRequest request = PushNotificationApiRequest.builder()
                .body("Body")
                .deviceTokens(List.of("tok"))
                .build();

        CustomException ex = assertThrows(CustomException.class, () -> apiService.sendNotification(request));
        assertEquals("PUSH_MISSING_TITLE", ex.getCode());
    }

    @Test
    void sendNotification_missingBody_throwsCustomException() {
        PushNotificationApiRequest request = PushNotificationApiRequest.builder()
                .title("Title")
                .deviceTokens(List.of("tok"))
                .build();

        CustomException ex = assertThrows(CustomException.class, () -> apiService.sendNotification(request));
        assertEquals("PUSH_MISSING_BODY", ex.getCode());
    }

    @Test
    void sendNotification_missingRecipients_throwsCustomException() {
        PushNotificationApiRequest request = PushNotificationApiRequest.builder()
                .title("Title")
                .body("Body")
                .build();

        CustomException ex = assertThrows(CustomException.class, () -> apiService.sendNotification(request));
        assertEquals("PUSH_MISSING_RECIPIENTS", ex.getCode());
    }

    @Test
    void sendNotification_dataMapPassedThroughToRequest() {
        Map<String, String> data = Map.of(
                "notificationType", "REFERRAL",
                "eventType", "HF_REFERRAL_PUSH_NOTIFICATION",
                "screen", "REFERRAL_SCREEN"
        );

        PushNotificationApiRequest request = PushNotificationApiRequest.builder()
                .title("Referral")
                .body("New referral received")
                .deviceTokens(List.of("tok1"))
                .data(data)
                .tenantId("tenant1")
                .build();

        apiService.sendNotification(request);

        ArgumentCaptor<PushNotificationRequest> captor = ArgumentCaptor.forClass(PushNotificationRequest.class);
        verify(pushNotificationService).sendPushNotification(captor.capture());

        Map<String, String> sentData = captor.getValue().getData();
        assertEquals("REFERRAL", sentData.get("notificationType"));
        assertEquals("HF_REFERRAL_PUSH_NOTIFICATION", sentData.get("eventType"));
        assertEquals("REFERRAL_SCREEN", sentData.get("screen"));
    }
}
