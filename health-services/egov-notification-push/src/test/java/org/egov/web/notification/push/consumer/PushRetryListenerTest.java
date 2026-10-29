package org.egov.web.notification.push.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PushRetryListenerTest {

    @InjectMocks
    private PushRetryListener listener;

    @Mock
    private PushNotificationService pushNotificationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Acknowledgment ack;

    private static HashMap<String, Object> envelope(long notBefore) {
        HashMap<String, Object> record = new HashMap<>();
        record.put("request", Map.of("eventId", "e1", "tenantId", "ng", "deviceTokens", List.of("a")));
        record.put("attempt", 2);
        record.put("notBefore", notBefore);
        record.put("lastError", "INTERNAL");
        return record;
    }

    @Test
    void dueEnvelope_isSentWithItsAttemptAndAcked() {
        listener.handle(envelope(System.currentTimeMillis() - 1), ack);

        verify(pushNotificationService).sendPushNotification(any(PushNotificationRequest.class), eq(2));
        verify(ack).acknowledge();
    }

    @Test
    void futureEnvelope_isNackedWithCappedPause() {
        listener.handle(envelope(System.currentTimeMillis() + 10 * 60_000), ack);

        verify(ack).nack(Duration.ofMillis(PushRetryListener.MAX_PAUSE_MS));
        verify(pushNotificationService, never()).sendPushNotification(any(), anyInt());
    }

    @Test
    void notBeforeBeyondMaxDelay_isProcessedInsteadOfBlockingTier() {
        listener.handle(envelope(System.currentTimeMillis() + 365L * 24 * 3600 * 1000), ack);

        verify(pushNotificationService).sendPushNotification(any(PushNotificationRequest.class), eq(2));
        verify(ack).acknowledge();
    }
}
