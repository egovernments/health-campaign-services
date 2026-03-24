package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.common.producer.Producer;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

    @Async("taskExecutor")
    public void processGenerationAsync(GenerateResource generateResource, RequestInfo requestInfo) {
        log.info("Starting async generation for id: {}", generateResource.getId());
        
        try {
            // Note: Status remains PENDING during processing, no intermediate status update needed
            
            // Create the request object for the workflow service with passed RequestInfo
            GenerateResourceRequest request = GenerateResourceRequest.builder()
                .generateResource(generateResource)
                .requestInfo(requestInfo)
                .build();
            
            // Call the actual generation service (this is the heavy operation)
            // Note: Validations have already been done in GenerationService
            GenerateResource processedResource = excelWorkflowService.generateAndUploadExcel(request);
            
            // Update with success status and fileStoreId via Kafka
            generateResource.setStatus(GenerationConstants.STATUS_COMPLETED);
            generateResource.setFileStoreId(processedResource.getFileStoreId());
            
            // Clear any error details from additionalDetails on success
            if (generateResource.getAdditionalDetails() != null) {
                generateResource.getAdditionalDetails().remove("errorCode");
                generateResource.getAdditionalDetails().remove("errorMessage");
            }
            //Update audit details
            if (generateResource.getAuditDetails() != null) {
                generateResource.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());
                if (requestInfo != null && requestInfo.getUserInfo() != null) {
                    generateResource.getAuditDetails().setLastModifiedBy(requestInfo.getUserInfo().getUuid());
                }
            }

            log.info("Pushing COMPLETED update to Kafka - ID: {}, Status: {}, LastModifiedBy: {}",
                    generateResource.getId(), generateResource.getStatus(), generateResource.getAuditDetails().getLastModifiedBy());
            producer.push(generateResource.getTenantId(), kafkaTopicConfig.getGenerationUpdateTopic(), generateResource);
            
            log.info("Async generation completed successfully for id: {}", generateResource.getId());
            
        } catch (Exception e) {
            log.error("Error during async generation for id: {}", generateResource.getId(), e);
            
            // Enrich additionalDetails with error code and error message (standardized approach)
            enrichmentUtil.enrichErrorDetailsInAdditionalDetails(generateResource, e);
            
            // Update status to FAILED via Kafka
            generateResource.setStatus(GenerationConstants.STATUS_FAILED);
            generateResource.setFileStoreId(null);
            generateResource.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());
            if (requestInfo != null && requestInfo.getUserInfo() != null) {
                generateResource.getAuditDetails().setLastModifiedBy(requestInfo.getUserInfo().getUuid());
            }
            log.info("Pushing FAILED update to Kafka - ID: {}, Status: {}, LastModifiedBy: {}", 
                    generateResource.getId(), generateResource.getStatus(), generateResource.getAuditDetails().getLastModifiedBy());
            producer.push(generateResource.getTenantId(), kafkaTopicConfig.getGenerationUpdateTopic(), generateResource);
        }
    }

}