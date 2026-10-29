package org.egov.web.notification.push.service;

import java.util.List;

import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.consumer.contract.RetryEnvelope;
import org.egov.web.notification.push.producer.DeviceTokenProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PushRetryService {

    public static final long MAX_DELAY_MS = 3_600_000;

    @Autowired
    private DeviceTokenProducer producer;

    @Autowired
    private PushProperties properties;

    @Autowired
    private PushMetrics metrics;

    public void schedule(PushNotificationRequest request, List<String> tokens, int attempt, String lastError, long retryAfterMs) {
        PushNotificationRequest retryRequest = request.toBuilder()
                .deviceTokens(tokens)
                .facilityId(null)
                .recipientRoles(null)
                .userIds(null)
                .build();
        List<String> topics = properties.retryTopics();
        if (attempt >= topics.size()) {
            RetryEnvelope envelope = RetryEnvelope.builder().request(retryRequest).attempt(attempt)
                    .notBefore(System.currentTimeMillis()).lastError(lastError).build();
            producer.push(request.getTenantId(), properties.getDeadLetterTopic(), request.getEventId(), envelope);
            metrics.deadLettered(request.getTenantId(), lastError, tokens.size());
            log.error("PUSH_DEADLETTER eventId={} tenantId={} tokens={} attempts={} lastError={}",
                    request.getEventId(), request.getTenantId(), tokens.size(), attempt, lastError);
            return;
        }
        long delay = Math.min(Math.max(properties.getRetryDelaysMs().get(attempt), retryAfterMs), MAX_DELAY_MS);
        RetryEnvelope envelope = RetryEnvelope.builder().request(retryRequest).attempt(attempt + 1)
                .notBefore(System.currentTimeMillis() + delay).lastError(lastError).build();
        producer.push(request.getTenantId(), topics.get(attempt), request.getEventId(), envelope);
        metrics.retried(request.getTenantId(), attempt + 1, tokens.size());
        log.warn("PUSH_RETRY_SCHEDULED eventId={} tenantId={} tokens={} attempt={} delayMs={} lastError={}",
                request.getEventId(), request.getTenantId(), tokens.size(), attempt + 1, delay, lastError);
    }

}
