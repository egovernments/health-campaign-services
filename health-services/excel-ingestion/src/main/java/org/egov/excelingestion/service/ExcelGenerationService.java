package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.web.models.GeneratedResource;
import org.egov.excelingestion.web.models.GeneratedResourceRequest;
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

    public GeneratedResource generateAndUploadExcel(GeneratedResourceRequest request) throws IOException {
        log.info("Generating and uploading Excel for type: {}", request.getGeneratedResource().getType());

        IGenerateProcessor processor = processorFactory.getProcessor(request.getGeneratedResource().getType());

        // Process resource (if process() does not generate bytes, call another method)
        GeneratedResource generatedResource = processor.process(request);

        // Suppose your processor has a method to generate bytes:
        byte[] excelBytes;
        if (processor instanceof HierarchyExcelGenerateProcessor) {
            try {
                // You can cast or better have a common interface method to generate bytes
                excelBytes = ((HierarchyExcelGenerateProcessor) processor)
                        .generateExcel(request.getGeneratedResource(), request.getRequestInfo());
            } catch (IOException e) {
                log.error("Error generating Excel bytes", e);
                throw new RuntimeException(e);
            }
        } else {
            throw new UnsupportedOperationException("Processor does not support Excel generation");
        }

        // Upload file
        String fileStoreId = fileStoreService.uploadFile(excelBytes, generatedResource.getTenantId(),
                generatedResource.getHierarchyType() + ".xlsx");

        generatedResource.setFileStoreId(fileStoreId);

        return generatedResource;
    }
}
