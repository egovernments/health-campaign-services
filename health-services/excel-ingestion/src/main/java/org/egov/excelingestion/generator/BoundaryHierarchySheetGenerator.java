package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.ColumnDefMaker;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generator for boundary hierarchy sheet - uses ExcelPopulator approach
 */
@Component
@Slf4j
public class BoundaryHierarchySheetGenerator implements IExcelPopulatorSheetGenerator {

    private final BoundaryService boundaryService;
    private final BoundaryUtil boundaryUtil;
    private final MDMSService mdmsService;
    private final CustomExceptionHandler exceptionHandler;
    private final ColumnDefMaker columnDefMaker;

    public BoundaryHierarchySheetGenerator(BoundaryService boundaryService, BoundaryUtil boundaryUtil,
                                          MDMSService mdmsService, CustomExceptionHandler exceptionHandler,
                                          ColumnDefMaker columnDefMaker) {
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
        this.mdmsService = mdmsService;
        this.exceptionHandler = exceptionHandler;
        this.columnDefMaker = columnDefMaker;
    }

    @Override
    public SheetGenerationResult generateSheetData(SheetGenerationConfig config,
                                                 GenerateResource generateResource,
                                                 RequestInfo requestInfo,
                                                 Map<String, String> localizationMap) {
        
        log.info("Generating boundary hierarchy sheet data for hierarchy: {}", generateResource.getHierarchyType());
        
        try {
            String tenantId = generateResource.getTenantId();
            String hierarchyType = generateResource.getHierarchyType();
            
            // Fetch boundary hierarchy data
            BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);
            List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();
            
            // Fetch boundary relationship data
            BoundarySearchResponse boundaryRelationshipData = boundaryService.fetchBoundaryRelationship(tenantId, hierarchyType, requestInfo);
            Map<String, EnrichedBoundary> codeToEnrichedBoundary = boundaryUtil.buildCodeToBoundaryMap(boundaryRelationshipData);
            
            // Get level types
            List<String> levelTypes = new ArrayList<>();
            for (BoundaryHierarchyChild hierarchyRelation : hierarchyRelations) {
                levelTypes.add(hierarchyRelation.getBoundaryType());
            }
            
            // Check if boundaries are configured in additionalDetails
            if (generateResource.getBoundaries() == null || generateResource.getBoundaries().isEmpty()) {
                log.info("No boundaries configured in additionalDetails for boundary hierarchy sheet, returning empty result");
                return SheetGenerationResult.builder()
                        .columnDefs(new ArrayList<>())
                        .data(new ArrayList<>())
                        .build();
            }
            
            // Filter boundaries based on additionalDetails configuration
            List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                    generateResource.getBoundaries(), codeToEnrichedBoundary, levelTypes);
            
            // Extract projectType from additionalDetails if present
            String projectType = null;
            if (generateResource.getAdditionalDetails() != null 
                && generateResource.getAdditionalDetails().containsKey("projectType")) {
                projectType = (String) generateResource.getAdditionalDetails().get("projectType");
            }
            
            // Fetch schema columns if projectType exists
            List<ColumnDef> schemaColumns = new ArrayList<>();
            if (projectType != null && !projectType.isEmpty()) {
                schemaColumns = fetchSchemaColumns(projectType, generateResource.getTenantId(), requestInfo);
            }
            
            // Create column definitions
            List<ColumnDef> boundaryColumns = createBoundaryHierarchyColumnDefs(hierarchyRelations, hierarchyType, schemaColumns);
            
            // Create data
            List<Map<String, Object>> boundaryData = getBoundaryHierarchyDataFromFiltered(
                    filteredBoundaries, hierarchyRelations, hierarchyType, localizationMap, schemaColumns);
            
