package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.producer.Producer;
import org.egov.common.exception.InvalidTenantIdException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class GenerationService {

    private final GeneratedFileRepository generatedFileRepository;
    private final Producer producer;
    private final AsyncGenerationService asyncGenerationService;
    private final ExcelGenerationValidationService validationService;
    private final RequestInfoConverter requestInfoConverter;
    
    @Value("${excel.ingestion.generation.save.topic}")
    private String saveGenerationTopic;

    public GenerationService(GeneratedFileRepository generatedFileRepository, 
                           Producer producer,
                           AsyncGenerationService asyncGenerationService,
                           ExcelGenerationValidationService validationService,
                           RequestInfoConverter requestInfoConverter) {
        this.generatedFileRepository = generatedFileRepository;
        this.producer = producer;
        this.asyncGenerationService = asyncGenerationService;
        this.validationService = validationService;
        this.requestInfoConverter = requestInfoConverter;
    }

    public String initiateGeneration(GenerateResourceRequest request) {
        String generationId = UUID.randomUUID().toString();
        
        GenerateResource generateResource = request.getGenerateResource();
        generateResource.setId(generationId);
        generateResource.setStatus(GenerationConstants.STATUS_PENDING);
        generateResource.setCreatedTime(System.currentTimeMillis());
        generateResource.setLastModifiedTime(System.currentTimeMillis());
        
        if (request.getRequestInfo() != null && request.getRequestInfo().getUserInfo() != null) {
            String userUuid = request.getRequestInfo().getUserInfo().getUuid();
            generateResource.setCreatedBy(userUuid);
            generateResource.setLastModifiedBy(userUuid);
        }
        
        // Extract and set locale from RequestInfo
        if (request.getRequestInfo() != null) {
            String locale = requestInfoConverter.extractLocale(request.getRequestInfo());
            generateResource.setLocale(locale);
        }

        try {
            // Perform all validations before starting async process
            log.info("Performing pre-generation validations for id: {}", generationId);
            validationService.validate(generateResource, request.getRequestInfo());
            log.info("Pre-generation validations completed successfully for id: {}", generationId);
            
            // Save initial record to database via Kafka (for central instance support)
            producer.push(generateResource.getTenantId(), saveGenerationTopic, generateResource);
            
            // Start async generation in background thread (now with request info)
            asyncGenerationService.processGenerationAsync(generateResource, request.getRequestInfo());
            
            log.info("Generation initiated with id: {} for tenantId: {}", generationId, generateResource.getTenantId());
            return generationId;
        } catch (org.egov.tracer.model.CustomException e) {
            // If validation fails with a CustomException, re-throw it directly
            log.error("Validation failed for generation id: {} - Error: {}", generationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error initiating generation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initiate generation", e);
        }
    }

    public GenerationSearchResponse searchGenerations(GenerationSearchRequest request) throws InvalidTenantIdException {
        try {
            GenerationSearchCriteria criteria = request.getGenerationSearchCriteria();
            
            // Set default pagination if not provided
            if (criteria.getLimit() == null) {
                criteria.setLimit(50);
            }
            if (criteria.getOffset() == null) {
                criteria.setOffset(0);
            }

            List<GenerateResource> generationDetails = generatedFileRepository.search(criteria);
            Long totalCount = generatedFileRepository.getCount(criteria);

            ResponseInfo responseInfo = ResponseInfo.builder()
                    .apiId(request.getRequestInfo().getApiId())
                    .ver(request.getRequestInfo().getVer())
                    .ts(request.getRequestInfo().getTs())
                    .status("successful")
                    .build();

            return GenerationSearchResponse.builder()
                    .responseInfo(responseInfo)
                    .generationDetails(generationDetails)
                    .totalCount(totalCount.intValue())
                    .build();

        } catch (InvalidTenantIdException e) {
            log.error("Invalid tenant ID in search request: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error searching generations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search generations", e);
        }
    }
}