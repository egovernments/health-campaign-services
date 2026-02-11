package org.egov.excelingestion.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Utility class for converting schema JSON to ColumnDef objects
 * Provides reusable schema conversion functionality for all generators and processors
 */
@Component
@Slf4j
public class SchemaColumnDefUtil {

    private final ColumnDefMaker columnDefMaker;
    private final CustomExceptionHandler exceptionHandler;

    public SchemaColumnDefUtil(ColumnDefMaker columnDefMaker, CustomExceptionHandler exceptionHandler) {
        this.columnDefMaker = columnDefMaker;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Converts schema JSON string to a list of ColumnDef objects
     * Processes stringProperties, numberProperties, and enumProperties
     * 
     * @param schemaJson The JSON string containing schema properties
     * @return List of ColumnDef objects sorted by orderNumber
     */
    public List<ColumnDef> convertSchemaToColumnDefs(String schemaJson) {
        List<ColumnDef> columns = new ArrayList<>();
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(schemaJson);
            
            // Process stringProperties
            if (root.has("stringProperties")) {
                for (JsonNode node : root.path("stringProperties")) {
                    columns.add(columnDefMaker.createColumnDefFromJson(node, "string"));
                }
            }
            
            // Process numberProperties
            if (root.has("numberProperties")) {
                for (JsonNode node : root.path("numberProperties")) {
                    columns.add(columnDefMaker.createColumnDefFromJson(node, "number"));
                }
            }
            
            // Process enumProperties
            if (root.has("enumProperties")) {
                for (JsonNode node : root.path("enumProperties")) {
                    columns.add(columnDefMaker.createColumnDefFromJson(node, "enum"));
                }
            }
            
            // Sort by orderNumber
            columns.sort(Comparator.comparingInt(ColumnDef::getOrderNumber));
            
        } catch (Exception e) {
            log.error("Error converting schema JSON to ColumnDefs: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.SCHEMA_CONVERSION_ERROR,
                    ErrorConstants.SCHEMA_CONVERSION_ERROR_MESSAGE, e);
        }
        
        return columns;
    }
}