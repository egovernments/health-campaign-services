package org.egov.campaign.infrastructure.kafka.producer;

import org.egov.common.producer.Producer;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Campaign-specific Kafka producer.
 *
 * WHY this pattern (identical to HealthNotificationProducer):
 *   - health-services-common ships a base Producer bean auto-configured by Spring.
 *   - We must extend it and mark @Primary so Spring resolves our bean when Producer is injected.
 *   - We use @Component with a named qualifier to allow explicit injection if ever needed.
 *
 * HOW to use:
 *   // Central instance — include tenantId so topic gets prefixed
 *   producer.push(tenantId, config.getCampaignCommandCreate(), payload);
 *   // → resolves to "ng-cms.campaign.command.create" when central instance is ON
 *   // → resolves to "cms.campaign.command.create" when central instance is OFF
 *
 *   // Non-tenant-scoped messages (e.g. DLQ, admin events) — no tenantId
 *   producer.push(config.getDlqTopic(), payload);
 *
 * NEVER use plain KafkaTemplate directly — always go through this Producer.
 * CustomKafkaTemplate adds distributed tracing headers to every message.
 */
@Component("campaignEventProducer")
@Primary
public class CampaignEventProducer extends Producer {

    public CampaignEventProducer(CustomKafkaTemplate<String, Object> kafkaTemplate,
                                  MultiStateInstanceUtil multiStateInstanceUtil) {
        super(kafkaTemplate, multiStateInstanceUtil);
    }
}
