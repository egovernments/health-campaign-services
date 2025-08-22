package org.egov.excelingestion.generator;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.beans.factory.annotation.Autowired;
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

    public BoundaryHierarchySheetGenerator(BoundaryService boundaryService, BoundaryUtil boundaryUtil) {
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
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
            
            // Filter boundaries based on additionalDetails configuration
            List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                    generateResource.getBoundaries(), codeToEnrichedBoundary, levelTypes);
            
            // Create column definitions
            List<ColumnDef> boundaryColumns = createBoundaryHierarchyColumnDefs(hierarchyRelations, hierarchyType);
            
            // Create data
            List<Map<String, Object>> boundaryData = getBoundaryHierarchyDataFromFiltered(
                    filteredBoundaries, hierarchyRelations, hierarchyType, localizationMap);
            
            return SheetGenerationResult.builder()
                    .columnDefs(boundaryColumns)
                    .data(boundaryData)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating boundary hierarchy sheet data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate boundary hierarchy sheet data", e);
        }
    }
    
    private List<ColumnDef> createBoundaryHierarchyColumnDefs(List<BoundaryHierarchyChild> hierarchyRelations, String hierarchyType) {
        List<ColumnDef> columns = new ArrayList<>();
        
        // Create columns for each hierarchy level using exact project-factory pattern
        for (int i = 0; i < hierarchyRelations.size(); i++) {
            String boundaryType = hierarchyRelations.get(i).getBoundaryType();
            String columnName = (hierarchyType + "_" + boundaryType).toUpperCase();
            
            columns.add(ColumnDef.builder()
                    .name(columnName)
                    .orderNumber(i + 1)
                    .width(50)
                    .colorHex("#93c47d")
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
        
        return columns;
    }
    
    private List<Map<String, Object>> getBoundaryHierarchyDataFromFiltered(List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                                                          List<BoundaryHierarchyChild> hierarchyRelations,
                                                                          String hierarchyType,
                                                                          Map<String, String> localizationMap) {
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
                
                data.add(row);
            }
        }
        
        return data;
    }
}