package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.generator.IExcelPopulatorSheetGenerator;
import org.egov.excelingestion.generator.ISheetGenerator;
import org.egov.excelingestion.util.BoundaryColumnUtil;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.web.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

@Service
@Slf4j
public class ConfigBasedGenerationService {

    private final ApplicationContext applicationContext;
    private final ExcelDataPopulator excelDataPopulator;
    private final BoundaryColumnUtil boundaryColumnUtil;
    private final CellProtectionManager cellProtectionManager;
    private final ExcelIngestionConfig config;
    private final CustomExceptionHandler exceptionHandler;
    private final GenerationConfigValidationService validationService;

    @Autowired
    public ConfigBasedGenerationService(ApplicationContext applicationContext,
                                      ExcelDataPopulator excelDataPopulator,
                                      BoundaryColumnUtil boundaryColumnUtil,
                                      CellProtectionManager cellProtectionManager,
                                      ExcelIngestionConfig config,
                                      CustomExceptionHandler exceptionHandler,
                                      GenerationConfigValidationService validationService) {
        this.applicationContext = applicationContext;
        this.excelDataPopulator = excelDataPopulator;
        this.boundaryColumnUtil = boundaryColumnUtil;
        this.cellProtectionManager = cellProtectionManager;
        this.config = config;
        this.exceptionHandler = exceptionHandler;
        this.validationService = validationService;
    }

    /**
     * Generate Excel workbook based on processor configuration
     */
    public byte[] generateExcelWithConfig(ProcessorGenerationConfig processorConfig,
                                        GenerateResource generateResource,
                                        RequestInfo requestInfo,
                                        Map<String, String> localizationMap) throws IOException {
        
        log.info("Starting config-based Excel generation for processor: {}", processorConfig.getProcessorType());
        
        // Validate configuration
        validationService.validateProcessorConfig(processorConfig);
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        String firstVisibleSheetName = null;
        
        // Sort sheets by order
        processorConfig.getSheets().sort(Comparator.comparingInt(SheetGenerationConfig::getOrder));
        
        // Generate each sheet
        for (SheetGenerationConfig sheetConfig : processorConfig.getSheets()) {
            String localizedSheetName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            String actualSheetName = truncateSheetName(localizedSheetName);
            
            log.info("Generating sheet: {} (order: {})", actualSheetName, sheetConfig.getOrder());
            
            try {
                if (sheetConfig.isGenerationClassViaExcelPopulator()) {
                    // Use ExcelPopulator approach
                    generateSheetViaExcelPopulator(workbook, actualSheetName, sheetConfig, 
                                                 generateResource, requestInfo, localizationMap);
                } else {
                    // Use direct workbook generation approach
                    generateSheetDirectly(workbook, actualSheetName, sheetConfig, 
                                        generateResource, requestInfo, localizationMap);
                }
                
                // Add boundary columns if configured
                if (sheetConfig.isAddLevelAndBoundaryColumns()) {
                    boundaryColumnUtil.addBoundaryColumnsToSheet(workbook, actualSheetName, localizationMap,
                            generateResource.getBoundaries(), generateResource.getHierarchyType(),
                            generateResource.getTenantId(), requestInfo);
                }
                
                // Track first visible sheet for setting as active
                if (sheetConfig.isVisible() && firstVisibleSheetName == null) {
                    firstVisibleSheetName = actualSheetName;
                }
                
            } catch (Exception e) {
                log.error("Error generating sheet {}: {}", actualSheetName, e.getMessage(), e);
                throw new RuntimeException("Failed to generate sheet: " + actualSheetName, e);
            }
        }
        
        // Apply workbook settings
        applyWorkbookSettings(workbook, processorConfig, firstVisibleSheetName);
        
        // Convert to byte array
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            workbook.write(bos);
        } finally {
            workbook.close();
        }
        
