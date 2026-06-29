package org.egov.campaign.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
@Slf4j
@RequiredArgsConstructor
public class CampaignCommandConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topicPattern = ".*cms\\.campaign\\.command\\.create",
            containerFactory = "sagaCoordinatorContainerFactory",
            groupId = "cms-campaign-saga-coordinator"
    )
    public void onCampaignCreate(ConsumerRecord<String, Object> record) {
        try {
            log.info("Received campaign create command on topic: {}", record.topic());
            // TODO Phase 2: sagaOrchestrator.start(CampaignCreationSaga.class, payload)
        } catch (Exception e) {
            log.error("Error processing campaign create command: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topicPattern = ".*cms\\.campaign\\.command\\.draft",
            containerFactory = "sagaCoordinatorContainerFactory",
            groupId = "cms-campaign-saga-coordinator"
    )
    public void onCampaignDraft(ConsumerRecord<String, Object> record) {
        try {
            log.info("Received campaign draft command on topic: {}", record.topic());
            // TODO Phase 1: campaignApplicationService.processDraft(payload)
        } catch (Exception e) {
            log.error("Error processing campaign draft command: {}", e.getMessage(), e);
        }
    }
}
