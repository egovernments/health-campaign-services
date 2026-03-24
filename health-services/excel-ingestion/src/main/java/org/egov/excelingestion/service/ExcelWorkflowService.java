package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Service that orchestrates the complete Excel generation workflow
 */
@Service
@Slf4j
public class ExcelWorkflowService {

    private final ExcelGenerationService excelGenerationService;
    private final FileStoreService fileStoreService;
    private final CustomExceptionHandler exceptionHandler;

    public ExcelWorkflowService(ExcelGenerationService excelGenerationService,
                               FileStoreService fileStoreService,
                               CustomExceptionHandler exceptionHandler) {
        this.excelGenerationService = excelGenerationService;
        this.fileStoreService = fileStoreService;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Generate Excel and upload to file store
     */
    public GenerateResource generateAndUploadExcel(GenerateResourceRequest request) throws IOException {
        GenerateResource generateResource = request.getGenerateResource();
        log.info("Starting Excel workflow for type: {}", generateResource.getType());

        // Note: ID and status are already set by GenerationService and AsyncGenerationService
        log.info("Processing Excel generation for ID: {}, Status: {}", generateResource.getId(), generateResource.getStatus());

        byte[] excelBytes;
        try {
            // Generate Excel using the clean type->config->generation flow
            excelBytes = excelGenerationService.generateExcel(generateResource, request.getRequestInfo());
            
        } catch (Exception e) {
            log.error("Error generating Excel for type: {}, ID: {}", generateResource.getType(), generateResource.getId(), e);
            generateResource.setStatus(ProcessingConstants.STATUS_FAILED);
            
            // Check if it's already a CustomException with specific error code, or find root CustomException
            org.egov.tracer.model.CustomException customException = findRootCustomException(e);
            if (customException != null) {
                throw customException;
            }
            
            // Otherwise, treat as internal server error
            exceptionHandler.throwCustomException(ErrorConstants.INTERNAL_SERVER_ERROR,
                    ErrorConstants.INTERNAL_SERVER_ERROR_MESSAGE, e);
            return null; // This will never be reached due to exception throwing above
        }

        try {
            // Upload the generated Excel bytes to file store
            String fileStoreId = fileStoreService.uploadFile(
                    excelBytes,
                    generateResource.getTenantId(),
                    generateResource.getHierarchyType() + ".xlsx");

            generateResource.setFileStoreId(fileStoreId);
            generateResource.setStatus(ProcessingConstants.STATUS_GENERATED);

            log.info("Excel workflow completed successfully for type: {}, ID: {}, fileStoreId: {}",
                    generateResource.getType(), generateResource.getId(), fileStoreId);

        } catch (Exception e) {
            log.error("Error uploading Excel file to file store for type: {}, ID: {}", generateResource.getType(), generateResource.getId(), e);
            generateResource.setStatus(ProcessingConstants.STATUS_FAILED);
            
            // Check if it's already a CustomException with specific error code, or find root CustomException
            org.egov.tracer.model.CustomException customException = findRootCustomException(e);
            if (customException != null) {
                throw customException;
            }
            
            // Otherwise, treat as internal server error
            exceptionHandler.throwCustomException(ErrorConstants.INTERNAL_SERVER_ERROR,
                    ErrorConstants.INTERNAL_SERVER_ERROR_MESSAGE, e);
            return null; // This will never be reached due to exception throwing above
        }

        return generateResource;
    }
    
    private org.egov.tracer.model.CustomException findRootCustomException(Exception exception) {
        if (exception == null) {
            return null;
        }
        
        // If it's already a CustomException, return it
        if (exception instanceof org.egov.tracer.model.CustomException) {
            return (org.egov.tracer.model.CustomException) exception;
        }
        
        // Check if the cause is a CustomException
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof org.egov.tracer.model.CustomException) {
                return (org.egov.tracer.model.CustomException) cause;
            }
            cause = cause.getCause();
        }
        
        // No CustomException found in the exception chain
        return null;
    }
}