        log.info("Config-based Excel generation completed successfully");
        return bos.toByteArray();
    }
    
    private void generateSheetViaExcelPopulator(XSSFWorkbook workbook, String sheetName,
                                              SheetGenerationConfig config,
                                              GenerateResource generateResource,
                                              RequestInfo requestInfo,
                                              Map<String, String> localizationMap) {
        try {
            // Get the sheet generator bean
            IExcelPopulatorSheetGenerator generator = getExcelPopulatorGenerator(config.getGenerationClass());
            
            // Generate sheet data
            SheetGenerationResult result = generator.generateSheetData(config, generateResource, requestInfo, localizationMap);
            
            // Use ExcelDataPopulator to create the sheet
            excelDataPopulator.populateSheetWithData(workbook, sheetName, 
                                                    result.getColumnDefs(), result.getData(), localizationMap);
            
        } catch (Exception e) {
            log.error("Error in ExcelPopulator sheet generation for {}: {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate sheet via ExcelPopulator: " + sheetName, e);
        }
    }
    
    private void generateSheetDirectly(XSSFWorkbook workbook, String sheetName,
                                     SheetGenerationConfig config,
                                     GenerateResource generateResource,
                                     RequestInfo requestInfo,
                                     Map<String, String> localizationMap) {
        try {
            // Get the sheet generator bean
            ISheetGenerator generator = getDirectSheetGenerator(config.getGenerationClass());
            
            // Generate sheet directly
            workbook = generator.generateSheet(workbook, sheetName, config, generateResource, requestInfo, localizationMap);
            
        } catch (Exception e) {
            log.error("Error in direct sheet generation for {}: {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate sheet directly: " + sheetName, e);
        }
    }
    
    private void applyWorkbookSettings(XSSFWorkbook workbook, ProcessorGenerationConfig processorConfig, String activeSheetName) {
        // Set zoom level
        Integer zoomLevel = processorConfig.getZoomLevel() != null ? processorConfig.getZoomLevel() : config.getExcelSheetZoom();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            sheet.setZoom(zoomLevel);
        }
        
        // Hide non-visible sheets
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            
            // Find the sheet config to check visibility  
            boolean isVisible = processorConfig.getSheets().stream()
                    .anyMatch(sheetConfig -> {
                        String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), new java.util.HashMap<>());
                        String actualName = truncateSheetName(localizedName);
                        return actualName.equals(sheetName) && sheetConfig.isVisible();
                    });
            
            // Hide sheets that start with "_h_" (helper sheets) or are configured as hidden
            if (sheetName.startsWith("_h_") || !isVisible) {
                workbook.setSheetHidden(i, true);
            }
        }
        
        // Set active sheet
        if (activeSheetName != null && workbook.getSheet(activeSheetName) != null) {
            workbook.setActiveSheet(workbook.getSheetIndex(activeSheetName));
        }
        
        // Apply protection if configured
        if (processorConfig.isApplyWorkbookProtection()) {
            String password = processorConfig.getProtectionPassword() != null ? 
                            processorConfig.getProtectionPassword() : config.getExcelSheetPassword();
            
            // Protect visible sheets
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (!workbook.isSheetHidden(i)) {
                    cellProtectionManager.applySheetProtection(workbook, sheet, password);
                }
            }
            
            // Apply workbook-level protection
            cellProtectionManager.applyWorkbookProtection(workbook, password);
            workbook.setWorkbookPassword(password, HashAlgorithm.sha512);
        }
    }
    
    private IExcelPopulatorSheetGenerator getExcelPopulatorGenerator(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (IExcelPopulatorSheetGenerator) applicationContext.getBean(clazz);
        } catch (Exception e) {
            log.error("Error getting ExcelPopulator generator bean for class {}: {}", className, e.getMessage(), e);
            throw new RuntimeException("Failed to get ExcelPopulator generator: " + className, e);
        }
    }
    
    private ISheetGenerator getDirectSheetGenerator(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (ISheetGenerator) applicationContext.getBean(clazz);
        } catch (Exception e) {
            log.error("Error getting direct sheet generator bean for class {}: {}", className, e.getMessage(), e);
            throw new RuntimeException("Failed to get direct sheet generator: " + className, e);
        }
    }
    
    private String getLocalizedSheetName(String sheetNameKey, Map<String, String> localizationMap) {
        if (localizationMap != null) {
            return localizationMap.getOrDefault(sheetNameKey, sheetNameKey);
        }
        return sheetNameKey;
    }
    
    private String truncateSheetName(String sheetName) {
        if (sheetName.length() > 31) {
            String truncated = sheetName.substring(0, 31);
            log.warn("Sheet name '{}' exceeds 31 character limit, trimming to '{}'", sheetName, truncated);
            return truncated;
        }
        return sheetName;
    }
}