package org.egov.web.notification.push.consumer;

import java.time.Duration;
import java.util.HashMap;

import org.egov.web.notification.push.consumer.contract.RetryEnvelope;
import org.egov.web.notification.push.service.PushNotificationService;
import org.egov.web.notification.push.service.PushRetryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PushRetryListener {

    static final long MAX_PAUSE_MS = 60_000;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(id = "push-retry-1", groupId = "${spring.kafka.consumer.group-id}-retry", containerFactory = "retryListenerContainerFactory",
            topicPattern = "(${kafka.tenant.id.pattern}){0,1}${kafka.topics.notification.push.retry.1}")
    public void retryTier1(HashMap<String, Object> record, Acknowledgment ack) {
        handle(record, ack);
    }

    @KafkaListener(id = "push-retry-2", groupId = "${spring.kafka.consumer.group-id}-retry", containerFactory = "retryListenerContainerFactory",
            topicPattern = "(${kafka.tenant.id.pattern}){0,1}${kafka.topics.notification.push.retry.2}")
    public void retryTier2(HashMap<String, Object> record, Acknowledgment ack) {
        handle(record, ack);
    }

    @KafkaListener(id = "push-retry-3", groupId = "${spring.kafka.consumer.group-id}-retry", containerFactory = "retryListenerContainerFactory",
            topicPattern = "(${kafka.tenant.id.pattern}){0,1}${kafka.topics.notification.push.retry.3}")
    public void retryTier3(HashMap<String, Object> record, Acknowledgment ack) {
        handle(record, ack);
    }

    void handle(HashMap<String, Object> record, Acknowledgment ack) {
        RetryEnvelope envelope = objectMapper.convertValue(record, RetryEnvelope.class);
        long wait = envelope.getNotBefore() - System.currentTimeMillis();
        if (wait > PushRetryService.MAX_DELAY_MS) {
            log.warn("PUSH_RETRY_NOT_BEFORE_OUT_OF_RANGE eventId={} waitMs={} — processing now", envelope.getRequest().getEventId(), wait);
            wait = 0;
        }
        if (wait > 0) {
            ack.nack(Duration.ofMillis(Math.min(wait, MAX_PAUSE_MS)));
            return;
        }
        log.info("PUSH_RETRY eventId={} tenantId={} attempt={} tokens={} lastError={}",
                envelope.getRequest().getEventId(), envelope.getRequest().getTenantId(), envelope.getAttempt(),
                envelope.getRequest().getDeviceTokens() == null ? 0 : envelope.getRequest().getDeviceTokens().size(),
                envelope.getLastError());
        pushNotificationService.sendPushNotification(envelope.getRequest(), envelope.getAttempt());
        ack.acknowledge();
    }

}
