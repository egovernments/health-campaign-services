package org.egov.web.notification.push.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import java.util.Map;

import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.junit.jupiter.api.Test;

class ConsolePushServiceTest {

    private final ConsolePushService consolePushService = new ConsolePushService();

    @Test
    void sendPushNotification_logsWithoutException() {
        PushNotificationRequest request = PushNotificationRequest.builder()
                .title("Test Title")
                .body("Test Body")
                .tenantId("tenant1")
                .facilityId("facility-1")
                .data(Map.of("notificationType", "STOCK", "eventType", "STOCK_ISSUE_PUSH_NOTIFICATION"))
                .deviceTokens(List.of("token1", "token2"))
                .build();

        assertDoesNotThrow(() -> consolePushService.sendPushNotification(request));
    }

    @Test
    void sendPushNotification_nullFields_doesNotThrow() {
        PushNotificationRequest request = PushNotificationRequest.builder().build();

        assertDoesNotThrow(() -> consolePushService.sendPushNotification(request));
    }
}
