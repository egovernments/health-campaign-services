package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ProcessingRepository;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.producer.Producer;
import org.egov.common.exception.InvalidTenantIdException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ProcessingService {

    private final ProcessingRepository processingRepository;
    private final Producer producer;
    private final AsyncProcessingService asyncProcessingService;
    private final ConfigBasedProcessingService configBasedProcessingService;
    private final CustomExceptionHandler exceptionHandler;
    private final KafkaTopicConfig kafkaTopicConfig;

    public ProcessingService(ProcessingRepository processingRepository, 
                           Producer producer,
                           AsyncProcessingService asyncProcessingService,
                           ConfigBasedProcessingService configBasedProcessingService,
                           CustomExceptionHandler exceptionHandler,
                           KafkaTopicConfig kafkaTopicConfig) {
        this.processingRepository = processingRepository;
        this.producer = producer;
        this.asyncProcessingService = asyncProcessingService;
        this.configBasedProcessingService = configBasedProcessingService;
        this.exceptionHandler = exceptionHandler;
        this.kafkaTopicConfig = kafkaTopicConfig;
    }

    public String initiateProcessing(ProcessResourceRequest request) {
        String processingId = UUID.randomUUID().toString();
        
        ProcessResource processResource = request.getResourceDetails();
        processResource.setId(processingId);
        processResource.setStatus(ProcessingConstants.STATUS_PENDING);
        
        // Set audit details
        AuditDetails auditDetails = AuditDetails.builder()
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();
        
        if (request.getRequestInfo() != null && request.getRequestInfo().getUserInfo() != null) {
            String userUuid = request.getRequestInfo().getUserInfo().getUuid();
            auditDetails.setCreatedBy(userUuid);
            auditDetails.setLastModifiedBy(userUuid);
        }
        
        processResource.setAuditDetails(auditDetails);

        try {
            // Validate processor classes exist before starting async processing
            validateProcessorClasses(processResource.getType());
            
            // Save initial record to database via Kafka (for central instance support)
            producer.push(processResource.getTenantId(), kafkaTopicConfig.getProcessingSaveTopic(), processResource);
            
            // Start async processing in background thread
            asyncProcessingService.processExcelAsync(processResource, request.getRequestInfo());
            
            log.info("Processing initiated with id: {} for tenantId: {}", processingId, processResource.getTenantId());
            return processingId;
        } catch (org.egov.tracer.model.CustomException e) {
            log.error("Error initiating processing: {}", e.getMessage(), e);
            // Re-throw CustomExceptions without wrapping
            throw e;
        } catch (Exception e) {
            log.error("Error initiating processing: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initiate processing", e);
        }
    }

    public ProcessingSearchResponse searchProcessing(ProcessingSearchRequest request) throws InvalidTenantIdException {
        try {
            ProcessingSearchCriteria criteria = request.getProcessingSearchCriteria();
            
            // Set default pagination if not provided
            if (criteria.getLimit() == null) {
                criteria.setLimit(50);
            }
            if (criteria.getOffset() == null) {
                criteria.setOffset(0);
            }

            List<ProcessResource> processingDetails = processingRepository.search(criteria);
            Long totalCount = processingRepository.getCount(criteria);

            ResponseInfo responseInfo = ResponseInfo.builder()
                    .apiId(request.getRequestInfo().getApiId())
                    .ver(request.getRequestInfo().getVer())
                    .ts(request.getRequestInfo().getTs())
                    .status("successful")
                    .build();

            return ProcessingSearchResponse.builder()
                    .responseInfo(responseInfo)
                    .processingDetails(processingDetails)
                    .totalCount(totalCount.intValue())
                    .build();

        } catch (InvalidTenantIdException e) {
            log.error("Invalid tenant ID in search request: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error searching processing records: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search processing records", e);
        }
    }
    
    private void validateProcessorClasses(String processorType) {
        try {
            // Get processor configuration
            java.util.List<org.egov.excelingestion.config.ProcessorConfigurationRegistry.ProcessorSheetConfig> config = 
                    configBasedProcessingService.getConfigByType(processorType);
            
            if (config == null) {
                return; // No processor configuration, validation not needed
            }
            
            // Validate each processor class exists
            for (org.egov.excelingestion.config.ProcessorConfigurationRegistry.ProcessorSheetConfig sheetConfig : config) {
                String processorClass = sheetConfig.getProcessorClass();
                if (processorClass != null && !processorClass.trim().isEmpty()) {
                    // If no package specified, assume it's in processor package
                    String fullProcessorClass = processorClass;
                    if (!processorClass.contains(".")) {
                        fullProcessorClass = "org.egov.excelingestion.processor." + processorClass;
                    }
                    
                    // Try to load the class
                    try {
                        Class.forName(fullProcessorClass);
                        log.info("Validated processor class exists: {}", fullProcessorClass);
                    } catch (ClassNotFoundException e) {
                        log.error("Processor class validation failed: {}", fullProcessorClass);
                        exceptionHandler.throwCustomException(
                                ErrorConstants.PROCESSOR_CLASS_NOT_FOUND,
                                ErrorConstants.PROCESSOR_CLASS_NOT_FOUND_MESSAGE.replace("{0}", processorClass),
                                e);
                    }
                }
            }
            
        } catch (org.egov.tracer.model.CustomException e) {
            // Re-throw CustomExceptions without wrapping
            throw e;
        } catch (Exception e) {
            log.error("Error validating processor classes for type {}: {}", processorType, e.getMessage());
            throw new RuntimeException("Failed to validate processor configuration: " + e.getMessage(), e);
        }
    }
}