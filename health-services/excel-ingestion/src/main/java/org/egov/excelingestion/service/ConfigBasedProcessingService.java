package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ProcessorConfigurationRegistry;
import org.egov.excelingestion.config.ProcessorConfigurationRegistry.ProcessorSheetConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.processor.IWorkbookProcessor;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.ParsingCompleteEvent;
import org.egov.excelingestion.web.models.SheetDataTemp;
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

    private final ProcessorConfigurationRegistry configRegistry;
    private final CustomExceptionHandler exceptionHandler;
    private final MDMSService mdmsService;
    private final ApplicationContext applicationContext;
    private final Producer producer;

    public ConfigBasedProcessingService(ProcessorConfigurationRegistry configRegistry,
                                      CustomExceptionHandler exceptionHandler,
                                      MDMSService mdmsService,
                                      ApplicationContext applicationContext,
                                      Producer producer) {
        this.configRegistry = configRegistry;
        this.exceptionHandler = exceptionHandler;
        this.mdmsService = mdmsService;
        this.applicationContext = applicationContext;
        this.producer = producer;
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
        java.util.List<ProcessorSheetConfig> config = getConfigByType(resource.getType());

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
    public java.util.List<ProcessorConfigurationRegistry.ProcessorSheetConfig> getConfigByType(String processorType) {
        if (!configRegistry.isProcessorTypeSupported(processorType)) {
            log.error("Processor type '{}' is not supported. Supported types: {}", 
                    processorType, String.join(", ", configRegistry.getSupportedProcessorTypes()));
            
            exceptionHandler.throwCustomException(
                    ErrorConstants.PROCESSING_TYPE_NOT_SUPPORTED,
                    ErrorConstants.PROCESSING_TYPE_NOT_SUPPORTED_MESSAGE
                            .replace("{0}", processorType)
                            .replace("{1}", String.join(", ", configRegistry.getSupportedProcessorTypes())),
                    new IllegalArgumentException("Unsupported processor type: " + processorType)
            );
        }

        return configRegistry.getConfigByType(processorType);
    }

    /**
     * Check if all required sheets are present in the workbook
     */
    private void checkRequiredSheetsPresent(Workbook workbook, java.util.List<ProcessorConfigurationRegistry.ProcessorSheetConfig> config, Map<String, String> localizationMap) {
        // Get all non-hidden sheet names from workbook
        Set<String> workbookSheets = new HashSet<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetAt(i).getSheetName();
            if (!isHiddenSheet(name)) {
                workbookSheets.add(name);
            }
        }
        
        // Check for all configured sheets
        for (ProcessorConfigurationRegistry.ProcessorSheetConfig sheetConfig : config) {
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
    private String getSchemaNameForSheet(String actualSheetName, java.util.List<ProcessorConfigurationRegistry.ProcessorSheetConfig> config, Map<String, String> localizationMap) {
        for (ProcessorConfigurationRegistry.ProcessorSheetConfig sheetConfig : config) {
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
    private boolean isSheetConfigured(String actualSheetName, java.util.List<ProcessorConfigurationRegistry.ProcessorSheetConfig> config, Map<String, String> localizationMap) {
        for (ProcessorConfigurationRegistry.ProcessorSheetConfig sheetConfig : config) {
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
    private Set<String> getConfiguredSheetNames(java.util.List<ProcessorConfigurationRegistry.ProcessorSheetConfig> config, Map<String, String> localizationMap) {
        Set<String> sheetNames = new HashSet<>();
        for (ProcessorConfigurationRegistry.ProcessorSheetConfig sheetConfig : config) {
            String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            sheetNames.add(localizedName);
        }
        return sheetNames;
    }

    /**
     * Check if a sheet is a hidden sheet
     */
    private boolean isHiddenSheet(String sheetName) {
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
    public Workbook processWorkbookWithProcessor(String sheetName,
                                                Workbook workbook,
                                                ProcessResource resource,
                                                RequestInfo requestInfo,
                                                Map<String, String> localizationMap) {
        // Get processor configuration
        java.util.List<ProcessorSheetConfig> config = getConfigByType(resource.getType());
        if (config == null) {
            return null;
        }
        
        // Find processor for this sheet
        String processorClass = null;
        for (ProcessorSheetConfig sheetConfig : config) {
            String configuredSheetName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            if (configuredSheetName.equals(sheetName) && sheetConfig.getProcessorClass() != null) {
                processorClass = sheetConfig.getProcessorClass();
                break;
            }
        }
        
        if (processorClass == null) {
            return null; // No processor configured for this sheet
        }
        
        // Load and execute processor
        try {
            log.info("Using processor {} for sheet {}", processorClass, sheetName);
            
            // Get processor bean from Spring context
            // If no package specified, assume it's in processor package
            String fullProcessorClass = processorClass;
            if (!processorClass.contains(".")) {
                fullProcessorClass = "org.egov.excelingestion.processor." + processorClass;
            }
            Class<?> clazz = Class.forName(fullProcessorClass);
            IWorkbookProcessor processor = (IWorkbookProcessor) applicationContext.getBean(clazz);
            
            // Process the workbook
            return processor.processWorkbook(workbook, sheetName, resource, requestInfo, localizationMap);
            
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
        
        return workbook;
    }

    /**
     * Handle conditional persistence and event publishing for a sheet
     */
    public void handlePostProcessing(String sheetName,
                                   int recordCount,
                                   ProcessResource resource,
                                   Map<String, String> localizationMap,
                                   java.util.List<Map<String, Object>> parsedData) {
        java.util.List<ProcessorSheetConfig> config = getConfigByType(resource.getType());
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
            saveSheetDataToTemp(sheetName, parsedData, resource, localizationMap);
        } else {
            log.info("Skipping persistence for sheet: {} (persistParsings = false)", sheetName);
        }
        
        // Handle event publishing
        String topic = sheetConfig.getTriggerParsingCompleteTopic();
        if (topic != null && !topic.trim().isEmpty()) {
            ParsingCompleteEvent event = ParsingCompleteEvent.builder()
                    .filestoreId(resource.getFileStoreId())
                    .referenceId(resource.getReferenceId())
                    .sheetName(sheetName)
                    .recordCount(recordCount)
                    .build();
            
            producer.push(resource.getTenantId(), topic, event);
            log.info("Published parsing complete event to topic: {} for sheet: {}, records: {}", 
                    topic, sheetName, recordCount);
        }
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
                                    Map<String, String> localizationMap) {
        java.util.List<ProcessorSheetConfig> config = getConfigByType(processorType);
        if (config == null) {
            return true; // Default behavior
        }
        
        ProcessorSheetConfig sheetConfig = getSheetConfig(sheetName, config, localizationMap);
        return sheetConfig != null ? sheetConfig.isPersistParsings() : true;
    }
    
    /**
     * Get parsing complete topic for sheet
     */
    public String getParsingCompleteTopic(String sheetName,
                                        String processorType,
                                        Map<String, String> localizationMap) {
        java.util.List<ProcessorSheetConfig> config = getConfigByType(processorType);
        if (config == null) {
            return null;
        }
        
        ProcessorSheetConfig sheetConfig = getSheetConfig(sheetName, config, localizationMap);
        return sheetConfig != null ? sheetConfig.getTriggerParsingCompleteTopic() : null;
    }
    
    /**
     * Save sheet data to temporary table via producer push
     */
    private void saveSheetDataToTemp(String sheetName, 
                                   List<Map<String, Object>> parsedData, 
                                   ProcessResource resource, 
                                   Map<String, String> localizationMap) {
        if (parsedData == null || parsedData.isEmpty()) {
            log.info("No data to save for sheet: {}", sheetName);
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long deleteTime = currentTime + 86400000L; // 1 day later
        
        List<SheetDataTemp> sheetDataList = new ArrayList<>();
        
        for (int i = 0; i < parsedData.size(); i++) {
            Map<String, Object> rowData = parsedData.get(i);
            
            SheetDataTemp sheetDataTemp = SheetDataTemp.builder()
                    .referenceId(resource.getReferenceId())
                    .fileStoreId(resource.getFileStoreId())
                    .sheetName(sheetName)
                    .rowNumber(i + 1) // 1-based row number
                    .rowJson(rowData)
                    .createdBy("system") // TODO: Get from request context
                    .createdTime(currentTime)
                    .deleteTime(deleteTime)
                    .build();
                    
            sheetDataList.add(sheetDataTemp);
        }
        
        // Create message payload for persister
        Map<String, Object> message = new HashMap<>();
        message.put("sheetData", sheetDataList);
        
        // Push to save-sheet-data-temp topic
        producer.push(resource.getTenantId(), "save-sheet-data-temp", message);
        
        log.info("Published {} sheet data records to save-sheet-data-temp topic for sheet: {}", 
                sheetDataList.size(), sheetName);
    }
}