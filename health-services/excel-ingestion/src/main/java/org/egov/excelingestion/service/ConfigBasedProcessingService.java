package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.mdms.ExcelIngestionProcessData;
import org.egov.excelingestion.web.models.mdms.ProcessSheetData;
import org.egov.excelingestion.web.models.ProcessorSheetConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.processor.IWorkbookProcessor;
import org.egov.excelingestion.processor.ISheetDataProcessor;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.ParsingCompleteEvent;
import org.egov.excelingestion.web.models.SheetDataTemp;
import org.egov.excelingestion.web.models.SheetGenerationResult;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.common.producer.Producer;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for processing validation using ProcessorConfigurationRegistry
 */
@Service
@Slf4j
public class ConfigBasedProcessingService {

    private final MDMSConfigService mdmsConfigService;
    private final CustomExceptionHandler exceptionHandler;
    private final MDMSService mdmsService;
    private final ApplicationContext applicationContext;
    private final Producer producer;
    private final KafkaTopicConfig kafkaTopicConfig;

    public ConfigBasedProcessingService(MDMSConfigService mdmsConfigService,
                                      CustomExceptionHandler exceptionHandler,
                                      MDMSService mdmsService,
                                      ApplicationContext applicationContext,
                                      Producer producer,
                                      KafkaTopicConfig kafkaTopicConfig) {
        this.mdmsConfigService = mdmsConfigService;
        this.exceptionHandler = exceptionHandler;
        this.mdmsService = mdmsService;
        this.applicationContext = applicationContext;
        this.producer = producer;
        this.kafkaTopicConfig = kafkaTopicConfig;
    }

    /**
     * Pre-validates schema configuration and fetches all required schemas
     */
    public Map<String, Map<String, Object>> preValidateAndFetchSchemas(Workbook workbook, 
                                                                      ProcessResource resource,
                                                                      RequestInfo requestInfo, 
                                                                      Map<String, String> localizationMap) {
        log.info("Starting config-based processing validation for type: {}", resource.getType());
        
        Map<String, Map<String, Object>> schemas = new HashMap<>();

        // Step 1: Get processor configuration by type
        java.util.List<ProcessorSheetConfig> config = getConfigByType(resource.getType(), requestInfo, resource.getTenantId());

        // Step 2: Check if all required sheets are present in the workbook
        checkRequiredSheetsPresent(workbook, config, localizationMap);

        // Step 3: Pre-validate all sheets and fetch their schemas
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();

            // Skip hidden sheets
            if (isHiddenSheet(sheetName)) {
                log.info("Skipping hidden sheet: {}", sheetName);
                continue;
            }

            log.info("Pre-validating schema for sheet: {}", sheetName);

            // Get schema name for this sheet from config
            String schemaName = getSchemaNameForSheet(sheetName, config, localizationMap);

            if (schemaName == null) {
                // Check if it's an unknown sheet or just no validation configured
                if (!isSheetConfigured(sheetName, config, localizationMap)) {
                    Set<String> configuredSheets = getConfiguredSheetNames(config, localizationMap);
                    exceptionHandler.throwCustomException(ErrorConstants.SHEET_NOT_CONFIGURED,
                            ErrorConstants.SHEET_NOT_CONFIGURED_MESSAGE
                                    .replace("{0}", sheetName)
                                    .replace("{1}", resource.getType())
                                    .replace("{2}", String.valueOf(configuredSheets)));
                }
                // Sheet is configured but has no schema (no validation required)
                log.info("Sheet '{}' is configured but has no validation schema", sheetName);
                continue;
            }

            // Fetch schema from MDMS if not already fetched
            if (!schemas.containsKey(schemaName)) {
                Map<String, Object> schema = fetchSchemaFromMDMS(resource.getTenantId(), schemaName, requestInfo);

                if (schema == null) {
                    exceptionHandler.throwCustomException(ErrorConstants.SCHEMA_NOT_FOUND_IN_MDMS,
                            ErrorConstants.SCHEMA_NOT_FOUND_IN_MDMS_MESSAGE
                                    .replace("{0}", schemaName)
                                    .replace("{1}", resource.getTenantId()),
                            new RuntimeException("Schema '" + schemaName + "' not found in MDMS"));
                }

                schemas.put(schemaName, schema);
                log.info("Successfully fetched and cached schema: {}", schemaName);
            }
        }

