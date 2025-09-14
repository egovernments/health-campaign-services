package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.constants.GenerationConstants;
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

    public AsyncGenerationService(ExcelWorkflowService excelWorkflowService,
                                Producer producer,
                                KafkaTopicConfig kafkaTopicConfig) {
        this.excelWorkflowService = excelWorkflowService;
        this.producer = producer;
        this.kafkaTopicConfig = kafkaTopicConfig;
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
            generateResource.setErrorDetails(null);
            generateResource.setLastModifiedTime(System.currentTimeMillis());
            if (requestInfo != null && requestInfo.getUserInfo() != null) {
                generateResource.setLastModifiedBy(requestInfo.getUserInfo().getUuid());
            }
            log.info("Pushing COMPLETED update to Kafka - ID: {}, Status: {}, LastModifiedBy: {}", 
                    generateResource.getId(), generateResource.getStatus(), generateResource.getLastModifiedBy());
            producer.push(generateResource.getTenantId(), kafkaTopicConfig.getGenerationUpdateTopic(), generateResource);
            
            log.info("Async generation completed successfully for id: {}", generateResource.getId());
            
        } catch (Exception e) {
            log.error("Error during async generation for id: {}", generateResource.getId(), e);
            
            // Extract error code from exception
            String errorCode = extractErrorCode(e);
            
            // Update status to FAILED with error code via Kafka
            generateResource.setStatus(GenerationConstants.STATUS_FAILED);
            generateResource.setErrorDetails(errorCode);
            generateResource.setFileStoreId(null);
            generateResource.setLastModifiedTime(System.currentTimeMillis());
            if (requestInfo != null && requestInfo.getUserInfo() != null) {
                generateResource.setLastModifiedBy(requestInfo.getUserInfo().getUuid());
            }
            log.info("Pushing FAILED update to Kafka - ID: {}, Status: {}, LastModifiedBy: {}", 
                    generateResource.getId(), generateResource.getStatus(), generateResource.getLastModifiedBy());
            producer.push(generateResource.getTenantId(), kafkaTopicConfig.getGenerationUpdateTopic(), generateResource);
        }
    }

    private String extractErrorCode(Exception exception) {
        if (exception == null) {
            return "GENERATION_FAILED";
        }
        
        // Find the root CustomException in the exception chain
        CustomException customException = findRootCustomException(exception);
        if (customException != null) {
            return customException.getCode() != null ? customException.getCode() : "GENERATION_FAILED";
        }
        
        // For other exceptions, return a generic error code
        return "GENERATION_FAILED";
    }
    
    private CustomException findRootCustomException(Exception exception) {
        if (exception == null) {
            return null;
        }
        
        // If it's already a CustomException, return it
        if (exception instanceof CustomException) {
            return (CustomException) exception;
        }
        
        // Check if the cause is a CustomException
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof CustomException) {
                return (CustomException) cause;
            }
            cause = cause.getCause();
        }
        
        // No CustomException found in the exception chain
        return null;
    }
}