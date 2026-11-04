package org.egov.web.notification.push.config;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Component
@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class PushProperties {

    @Value("${state.level.tenant.id}")
    private String stateLevelTenantId;

    @Value("${fcm.enabled}")
    private Boolean fcmEnabled;

    @Value("${fcm.service-account-key-json:}")
    private String fcmServiceAccountKeyJson;

    @Value("${fcm.tenant-project-map-json:}")
    private String fcmTenantProjectMapJson;

    @Value("${fcm.send.chunk.size:50}")
    private Integer fcmSendChunkSize;

    @Value("${fcm.http.max.connections:100}")
    private Integer fcmHttpMaxConnections;

    @Value("${kafka.topics.notification.push}")
    private String pushNotificationTopic;

    @Value("${kafka.topics.notification.push.retry.1}")
    private String retryTopic1;

    @Value("${kafka.topics.notification.push.retry.2}")
    private String retryTopic2;

    @Value("${kafka.topics.notification.push.retry.3}")
    private String retryTopic3;

    @Value("${kafka.topics.notification.push.deadletter}")
    private String deadLetterTopic;

    @Value("#{'${push.retry.delays.ms}'.split(',')}")
    private List<Long> retryDelaysMs;

    @Value("${push.send.deadline.ms:30000}")
    private Long sendDeadlineMs;

    @Value("${push.kafka.error.retry.attempts:3}")
    private Long kafkaErrorRetryAttempts;

    @Value("${push.kafka.error.retry.interval.ms:2000}")
    private Long kafkaErrorRetryIntervalMs;

    @Value("${push.ttl.default.seconds:21600}")
    private Long defaultTtlSeconds;

    @Value("${push.ttl-by-type-json:{}}")
    private String ttlByTypeJson;

    private Map<String, Long> ttlByType = Map.of();

    @Value("${kafka.topics.persister.save.device.token}")
    private String saveDeviceTokenTopic;

    @Value("${kafka.topics.persister.delete.device.token}")
    private String deleteDeviceTokenTopic;

    @Value("${kafka.topics.persister.unregister.device.token}")
    private String unregisterDeviceTokenTopic;

    @Value("${state.level.tenantid.length:1}")
    private int tenantIdLength;

    @Value("${state.schema.index.position.tenantid:0}")
    private int schemaIndexPosition;

    @Value("${is.environment.central.instance:false}")
    private Boolean isCentralInstance;

    public long ttlSeconds(String type) {
        return type == null ? defaultTtlSeconds : ttlByType.getOrDefault(type, defaultTtlSeconds);
    }

    public List<String> retryTopics() {
        return List.of(retryTopic1, retryTopic2, retryTopic3);
    }

    public String statePrefix(String topic) {
        for (String base : List.of(pushNotificationTopic, retryTopic1, retryTopic2, retryTopic3)) {
            if (topic != null && topic.endsWith(base)) {
                return topic.substring(0, topic.length() - base.length());
            }
        }
        return "";
    }

    public void parseTtlByType() {
        try {
            ttlByType = new ObjectMapper().readValue(ttlByTypeJson == null || ttlByTypeJson.isBlank() ? "{}" : ttlByTypeJson,
                    new TypeReference<Map<String, Long>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("PUSH_TTL_BY_TYPE_JSON must be a JSON object like {\"STOCK_ISSUE\": 21600} (seconds)");
        }
    }

    @PostConstruct
    public void logConfig() {
        parseTtlByType();
        log.info("=== PushProperties Configuration ===");
        log.info("  isCentralInstance     = {}", isCentralInstance);
        log.info("  stateLevelTenantId    = {}", stateLevelTenantId);
        log.info("  tenantIdLength        = {}", tenantIdLength);
        log.info("  schemaIndexPosition   = {}", schemaIndexPosition);
        log.info("  fcmEnabled            = {}", fcmEnabled);
        log.info("  pushNotificationTopic = {}", pushNotificationTopic);
        log.info("  ENV IS_ENVIRONMENT_CENTRAL_INSTANCE = {}",
                System.getenv("IS_ENVIRONMENT_CENTRAL_INSTANCE"));
        log.info("====================================");
    }

}
