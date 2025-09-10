package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
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
    
    @Value("${excel.ingestion.processing.save.topic}")
    private String saveProcessingTopic;

    public ProcessingService(ProcessingRepository processingRepository, 
                           Producer producer,
                           AsyncProcessingService asyncProcessingService) {
        this.processingRepository = processingRepository;
        this.producer = producer;
        this.asyncProcessingService = asyncProcessingService;
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
            // Save initial record to database via Kafka (for central instance support)
            producer.push(processResource.getTenantId(), saveProcessingTopic, processResource);
            
            // Start async processing in background thread
            asyncProcessingService.processExcelAsync(processResource, request.getRequestInfo());
            
            log.info("Processing initiated with id: {} for tenantId: {}", processingId, processResource.getTenantId());
            return processingId;
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
}