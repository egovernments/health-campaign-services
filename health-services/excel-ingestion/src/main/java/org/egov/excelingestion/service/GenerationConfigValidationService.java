package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Service for validating processor generation configurations
 */
@Service
@Slf4j
public class GenerationConfigValidationService {

    private final ApplicationContext applicationContext;
    private final CustomExceptionHandler exceptionHandler;

    public GenerationConfigValidationService(ApplicationContext applicationContext, 
                                           CustomExceptionHandler exceptionHandler) {
        this.applicationContext = applicationContext;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Validate processor generation configuration
     */
    public void validateProcessorConfig(ProcessorGenerationConfig config) {
        log.info("Validating processor generation config for: {}", config.getProcessorType());
        
        // Validate basic config
        validateBasicConfig(config);
        
        // Validate sheets
        validateSheets(config);
        
        // Validate generation classes exist and are accessible
        validateGenerationClasses(config);
        
        log.info("Processor generation config validation completed successfully");
    }
    
    private void validateBasicConfig(ProcessorGenerationConfig config) {
        if (!StringUtils.hasText(config.getProcessorType())) {
            exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                    "Processor type is required", 
                    new IllegalArgumentException("Processor type cannot be null or empty"));
        }
        
        if (config.getSheets() == null || config.getSheets().isEmpty()) {
            exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                    "At least one sheet configuration is required", 
                    new IllegalArgumentException("Sheets list cannot be null or empty"));
        }
        
        if (config.getZoomLevel() != null && (config.getZoomLevel() < 10 || config.getZoomLevel() > 400)) {
            exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                    "Zoom level must be between 10 and 400", 
                    new IllegalArgumentException("Invalid zoom level: " + config.getZoomLevel()));
        }
    }
    
    private void validateSheets(ProcessorGenerationConfig config) {
        Set<String> sheetNameKeys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        
        for (SheetGenerationConfig sheetConfig : config.getSheets()) {
            validateSheetConfig(sheetConfig);
            
            // Check for duplicate sheet name keys
            if (!sheetNameKeys.add(sheetConfig.getSheetNameKey())) {
                exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                        "Duplicate sheet name key: " + sheetConfig.getSheetNameKey(), 
                        new IllegalArgumentException("Sheet name keys must be unique"));
            }
            
            // Check for duplicate orders
            if (!orders.add(sheetConfig.getOrder())) {
                exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                        "Duplicate sheet order: " + sheetConfig.getOrder(), 
                        new IllegalArgumentException("Sheet orders must be unique"));
            }
        }
    }
    
    private void validateSheetConfig(SheetGenerationConfig sheetConfig) {
        if (!StringUtils.hasText(sheetConfig.getSheetNameKey())) {
            exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                    "Sheet name key is required", 
                    new IllegalArgumentException("Sheet name key cannot be null or empty"));
        }
        
        // Validate that either generationClass or schemaName is provided
        if (!StringUtils.hasText(sheetConfig.getGenerationClass()) && 
            !StringUtils.hasText(sheetConfig.getSchemaName())) {
            exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                    "Either generation class or schema name is required for sheet: " + sheetConfig.getSheetNameKey(), 
                    new IllegalArgumentException("Sheet must have either generationClass or schemaName"));
        }
        
        if (sheetConfig.getOrder() < 0) {
            exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                    "Sheet order must be non-negative", 
                    new IllegalArgumentException("Invalid sheet order: " + sheetConfig.getOrder()));
        }
    }
    
    private void validateGenerationClasses(ProcessorGenerationConfig config) {
        for (SheetGenerationConfig sheetConfig : config.getSheets()) {
            // Skip validation for automatic schema-based sheets
            if (shouldUseSchemaBasedGeneration(sheetConfig)) {
                // Validate that SchemaBasedSheetGenerator exists as Spring bean
                try {
                    Class<?> schemaGenClass = Class.forName("org.egov.excelingestion.generator.SchemaBasedSheetGenerator");
                    if (applicationContext.getBeanNamesForType(schemaGenClass).length == 0) {
                        exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                                "SchemaBasedSheetGenerator not registered as Spring bean", 
                                new RuntimeException("SchemaBasedSheetGenerator bean not found"));
                    }
                } catch (ClassNotFoundException e) {
                    exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                            "SchemaBasedSheetGenerator class not found", e);
                }
                continue;
            }
            
            // Validate custom generation classes
            if (StringUtils.hasText(sheetConfig.getGenerationClass())) {
                try {
                    String className = sheetConfig.getGenerationClass();
                    // If no package specified, assume it's in generator package
                    if (!className.contains(".")) {
                        className = "org.egov.excelingestion.generator." + className;
                    }
                    Class<?> clazz = Class.forName(className);
                    
                    // Check if the class is a Spring bean
                    if (applicationContext.getBeanNamesForType(clazz).length == 0) {
                        log.warn("Generation class {} is not registered as a Spring bean", 
                                className);
                    }
                    
                    // Validate that the class implements the correct interface
                    if (sheetConfig.isGenerationClassViaExcelPopulator()) {
                        if (!org.egov.excelingestion.generator.IExcelPopulatorSheetGenerator.class.isAssignableFrom(clazz)) {
                            exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                                    "ExcelPopulator generation class must implement IExcelPopulatorSheetGenerator", 
                                    new IllegalArgumentException("Invalid interface for class: " + className));
                        }
                    } else {
                        if (!org.egov.excelingestion.generator.ISheetGenerator.class.isAssignableFrom(clazz)) {
                            exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                                    "Direct generation class must implement ISheetGenerator", 
                                    new IllegalArgumentException("Invalid interface for class: " + className));
                        }
                    }
                    
                } catch (ClassNotFoundException e) {
                    exceptionHandler.throwCustomException(ErrorConstants.VALIDATION_ERROR,
                            "Generation class not found: " + sheetConfig.getGenerationClass(), e);
                }
            }
        }
    }
    
    /**
     * Check if sheet should use automatic schema-based generation
     */
    private boolean shouldUseSchemaBasedGeneration(SheetGenerationConfig config) {
        return (config.getGenerationClass() == null || config.getGenerationClass().isEmpty()) &&
               (config.getSchemaName() != null && !config.getSchemaName().isEmpty());
    }
}