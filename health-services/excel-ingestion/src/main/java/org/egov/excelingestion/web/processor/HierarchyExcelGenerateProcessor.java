package org.egov.excelingestion.web.processor;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.service.ExcelGenerationService;
import org.egov.excelingestion.web.models.GeneratedResource;
import org.egov.excelingestion.web.models.GeneratedResourceRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component("hierarchyExcelGenerateProcessor")
@Slf4j
public class HierarchyExcelGenerateProcessor implements IGenerateProcessor {

    private final ExcelGenerationService excelGenerationService;

    public HierarchyExcelGenerateProcessor(ExcelGenerationService excelGenerationService) {
        this.excelGenerationService = excelGenerationService;
    }

    @Override
    public GeneratedResource process(GeneratedResourceRequest request) {
        log.info("Processing hierarchy excel generation for type: {}", request.getGeneratedResource().getType());
        try {
            String fileStoreId = excelGenerationService.generateExcelAndUpload(request.getGeneratedResource(), request.getRequestInfo());
            request.getGeneratedResource().setFileStoreId(fileStoreId);
        } catch (IOException e) {
            log.error("Error generating and uploading Excel file: {}", e.getMessage());
            throw new RuntimeException("Error generating and uploading Excel file", e);
        }
        return request.getGeneratedResource();
    }

    @Override
    public String getType() {
        return "hierarchyExcel";
    }
}
