package org.egov.web.notification.push.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.repository.DeviceTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TokenCleanupJob {

    @Autowired
    private DeviceTokenRepository repository;

    @Autowired
    private PushProperties properties;

    @Value("${push.token.cleanup.retention.days:30}")
    private long retentionDays;

    @Value("${push.token.cleanup.schemas:}")
    private String schemas;

    @Scheduled(cron = "${push.token.cleanup.cron:0 30 2 * * *}")
    public void purgeSoftDeletedTokens() {
        long cutoff = System.currentTimeMillis() - Duration.ofDays(retentionDays).toMillis();
        for (String schema : targetSchemas()) {
            try {
                int purged = repository.purgeSoftDeleted(schema, cutoff);
                log.info("PUSH_TOKEN_CLEANUP schema={} purged={} retentionDays={}", schema, purged, retentionDays);
            } catch (Exception e) {
                log.error("PUSH_TOKEN_CLEANUP_FAIL schema={}: {}", schema, e.getMessage(), e);
            }
        }
    }

    List<String> targetSchemas() {
        if (!Boolean.TRUE.equals(properties.getIsCentralInstance())) {
            return Collections.singletonList(null);
        }
        List<String> list = Arrays.stream(schemas.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (list.isEmpty()) {
            log.warn("PUSH_TOKEN_CLEANUP skipped: central instance needs push.token.cleanup.schemas");
        }
        return list;
    }

}
