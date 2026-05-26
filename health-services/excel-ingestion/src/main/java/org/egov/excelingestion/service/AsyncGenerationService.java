package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.producer.Producer;
import org.springframework.stereotype.Service;

/**
 * Performs the actual generation work. Invoked synchronously from the
 * {@code GenerationInitConsumer} (which already enforces single-record polling
 * + manual acknowledgement, so we don't need {@code @Async} here).
 *
 * Transitions emitted:
 *   QUEUED  -> IN_PROGRESS  (just before the heavy work starts)
 *   IN_PROGRESS -> COMPLETED (on success)
 *   IN_PROGRESS -> FAILED    (on any throwable)
 */
@Service
@Slf4j
public class AsyncGenerationService {

    private final ExcelWorkflowService excelWorkflowService;
    private final Producer producer;
    private final KafkaTopicConfig kafkaTopicConfig;
    private final EnrichmentUtil enrichmentUtil;

    public AsyncGenerationService(ExcelWorkflowService excelWorkflowService,
                                  Producer producer,
                                  KafkaTopicConfig kafkaTopicConfig,
                                  EnrichmentUtil enrichmentUtil) {
        this.excelWorkflowService = excelWorkflowService;
        this.producer = producer;
        this.kafkaTopicConfig = kafkaTopicConfig;
        this.enrichmentUtil = enrichmentUtil;
    }

    public void processGeneration(GenerateResource generateResource, RequestInfo requestInfo) {
        log.info("Starting generation for id: {}", generateResource.getId());

        markInProgress(generateResource, requestInfo);

        try {
            GenerateResourceRequest request = GenerateResourceRequest.builder()
                    .generateResource(generateResource)
                    .requestInfo(requestInfo)
                    .build();

            GenerateResource processedResource = excelWorkflowService.generateAndUploadExcel(request);

            generateResource.setStatus(GenerationConstants.STATUS_COMPLETED);
            generateResource.setFileStoreId(processedResource.getFileStoreId());

            if (generateResource.getAdditionalDetails() != null) {
                generateResource.getAdditionalDetails().remove("errorCode");
                generateResource.getAdditionalDetails().remove("errorMessage");
            }

            stampAudit(generateResource, requestInfo);
            publishUpdate(generateResource);
            log.info("Generation completed successfully for id: {}", generateResource.getId());
        } catch (Exception e) {
            log.error("Error during generation for id: {}", generateResource.getId(), e);

            enrichmentUtil.enrichErrorDetailsInAdditionalDetails(generateResource, e);

            generateResource.setStatus(GenerationConstants.STATUS_FAILED);
            generateResource.setFileStoreId(null);

            stampAudit(generateResource, requestInfo);
            publishUpdate(generateResource);
        }
    }

    private void markInProgress(GenerateResource generateResource, RequestInfo requestInfo) {
        generateResource.setStatus(GenerationConstants.STATUS_IN_PROGRESS);
        stampAudit(generateResource, requestInfo);
        publishUpdate(generateResource);
        log.info("Marked generation id={} as IN_PROGRESS", generateResource.getId());
    }

    private void stampAudit(GenerateResource generateResource, RequestInfo requestInfo) {
        long now = System.currentTimeMillis();
        generateResource.setLastModifiedTime(now);
        if (generateResource.getAuditDetails() != null) {
            generateResource.getAuditDetails().setLastModifiedTime(now);
        }
        if (requestInfo != null && requestInfo.getUserInfo() != null && requestInfo.getUserInfo().getUuid() != null) {
            String userUuid = requestInfo.getUserInfo().getUuid();
            generateResource.setLastModifiedBy(userUuid);
            if (generateResource.getAuditDetails() != null) {
                generateResource.getAuditDetails().setLastModifiedBy(userUuid);
            }
        }
    }

    private void publishUpdate(GenerateResource generateResource) {
        log.info("Pushing {} update to Kafka topic: {} - ID: {}",
                generateResource.getStatus(),
                producer.getResolvedTopicName(generateResource.getTenantId(), kafkaTopicConfig.getGenerationUpdateTopic()),
                generateResource.getId());
        producer.push(generateResource.getTenantId(),
                kafkaTopicConfig.getGenerationUpdateTopic(),
                generateResource);
    }
}
