package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.processor.GenerateProcessorFactory;
import org.egov.excelingestion.web.processor.HierarchyExcelGenerateProcessor;
import org.egov.excelingestion.web.processor.IGenerateProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
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

        // Process resource (if process() does not generate bytes, call another method)
        GenerateResource generateResource = processor.process(request);

        // Suppose your processor has a method to generate bytes:
        byte[] excelBytes;
        if (processor instanceof HierarchyExcelGenerateProcessor) {
            try {
                // You can cast or better have a common interface method to generate bytes
                excelBytes = ((HierarchyExcelGenerateProcessor) processor)
                        .generateExcel(request.getGenerateResource(), request.getRequestInfo());
            } catch (IOException e) {
                log.error("Error generating Excel bytes", e);
                throw new RuntimeException(e);
            }
        } else {
            throw new UnsupportedOperationException("Processor does not support Excel generation");
        }

        // Upload file
        String fileStoreId = fileStoreService.uploadFile(excelBytes, generateResource.getTenantId(),
                generateResource.getHierarchyType() + ".xlsx");

        generateResource.setFileStoreId(fileStoreId);

        return generateResource;
    }
}
