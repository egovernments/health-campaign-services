package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.tracer.model.CustomException;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.processor.IGenerateProcessor;
import org.egov.excelingestion.web.processor.GenerateProcessorFactory;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
@Slf4j
public class ExcelGenerationService {

    private final GenerateProcessorFactory processorFactory;
    private final FileStoreService fileStoreService;

    public ExcelGenerationService(GenerateProcessorFactory processorFactory, FileStoreService fileStoreService) {
        this.processorFactory = processorFactory;
        this.fileStoreService = fileStoreService;
    }

    public GenerateResource generateAndUploadExcel(GenerateResourceRequest request) throws IOException {
        log.info("Generating and uploading Excel for type: {}", request.getGenerateResource().getType());

        IGenerateProcessor processor = processorFactory.getProcessor(request.getGenerateResource().getType());

        // Process resource first
        GenerateResource generateResource = processor.process(request);

        byte[] excelBytes;
        try {
            excelBytes = processor.generateExcel(generateResource, request.getRequestInfo());
        } catch (IOException e) {
            log.error("Error generating Excel bytes", e);
            throw new CustomException(ErrorConstants.EXCEL_GENERATION_ERROR, 
                    ErrorConstants.EXCEL_GENERATION_ERROR_MESSAGE);
        }

        // Upload the generated Excel bytes to file store
        String fileStoreId = fileStoreService.uploadFile(
                excelBytes,
                generateResource.getTenantId(),
                generateResource.getHierarchyType() + ".xlsx");

        generateResource.setFileStoreId(fileStoreId);

        return generateResource;
    }
}
