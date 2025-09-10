package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ProcessResourceRequest;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.common.producer.Producer;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AsyncProcessingService {

    private final ExcelProcessingService excelProcessingService;
    private final Producer producer;
    
    @Value("${excel.ingestion.processing.update.topic}")
    private String updateProcessingTopic;

    public AsyncProcessingService(ExcelProcessingService excelProcessingService,
                                Producer producer) {
        this.excelProcessingService = excelProcessingService;
        this.producer = producer;
    }

    @Async("taskExecutor")
    public void processExcelAsync(ProcessResource processResource, RequestInfo requestInfo) {
        log.info("Starting async processing for id: {}", processResource.getId());
        
        try {
            // Create the request object for the processing service
            ProcessResourceRequest request = ProcessResourceRequest.builder()
                .resourceDetails(processResource)
                .requestInfo(requestInfo)
                .build();
            
            // Call the actual processing service (this is the heavy operation)
            ProcessResource processedResource = excelProcessingService.processExcelFile(request);
            
            // Update with success status and processed file store ID via Kafka
            processResource.setStatus(ProcessingConstants.STATUS_COMPLETED);
            processResource.setProcessedFileStoreId(processedResource.getProcessedFileStoreId());
            processResource.setAdditionalDetails(processedResource.getAdditionalDetails());
            
            // Update audit details
            if (processResource.getAuditDetails() != null) {
                processResource.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());
                if (requestInfo != null && requestInfo.getUserInfo() != null) {
                    processResource.getAuditDetails().setLastModifiedBy(requestInfo.getUserInfo().getUuid());
                }
            }
            
            log.info("Pushing COMPLETED update to Kafka - ID: {}, Status: {}", 
                    processResource.getId(), processResource.getStatus());
            producer.push(processResource.getTenantId(), updateProcessingTopic, processResource);
            
            log.info("Async processing completed successfully for id: {}", processResource.getId());
            
        } catch (Exception e) {
            log.error("Error during async processing for id: {}", processResource.getId(), e);
            
            // Extract error code from exception
            String errorCode = extractErrorCode(e);
            
            // Update status to FAILED with error code via Kafka
            processResource.setStatus(ProcessingConstants.STATUS_FAILED);
            processResource.setProcessedFileStoreId(null);
            
            // Add error details to additional details
            if (processResource.getAdditionalDetails() == null) {
                processResource.setAdditionalDetails(new java.util.HashMap<>());
            }
            processResource.getAdditionalDetails().put("errorCode", errorCode);
            processResource.getAdditionalDetails().put("errorMessage", e.getMessage());
            
            // Update audit details
            if (processResource.getAuditDetails() != null) {
                processResource.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());
                if (requestInfo != null && requestInfo.getUserInfo() != null) {
                    processResource.getAuditDetails().setLastModifiedBy(requestInfo.getUserInfo().getUuid());
                }
            }
            
            log.info("Pushing FAILED update to Kafka - ID: {}, Status: {}", 
                    processResource.getId(), processResource.getStatus());
            producer.push(processResource.getTenantId(), updateProcessingTopic, processResource);
        }
    }

    private String extractErrorCode(Exception exception) {
        if (exception == null) {
            return "PROCESSING_FAILED";
        }
        
        // Find the root CustomException in the exception chain
        CustomException customException = findRootCustomException(exception);
        if (customException != null) {
            return customException.getCode() != null ? customException.getCode() : "PROCESSING_FAILED";
        }
        
        // For other exceptions, return a generic error code
        return "PROCESSING_FAILED";
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