package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationResult;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generator for schema-based sheets (facility, user) - uses ExcelPopulator approach
 */
@Component
@Slf4j
public class SchemaBasedSheetGenerator implements IExcelPopulatorSheetGenerator {

    private final MDMSService mdmsService;
    private final CustomExceptionHandler exceptionHandler;

    public SchemaBasedSheetGenerator(MDMSService mdmsService, CustomExceptionHandler exceptionHandler) {
        this.mdmsService = mdmsService;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public SheetGenerationResult generateSheetData(SheetGenerationConfig config,
                                                 GenerateResource generateResource,
                                                 RequestInfo requestInfo,
                                                 Map<String, String> localizationMap) {
        
        log.info("Generating schema-based sheet data for schema: {}", config.getSchemaName());
        
        try {
            // Fetch schema from MDMS
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", config.getSchemaName());
            
            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, generateResource.getTenantId(), "HCM-ADMIN-CONSOLE.schemas", filters, 1, 0);
            
            String schemaJson = extractSchemaFromMDMSResponse(mdmsList, config.getSchemaName());
            
            if (schemaJson != null && !schemaJson.isEmpty()) {
                List<ColumnDef> columns = convertSchemaToColumnDefs(schemaJson);
                
                return SheetGenerationResult.builder()
                        .columnDefs(columns)
                        .data(null) // Headers-only sheet
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Error generating schema-based sheet data for {}: {}", config.getSchemaName(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate schema-based sheet data: " + config.getSchemaName(), e);
        }
        
        return SheetGenerationResult.builder()
                .columnDefs(new ArrayList<>())
                .data(null)
                .build();
    }
    
    private String extractSchemaFromMDMSResponse(List<Map<String, Object>> mdmsList, String title) {
        try {
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                
                Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                if (properties != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    log.info("Successfully extracted MDMS schema for: {}", title);
                    return mapper.writeValueAsString(properties);
                }
            }
            log.warn("No MDMS data found for schema: {}", title);
        } catch (Exception e) {
            log.error("Error extracting MDMS schema {}: {}", title, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR,
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
        }
        
        exceptionHandler.throwCustomException(ErrorConstants.MDMS_DATA_NOT_FOUND,
                ErrorConstants.MDMS_DATA_NOT_FOUND_MESSAGE.replace("{0}", title),
                new RuntimeException("Schema '" + title + "' not found in MDMS configuration"));
        return null;
    }
    
    private List<ColumnDef> convertSchemaToColumnDefs(String schemaJson) {
        List<ColumnDef> columns = new ArrayList<>();
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(schemaJson);
            
            // Process stringProperties
            if (root.has("stringProperties")) {
                for (JsonNode node : root.path("stringProperties")) {
                    columns.add(parseJsonToColumnDef(node, "string"));
                }
            }
            
            // Process numberProperties
            if (root.has("numberProperties")) {
                for (JsonNode node : root.path("numberProperties")) {
                    columns.add(parseJsonToColumnDef(node, "number"));
                }
            }
            
            // Process enumProperties
            if (root.has("enumProperties")) {
                for (JsonNode node : root.path("enumProperties")) {
                    columns.add(parseJsonToColumnDef(node, "enum"));
                }
            }
            
            // Sort by orderNumber
            columns.sort(Comparator.comparingInt(ColumnDef::getOrderNumber));
            
        } catch (Exception e) {
            log.error("Error converting schema JSON to ColumnDefs: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.SCHEMA_CONVERSION_ERROR,
                    "Error converting schema to column definitions", e);
        }
        
        return columns;
    }
    
    private ColumnDef parseJsonToColumnDef(JsonNode node, String type) {
        ColumnDef.ColumnDefBuilder builder = ColumnDef.builder()
                .name(node.path("name").asText())
                .type(type)
                .description(node.path("description").asText())
                .colorHex(node.path("color").asText())
                .orderNumber(node.path("orderNumber").asInt(9999))
                .freezeColumnIfFilled(node.path("freezeColumnIfFilled").asBoolean(false))
                .hideColumn(node.path("hideColumn").asBoolean(false))
                .required(node.path("isRequired").asBoolean(false))
                .width(node.has("width") ? node.path("width").asInt() : null)
                .wrapText(node.path("wrapText").asBoolean(false))
                .prefix(node.path("prefix").asText(null))
                .adjustHeight(node.path("adjustHeight").asBoolean(false))
                .showInProcessed(node.path("showInProcessed").asBoolean(true))
                .freezeColumn(node.path("freezeColumn").asBoolean(false))
                .freezeTillData(node.path("freezeTillData").asBoolean(false))
                .unFreezeColumnTillData(node.path("unFreezeColumnTillData").asBoolean(false));
        
        // Handle enum properties
        if ("enum".equals(type) && node.has("enum")) {
            List<String> enumValues = new ArrayList<>();
            node.path("enum").forEach(enumNode -> enumValues.add(enumNode.asText()));
            builder.enumValues(enumValues);
        }
        
        // Handle multiSelectDetails for string properties
        if (node.has("multiSelectDetails")) {
            JsonNode multiSelectNode = node.path("multiSelectDetails");
            List<String> enumValues = new ArrayList<>();
            multiSelectNode.path("enum").forEach(enumNode -> enumValues.add(enumNode.asText()));
            
            MultiSelectDetails details = MultiSelectDetails.builder()
                    .maxSelections(multiSelectNode.path("maxSelections").asInt(1))
                    .minSelections(multiSelectNode.path("minSelections").asInt(0))
                    .enumValues(enumValues)
                    .build();
            
            builder.multiSelectDetails(details);
        }
        
        return builder.build();
    }
}