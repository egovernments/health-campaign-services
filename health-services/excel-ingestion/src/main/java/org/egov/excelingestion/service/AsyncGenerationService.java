package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.service.ExcelWorkflowService;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.common.producer.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AsyncGenerationService {

    private final ExcelWorkflowService excelWorkflowService;
    private final Producer producer;
    
    @Value("${excel.ingestion.generation.update.topic}")
    private String updateGenerationTopic;

    public AsyncGenerationService(ExcelWorkflowService excelWorkflowService,
                                Producer producer) {
        this.excelWorkflowService = excelWorkflowService;
        this.producer = producer;
    }

    @Async("taskExecutor")
    public void processGenerationAsync(GenerateResource generateResource) {
        log.info("Starting async generation for id: {}", generateResource.getId());
        
        try {
            // Update status to IN_PROGRESS via Kafka
            generateResource.setStatus(GenerationConstants.STATUS_IN_PROGRESS);
            generateResource.setLastModifiedTime(System.currentTimeMillis());
            producer.push(generateResource.getTenantId(), updateGenerationTopic, generateResource);
            
            // Create the request object for the workflow service
            GenerateResourceRequest request = GenerateResourceRequest.builder()
                .generateResource(generateResource)
                .build();
            
            // Call the actual generation service (this is the heavy operation)
            GenerateResource processedResource = excelWorkflowService.generateAndUploadExcel(request);
            
            // Update with success status and fileStoreId via Kafka
            generateResource.setStatus(GenerationConstants.STATUS_COMPLETED);
            generateResource.setFileStoreId(processedResource.getFileStoreId());
            generateResource.setErrorDetails(null);
            generateResource.setLastModifiedTime(System.currentTimeMillis());
            producer.push(generateResource.getTenantId(), updateGenerationTopic, generateResource);
            
            log.info("Async generation completed successfully for id: {}", generateResource.getId());
            
        } catch (Exception e) {
            log.error("Error during async generation for id: {}", generateResource.getId(), e);
            
            // Update status to FAILED with error details via Kafka
            generateResource.setStatus(GenerationConstants.STATUS_FAILED);
            generateResource.setErrorDetails(e.getMessage());
            generateResource.setFileStoreId(null);
            generateResource.setLastModifiedTime(System.currentTimeMillis());
            producer.push(generateResource.getTenantId(), updateGenerationTopic, generateResource);
        }
    }
}