            return SheetGenerationResult.builder()
                    .columnDefs(boundaryColumns)
                    .data(boundaryData)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating boundary hierarchy sheet data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate boundary hierarchy sheet data", e);
        }
    }
    
    private List<ColumnDef> fetchSchemaColumns(String projectType, String tenantId, RequestInfo requestInfo) {
        List<ColumnDef> columns = new ArrayList<>();
        String schemaName = "target-" + projectType;
        
        try {
            // Fetch schema from MDMS
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", schemaName);
            
            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, tenantId, ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);
            
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                
                if (properties != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    String schemaJson = mapper.writeValueAsString(properties);
                    columns = convertSchemaToColumnDefs(schemaJson);
                    log.info("Successfully fetched {} schema columns for projectType: {}", columns.size(), projectType);
                }
            } else {
                log.warn("No schema found for: {}", schemaName);
            }
        } catch (Exception e) {
            log.error("Error fetching schema for projectType {}: {}", projectType, e.getMessage());
            // Don't throw exception, just return empty columns
        }
        
        return columns;
    }
    
    private List<ColumnDef> convertSchemaToColumnDefs(String schemaJson) {
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
            log.error("Error converting schema JSON to ColumnDefs: {}", e.getMessage());
        }
        
        return columns;
    }
    
    
    private List<ColumnDef> createBoundaryHierarchyColumnDefs(List<BoundaryHierarchyChild> hierarchyRelations, 
                                                             String hierarchyType, 
                                                             List<ColumnDef> schemaColumns) {
        List<ColumnDef> columns = new ArrayList<>();
        
        // Create columns for each hierarchy level using exact project-factory pattern
        // All boundary columns are fully locked (freezeColumn: true)
        for (int i = 0; i < hierarchyRelations.size(); i++) {
            String boundaryType = hierarchyRelations.get(i).getBoundaryType();
            String columnName = (hierarchyType + "_" + boundaryType).toUpperCase();
            
            columns.add(ColumnDef.builder()
                    .name(columnName)
                    .orderNumber(i + 1)
                    .width(50)
                    .colorHex("#93c47d")
                    .freezeColumn(true) // Lock boundary columns completely
                    .build());
        }
        
        // Add hidden boundary code column
        columns.add(ColumnDef.builder()
                .name("HCM_ADMIN_CONSOLE_BOUNDARY_CODE")
                .orderNumber(hierarchyRelations.size() + 1)
                .width(80)
                .hideColumn(true)
                .freezeColumn(true)
                .adjustHeight(true)
                .build());
        
        // Add schema columns after boundary columns
        int currentOrderNumber = hierarchyRelations.size() + 2;
        for (ColumnDef schemaCol : schemaColumns) {
            // Create a new column with adjusted order number
            columns.add(ColumnDef.builder()
                    .name(schemaCol.getName())
                    .type(schemaCol.getType())
                    .description(schemaCol.getDescription())
                    .colorHex(schemaCol.getColorHex())
                    .orderNumber(currentOrderNumber++)
                    .freezeColumnIfFilled(schemaCol.isFreezeColumnIfFilled())
                    .hideColumn(schemaCol.isHideColumn())
                    .required(schemaCol.isRequired())
                    .pattern(schemaCol.getPattern())
                    .minimum(schemaCol.getMinimum())
                    .maximum(schemaCol.getMaximum())
                    .minLength(schemaCol.getMinLength())
                    .maxLength(schemaCol.getMaxLength())
                    .freezeColumn(schemaCol.isFreezeColumn())
                    .adjustHeight(schemaCol.isAdjustHeight())
                    .width(schemaCol.getWidth())
                    .unFreezeColumnTillData(schemaCol.isUnFreezeColumnTillData())
                    .freezeTillData(schemaCol.isFreezeTillData())
                    .enumValues(schemaCol.getEnumValues())
                    .multiSelectDetails(schemaCol.getMultiSelectDetails())
                    .build());
        }
        
        
        return columns;
    }
    
    private List<Map<String, Object>> getBoundaryHierarchyDataFromFiltered(List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                                                          List<BoundaryHierarchyChild> hierarchyRelations,
                                                                          String hierarchyType,
                                                                          Map<String, String> localizationMap,
                                                                          List<ColumnDef> schemaColumns) {
        List<Map<String, Object>> data = new ArrayList<>();
        
        int lastLevelIndex = hierarchyRelations.size() - 1;
        
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> boundaryPath = boundary.getBoundaryPath();
            
            // Only include boundaries that have data at the last level (leaf boundaries)
            if (boundaryPath.size() > lastLevelIndex &&
                boundaryPath.get(lastLevelIndex) != null &&
                !boundaryPath.get(lastLevelIndex).isEmpty()) {
                
                Map<String, Object> row = new HashMap<>();
                
                // Fill columns based on hierarchy levels
                for (int i = 0; i < hierarchyRelations.size(); i++) {
                    String boundaryType = hierarchyRelations.get(i).getBoundaryType();
                    String columnName = (hierarchyType + "_" + boundaryType).toUpperCase();
                    String boundaryCode = i < boundaryPath.size() ? boundaryPath.get(i) : "";
                    String boundaryName = "";
                    
                    if (boundaryCode != null && !boundaryCode.isEmpty()) {
                        boundaryName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                    }
                    
                    row.put(columnName, boundaryName);
                }
                
                // Add the hidden boundary code column
                String lastLevelBoundaryCode = boundaryPath.get(lastLevelIndex);
                row.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", lastLevelBoundaryCode);
                
                // Add schema columns with null/default values (to be filled by users)
                for (ColumnDef schemaCol : schemaColumns) {
                    row.put(schemaCol.getName(), null);
                }
                
                data.add(row);
            }
        }
        
        return data;
    }
}