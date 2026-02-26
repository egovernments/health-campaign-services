package org.egov.healthnotification.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class HealthNotificationProperties {

    @Value("${health.notification.consumer.topic}")
    private String consumerTopic;

    @Value("${health.notification.producer.topic}")
    private String producerTopic;

    @Value("${state.level.tenant.id}")
    private String stateLevelTenantId;
}
