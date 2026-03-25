package org.egov.web.notification.push.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Component
@Getter
@Setter
@NoArgsConstructor
public class PushProperties {

    @Value("${state.level.tenant.id}")
    private String stateLevelTenantId;

    @Value("${fcm.enabled}")
    private Boolean fcmEnabled;

    @Value("${fcm.service-account-key-json}")
    private String fcmServiceAccountKeyJson;

    @Value("${fcm.batch.size}")
    private Integer fcmBatchSize;

    @Value("${kafka.topics.notification.push}")
    private String pushNotificationTopic;

    @Value("${kafka.topics.persister.save.device.token}")
    private String saveDeviceTokenTopic;

    @Value("${kafka.topics.persister.delete.device.token}")
    private String deleteDeviceTokenTopic;

    @Value("${state.level.tenantid.length:1}")
    private int tenantIdLength;

    @Value("${state.schema.index.position.tenantid:0}")
    private int schemaIndexPosition;

    @Value("${is.environment.central.instance:false}")
    private Boolean isCentralInstance;

}
