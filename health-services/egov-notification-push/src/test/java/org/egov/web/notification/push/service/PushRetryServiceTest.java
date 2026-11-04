package org.egov.web.notification.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.consumer.contract.RetryEnvelope;
import org.egov.web.notification.push.producer.DeviceTokenProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushRetryServiceTest {

    @InjectMocks
    private PushRetryService service;

    @Mock
    private DeviceTokenProducer producer;

    @Mock
    private PushProperties properties;

    @Mock
    private PushMetrics metrics;

    private final PushNotificationRequest request = PushNotificationRequest.builder()
            .eventId("e1").tenantId("ng").title("T").facilityId("F1").recipientRoles(List.of("R"))
            .deviceTokens(List.of("a", "b", "c")).build();

    @BeforeEach
    void setUp() {
        when(properties.retryTopics()).thenReturn(List.of("r1", "r2", "r3"));
    }

    private RetryEnvelope published(String topic) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq("ng"), eq(topic), eq("e1"), captor.capture());
        return (RetryEnvelope) captor.getValue();
    }

    @Test
    void firstFailure_goesToTier1WithOnlyFailedTokens() {
        when(properties.getRetryDelaysMs()).thenReturn(List.of(60_000L, 300_000L, 1_800_000L));
        long before = System.currentTimeMillis();

        service.schedule(request, List.of("b"), 0, "INTERNAL", 0);

        RetryEnvelope envelope = published("r1");
        assertEquals(1, envelope.getAttempt());
        assertEquals(List.of("b"), envelope.getRequest().getDeviceTokens());
        assertNull(envelope.getRequest().getFacilityId());
        assertNull(envelope.getRequest().getRecipientRoles());
        assertTrue(envelope.getNotBefore() >= before + 60_000);
        assertEquals("INTERNAL", envelope.getLastError());
    }

    @Test
    void retryAfterLongerThanTierDelay_isHonoured() {
        when(properties.getRetryDelaysMs()).thenReturn(List.of(60_000L, 300_000L, 1_800_000L));
        long before = System.currentTimeMillis();

        service.schedule(request, List.of("b"), 1, "QUOTA_EXCEEDED", 600_000);

        assertTrue(published("r2").getNotBefore() >= before + 600_000);
    }

    @Test
    void attemptsUsedUp_goesToDeadLetter() {
        when(properties.getDeadLetterTopic()).thenReturn("dlq");

        service.schedule(request, List.of("c"), 3, "INTERNAL", 0);

        RetryEnvelope envelope = published("dlq");
        assertEquals(3, envelope.getAttempt());
        assertEquals(List.of("c"), envelope.getRequest().getDeviceTokens());
    }

    @Test
    void hugeRetryAfter_isCappedSoTierIsNotBlocked() {
        when(properties.getRetryDelaysMs()).thenReturn(List.of(60_000L, 300_000L, 1_800_000L));
        long before = System.currentTimeMillis();

        service.schedule(request, List.of("b"), 0, "QUOTA_EXCEEDED", Long.MAX_VALUE);

        assertTrue(published("r1").getNotBefore() <= before + PushRetryService.MAX_DELAY_MS + 1000);
    }
}