        log.info("Config-based pre-validation completed successfully. Fetched {} schemas", schemas.size());
        return schemas;
    }

    /**
     * Get processor configuration by type with validation
     */
    public java.util.List<ProcessorSheetConfig> getConfigByType(String processorType, RequestInfo requestInfo, String tenantId) {
        ExcelIngestionProcessData processData = mdmsConfigService.getExcelIngestionProcessConfig(requestInfo, tenantId, processorType);
        if (processData == null || processData.getSheets() == null || processData.getSheets().isEmpty()) {
            log.error("Processor type '{}' is not supported for tenant: {}", processorType, tenantId);
            
            exceptionHandler.throwCustomException(
                    ErrorConstants.PROCESSING_TYPE_NOT_SUPPORTED,
                    ErrorConstants.PROCESSING_TYPE_NOT_SUPPORTED_MESSAGE
                            .replace("{0}", processorType)
                            .replace("{1}", "Check MDMS configuration"),
                    new IllegalArgumentException("Unsupported processor type: " + processorType)
            );
        }

        List<ProcessorSheetConfig> configs = new ArrayList<>();
        for (ProcessSheetData sheetData : processData.getSheets()) {
            configs.add(new ProcessorSheetConfig(
                sheetData.getSheetName(), 
                sheetData.getSchemaName(), 
                sheetData.getProcessorClass(), 
                sheetData.getParseEnabled() != null ? sheetData.getParseEnabled() : true
            ));
        }
        
        return configs;
    }

    /**
     * Check if all required sheets are present in the workbook
     */
    private void checkRequiredSheetsPresent(Workbook workbook, java.util.List<ProcessorSheetConfig> config, Map<String, String> localizationMap) {
        // Get all non-hidden sheet names from workbook
        Set<String> workbookSheets = new HashSet<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetAt(i).getSheetName();
            if (!isHiddenSheet(name)) {
                workbookSheets.add(name);
            }
        }
        
        for (ProcessorSheetConfig sheetConfig : config) {
            String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            if (!workbookSheets.contains(localizedName)) {
                Set<String> expectedSheets = getConfiguredSheetNames(config, localizationMap);
                
                exceptionHandler.throwCustomException(ErrorConstants.REQUIRED_SHEET_MISSING,
                        ErrorConstants.REQUIRED_SHEET_MISSING_MESSAGE
                                .replace("{0}", localizedName)
                                .replace("{1}", expectedSheets.toString()),
                        new RuntimeException("Required sheet '" + localizedName + "' missing"));
            }
        }
    }

    /**
     * Get schema name for a sheet from configuration
     */
    private String getSchemaNameForSheet(String actualSheetName, java.util.List<ProcessorSheetConfig> config, Map<String, String> localizationMap) {
        for (ProcessorSheetConfig sheetConfig : config) {
            String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            if (localizedName.equals(actualSheetName)) {
                return sheetConfig.getSchemaName();
            }
        }
        return null;
    }

    /**
     * Check if a sheet is configured
     */
    private boolean isSheetConfigured(String actualSheetName, java.util.List<ProcessorSheetConfig> config, Map<String, String> localizationMap) {
        for (ProcessorSheetConfig sheetConfig : config) {
            String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            if (localizedName.equals(actualSheetName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all configured sheet names
     */
    private Set<String> getConfiguredSheetNames(java.util.List<ProcessorSheetConfig> config, Map<String, String> localizationMap) {
        Set<String> sheetNames = new HashSet<>();
        for (ProcessorSheetConfig sheetConfig : config) {
            String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            sheetNames.add(localizedName);
        }
        return sheetNames;
    }

    /**
     * Check if a sheet is a hidden sheet
     */
    public boolean isHiddenSheet(String sheetName) {
        return sheetName != null && sheetName.startsWith("_h_") && sheetName.endsWith("_h_");
    }

    /**
     * Get localized sheet name with 31-char limit handling
     */
    private String getLocalizedSheetName(String sheetKey, Map<String, String> localizationMap) {
        String localizedName = sheetKey;
        
        if (localizationMap != null && localizationMap.containsKey(sheetKey)) {
            localizedName = localizationMap.get(sheetKey);
        }
        
        // Handle Excel's 31 character limit
        if (localizedName.length() > 31) {
            localizedName = localizedName.substring(0, 31);
        }
        
        return localizedName;
    }

    /**
     * Fetch schema from MDMS
     */
    private Map<String, Object> fetchSchemaFromMDMS(String tenantId, String schemaName, RequestInfo requestInfo) {
        try {
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", schemaName);
            
            java.util.List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, tenantId, ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);
            
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                
                if (data != null) {
                    Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                    if (properties != null) {
                        log.info("Successfully fetched MDMS schema for: {}", schemaName);
                        return properties;
                    }
                }
            }
            
            log.warn("No MDMS data found for schema: {}", schemaName);
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching MDMS schema {}: {}", schemaName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Process workbook using configured processor if available
     * Returns processed workbook with error columns added, or original workbook if no processor configured
     */
    public Workbook processWorkbookWithProcessor(Workbook workbook,
                                                ProcessResource resource,
                                                RequestInfo requestInfo,
                                                Map<String, String> localizationMap) {
        // Get processor configuration
        java.util.List<ProcessorSheetConfig> config = getConfigByType(resource.getType(), requestInfo, resource.getTenantId());
        if (config == null) {
            return workbook;
        }
        
        // Process all sheets that have processors configured
        for (ProcessorSheetConfig sheetConfig : config) {
            String processorClass = sheetConfig.getProcessorClass();
            if (processorClass == null) {
                continue; // No processor configured for this sheet
            }
            
            String configuredSheetName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            
            // Load and execute processor
            try {
                log.info("Using processor {} for sheet {}", processorClass, configuredSheetName);
                
                // Get processor bean from Spring context
                // If no package specified, assume it's in processor package
                String fullProcessorClass = processorClass;
                if (!processorClass.contains(".")) {
                    fullProcessorClass = "org.egov.excelingestion.processor." + processorClass;
                }
                Class<?> clazz = Class.forName(fullProcessorClass);
                Object processorBean = applicationContext.getBean(clazz);
                
                // Check which interface the processor implements
                if (processorBean instanceof IWorkbookProcessor) {
                    IWorkbookProcessor processor = (IWorkbookProcessor) processorBean;
                    workbook = processor.processWorkbook(workbook, configuredSheetName, resource, requestInfo, localizationMap);
                } else if (processorBean instanceof ISheetDataProcessor) {
                    ISheetDataProcessor processor = (ISheetDataProcessor) processorBean;
                    
                    // Convert workbook sheet to data
                    List<Map<String, Object>> sheetData = extractSheetDataFromWorkbook(workbook, configuredSheetName);
                    
                    // Process the data
                    SheetGenerationResult result = processor.processSheetData(sheetData, resource, requestInfo, localizationMap);
                    
                    // Apply the results back to the workbook using ExcelDataPopulator
                    if (result != null) {
                        ExcelDataPopulator populator = applicationContext.getBean(ExcelDataPopulator.class);
                        populator.populateSheetWithData(workbook, configuredSheetName, result.getColumnDefs(), result.getData(), localizationMap);
                    }
                } else {
                    throw new IllegalArgumentException("Processor " + processorClass + " must implement either IWorkbookProcessor or ISheetDataProcessor");
                }
                
            } catch (ClassNotFoundException e) {
                log.error("Processor class not found: {}", processorClass, e);
                exceptionHandler.throwCustomException(ErrorConstants.PROCESSOR_CLASS_NOT_FOUND,
                        ErrorConstants.PROCESSOR_CLASS_NOT_FOUND_MESSAGE.replace("{0}", processorClass));
            } catch (org.egov.tracer.model.CustomException e) {
                // Re-throw CustomExceptions (like schema not found) without wrapping
                log.error("Custom exception from processor {}: {}", processorClass, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("Error executing processor {}: {}", processorClass, e.getMessage(), e);
                exceptionHandler.throwCustomException(ErrorConstants.PROCESSOR_EXECUTION_ERROR,
                        ErrorConstants.PROCESSOR_EXECUTION_ERROR_MESSAGE.replace("{0}", e.getMessage()));
            }
        }
        
        return workbook;
    }

    /**
     * Extract sheet data from workbook for ISheetDataProcessor
     */
    private List<Map<String, Object>> extractSheetDataFromWorkbook(Workbook workbook, String sheetName) {
        List<Map<String, Object>> data = new ArrayList<>();
        
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            log.warn("Sheet not found: {}", sheetName);
            return data;
        }
        
        if (ExcelUtil.findActualLastRowWithData(sheet) < 2) {
            return data; // No data rows
        }
        
        // Get header row (row 0 - technical names)
        org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return data;
        }
        
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.getCell(i);
            headers.add(cell != null ? cell.getStringCellValue().trim() : "");
        }
        
        // Process data rows (starting from row 2, skip row 1 which is localized headers)
        int actualLastRow = ExcelUtil.findActualLastRowWithData(sheet);
        for (int rowIndex = 2; rowIndex <= actualLastRow; rowIndex++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            
            Map<String, Object> rowData = new HashMap<>();
            boolean hasData = false; // Track if row has any non-empty data
            
            for (int colIndex = 0; colIndex < headers.size() && colIndex < row.getLastCellNum(); colIndex++) {
                org.apache.poi.ss.usermodel.Cell cell = row.getCell(colIndex);
                String header = headers.get(colIndex);
                
                if (!header.isEmpty()) {
                    String cellValue = ExcelUtil.getCellValueAsString(cell);
                    rowData.put(header, cellValue);
                    
                    // Check if this cell has actual data (not null, not empty, not just whitespace)
                    if (cellValue != null && !cellValue.trim().isEmpty()) {
                        hasData = true;
                    }
                }
            }
            
            // Only add the row if it contains actual data
            if (hasData) {
                data.add(rowData);
            }
        }
        
        log.debug("Extracted {} rows with actual data from sheet: {}", data.size(), sheetName);
        return data;
    }
    

    /**
     * Handle conditional persistence and event publishing for a sheet
     */
    public void handlePostProcessing(String sheetName,
                                   int recordCount,
                                   ProcessResource resource,
                                   Map<String, String> localizationMap,
                                   java.util.List<Map<String, Object>> parsedData,
                                   RequestInfo requestInfo) {
        java.util.List<ProcessorSheetConfig> config = getConfigByType(resource.getType(), requestInfo, resource.getTenantId());
        if (config == null) {
            return;
        }
        
        ProcessorSheetConfig sheetConfig = getSheetConfig(sheetName, config, localizationMap);
        if (sheetConfig == null) {
            return;
        }
        
        // Handle conditional persistence
        if (sheetConfig.isPersistParsings()) {
            log.info("Persisting {} records for sheet: {}", recordCount, sheetName);
            saveSheetDataToTemp(sheetName, parsedData, resource, localizationMap, requestInfo);
        } else {
            log.info("Skipping persistence for sheet: {} (persistParsings = false)", sheetName);
        }
        
        // Note: Per-sheet event publishing removed - now handled at processing type level
    }
    
    /**
     * Extract createdBy from RequestInfo userInfo
     */
    private String extractCreatedByFromRequestInfo(RequestInfo requestInfo) {
        if (requestInfo != null && requestInfo.getUserInfo() != null && 
            requestInfo.getUserInfo().getUuid() != null && !requestInfo.getUserInfo().getUuid().trim().isEmpty()) {
            return requestInfo.getUserInfo().getUuid();
        }
        
        // Throw custom exception if user info is not available
        exceptionHandler.throwCustomException(ErrorConstants.USER_INFO_NOT_FOUND,
                ErrorConstants.USER_INFO_NOT_FOUND_MESSAGE,
                new RuntimeException("RequestInfo or UserInfo or UUID not provided"));
        return null; // This line will never be reached due to exception above
    }
    
    /**
     * Get sheet configuration by name
     */
    private ProcessorSheetConfig getSheetConfig(String sheetName,
                                              java.util.List<ProcessorSheetConfig> config,
                                              Map<String, String> localizationMap) {
        for (ProcessorSheetConfig sheetConfig : config) {
            String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            if (localizedName.equals(sheetName)) {
                return sheetConfig;
            }
        }
        return null;
    }
    
    /**
     * Check if sheet should be persisted based on configuration
     */
    public boolean shouldPersistSheet(String sheetName,
                                    String processorType,
                                    Map<String, String> localizationMap,
                                    RequestInfo requestInfo,
                                    String tenantId) {
        java.util.List<ProcessorSheetConfig> config = getConfigByType(processorType, requestInfo, tenantId);
        if (config == null) {
            return true; // Default behavior
        }
        
        ProcessorSheetConfig sheetConfig = getSheetConfig(sheetName, config, localizationMap);
        return sheetConfig != null ? sheetConfig.isPersistParsings() : true;
    }
    
    /**
     * Send processing result to configured topic after all sheets are processed
     */
    public void sendProcessingResult(ProcessResource resource, RequestInfo requestInfo) {
        ExcelIngestionProcessData processData = mdmsConfigService.getExcelIngestionProcessConfig(requestInfo, resource.getTenantId(), resource.getType());
        String topic = processData.getProcessingResultTopic(); // Assuming this method exists
        if (topic != null && !topic.trim().isEmpty()) {
            producer.push(resource.getTenantId(), topic, resource);
            log.info("Published processing result to topic: {} for processing type: {}, resource ID: {}", 
                    topic, resource.getType(), resource.getId());
            log.info("Processing result sent to topic for resource: {}", resource.getId());
        } else {
            log.debug("No processing result topic configured for processing type: {}", resource.getType());
        }
    }
    
    /**
     * Save sheet data to temporary table via producer push in chunks of 200 records
     */
    private void saveSheetDataToTemp(String sheetName, 
                                   List<Map<String, Object>> parsedData, 
                                   ProcessResource resource, 
                                   Map<String, String> localizationMap,
                                   RequestInfo requestInfo) {
        if (parsedData == null || parsedData.isEmpty()) {
            log.info("No data to save for sheet: {}", sheetName);
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long deleteTime = currentTime + 86400000L; // 1 day later
        
        final int CHUNK_SIZE = 200;
        int totalRecords = parsedData.size();
        int totalChunks = (int) Math.ceil((double) totalRecords / CHUNK_SIZE);
        
        log.info("Processing {} records in {} chunks for sheet: {}", 
                totalRecords, totalChunks, sheetName);
        
        for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            int startIndex = chunkIndex * CHUNK_SIZE;
            int endIndex = Math.min(startIndex + CHUNK_SIZE, totalRecords);
            
            List<SheetDataTemp> chunkDataList = new ArrayList<>();
            
            for (int i = startIndex; i < endIndex; i++) {
                Map<String, Object> rowData = parsedData.get(i);
                
                // Extract actual row number
                Integer actualRowNumber = (Integer) rowData.get("__actualRowNumber__");
                
                // Remove __actualRowNumber__ from rowData
                rowData.remove("__actualRowNumber__");
                
                SheetDataTemp sheetDataTemp = SheetDataTemp.builder()
                        .referenceId(resource.getReferenceId())
                        .tenantId(resource.getTenantId())
                        .fileStoreId(resource.getFileStoreId())
                        .sheetName(sheetName)
                        .rowNumber(actualRowNumber)
                        .rowJson(rowData)
                        .createdBy(extractCreatedByFromRequestInfo(requestInfo))
                        .createdTime(currentTime)
                        .deleteTime(deleteTime)
                        .build();
                        
                chunkDataList.add(sheetDataTemp);
            }
            
            // Create message payload for persister
            Map<String, Object> message = new HashMap<>();
            message.put("sheetData", chunkDataList);
            
            // Push chunk to save-sheet-data-temp topic
            producer.push(resource.getTenantId(), kafkaTopicConfig.getSheetDataSaveTopic(), message);
            
            log.info("Published chunk {}/{} with {} records to save-sheet-data-temp topic for sheet: {}", 
                    chunkIndex + 1, totalChunks, chunkDataList.size(), sheetName);
        }
        
        log.info("Successfully published all {} records in {} chunks to save-sheet-data-temp topic for sheet: {}", 
                totalRecords, totalChunks, sheetName);
    }
}