package org.egov.web.notification.push.config;

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

    @Value("${fcm.service-account-key-json}")
    private String fcmServiceAccountKeyJson;

    @Value("${fcm.batch.size}")
    private Integer fcmBatchSize;

    @Value("${fcm.send.chunk.size:50}")
    private Integer fcmSendChunkSize;

    @Value("${fcm.http.max.connections:100}")
    private Integer fcmHttpMaxConnections;

    @Value("${kafka.topics.notification.push}")
    private String pushNotificationTopic;

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

    @PostConstruct
    public void logConfig() {
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
