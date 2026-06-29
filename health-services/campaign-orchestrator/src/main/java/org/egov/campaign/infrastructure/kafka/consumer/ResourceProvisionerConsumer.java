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
public class ResourceProvisionerConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topicPattern = ".*cms\\.resource\\.command\\.project\\.create",
            containerFactory = "projectProvisionerContainerFactory",
            groupId = "cms-project-provisioner"
    )
    public void onProjectCreateChunk(ConsumerRecord<String, Object> record) {
        try {
            log.info("Received project provisioning chunk on topic: {}", record.topic());
            // TODO Phase 3: projectCreationSaga.executeCreateChunk(payload)
        } catch (Exception e) {
            log.error("Error processing project provisioning chunk: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topicPattern = ".*cms\\.resource\\.command\\.user\\.create",
            containerFactory = "userProvisionerContainerFactory",
            groupId = "cms-user-provisioner"
    )
    public void onUserCreateChunk(ConsumerRecord<String, Object> record) {
        try {
            log.info("Received user provisioning chunk on topic: {}", record.topic());
            // TODO Phase 4: userCreationSaga.executeCreateChunk(payload)
        } catch (Exception e) {
            log.error("Error processing user provisioning chunk: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topicPattern = ".*cms\\.resource\\.command\\.facility\\.create",
            containerFactory = "facilityProvisionerContainerFactory",
            groupId = "cms-facility-provisioner"
    )
    public void onFacilityCreateChunk(ConsumerRecord<String, Object> record) {
        try {
            log.info("Received facility provisioning chunk on topic: {}", record.topic());
            // TODO Phase 5: facilitySaga.executeCreateChunk(payload)
        } catch (Exception e) {
            log.error("Error processing facility provisioning chunk: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topicPattern = ".*cms\\.resource\\.command\\.mapping\\.apply",
            containerFactory = "mappingReconcilerContainerFactory",
            groupId = "cms-mapping-reconciler"
    )
    public void onMappingApplyChunk(ConsumerRecord<String, Object> record) {
        try {
            log.info("Received mapping reconciliation chunk on topic: {}", record.topic());
            // TODO Phase 6: mappingReconcilerSaga.executeApplyChunk(payload)
        } catch (Exception e) {
            log.error("Error processing mapping reconciliation chunk: {}", e.getMessage(), e);
        }
    }
}
