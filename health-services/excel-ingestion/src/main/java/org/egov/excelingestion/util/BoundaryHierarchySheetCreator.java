package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.awt.Color;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.service.BoundaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BoundaryHierarchySheetCreator {

    @Autowired
    private BoundaryService boundaryService;
    
    @Autowired
    private ExcelIngestionConfig config;
    
    @Autowired
    private CustomExceptionHandler exceptionHandler;

    public Workbook createBoundaryHierarchySheet(XSSFWorkbook workbook, 
                                                 String hierarchyType,
                                                 String tenantId,
                                                 RequestInfo requestInfo,
                                                 Map<String, String> localizationMap,
                                                 String localizedSheetName,
                                                 List<Boundary> boundaries) {
        
        // Fetch boundary hierarchy data to get level types
        BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);
        
        if (hierarchyData == null || hierarchyData.getBoundaryHierarchy() == null || hierarchyData.getBoundaryHierarchy().isEmpty()) {
            exceptionHandler.throwCustomException(ErrorConstants.BOUNDARY_HIERARCHY_NOT_FOUND,
                    ErrorConstants.BOUNDARY_HIERARCHY_NOT_FOUND_MESSAGE.replace("{0}", hierarchyType),
                    new RuntimeException("Boundary hierarchy data is null or empty for type: " + hierarchyType));
        }

        List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();
        
        // Get original levels from hierarchy (just the boundary types for column headers)
        List<String> originalLevels = new ArrayList<>();
        if (hierarchyRelations != null && !hierarchyRelations.isEmpty()) {
            for (BoundaryHierarchyChild hierarchyRelation : hierarchyRelations) {
                originalLevels.add(hierarchyRelation.getBoundaryType());
            }
        }

        // Create the sheet
        Sheet hierarchySheet = workbook.createSheet(localizedSheetName);
        
        // Create headers
        Row hiddenRow = hierarchySheet.createRow(0);
        Row visibleRow = hierarchySheet.createRow(1);
        
        // Create header style with default color
        CellStyle headerStyle = createHeaderStyle(workbook, config.getDefaultHeaderColor());
        
        // Add columns for each level first
        for (int i = 0; i < originalLevels.size(); i++) {
            String levelType = originalLevels.get(i);
            
            // Hidden row: unlocalized level names
            String unlocalizedCode = hierarchyType.toUpperCase() + "_" + levelType.toUpperCase();
            hiddenRow.createCell(i).setCellValue(unlocalizedCode);
            
            // Visible row: localized level names
            String localizedLevelName = localizationMap.getOrDefault(unlocalizedCode, unlocalizedCode);
            Cell headerCell = visibleRow.createCell(i);
            headerCell.setCellValue(localizedLevelName);
            headerCell.setCellStyle(headerStyle);
            
            // Set column width
            hierarchySheet.setColumnWidth(i, 40 * 256);
        }
        
        // Add boundary code column as the last column (hidden)
        int boundaryCodeCol = originalLevels.size();
        hiddenRow.createCell(boundaryCodeCol).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        Cell codeHeaderCell = visibleRow.createCell(boundaryCodeCol);
        codeHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
        codeHeaderCell.setCellStyle(headerStyle);
        // Hide the boundary code column
        hierarchySheet.setColumnHidden(boundaryCodeCol, true);
        hierarchySheet.setColumnWidth(boundaryCodeCol, 40 * 256);
        
        // Hide the first row (technical names)
        hiddenRow.setZeroHeight(true);
        
        // Freeze panes after headers
        hierarchySheet.createFreezePane(0, 2);
        
        // Set default cell styles for the entire sheet
        CellStyle lockedStyle = workbook.createCellStyle();
        lockedStyle.setLocked(true);
        
        CellStyle unlockedStyle = workbook.createCellStyle();
        unlockedStyle.setLocked(false);
        
        // Set column-level default styles to avoid creating millions of cells
        // Column 0 is for boundary code, then levels start from column 1
        for (int c = 0; c <= originalLevels.size(); c++) {
            // Set default style for entire hierarchy columns as unlocked
            hierarchySheet.setDefaultColumnStyle(c, unlockedStyle);
        }
        
        // Lock only the header rows (row 0 and row 1) by applying locked style
        // Note: visibleRow cells already have headerStyle which includes locking
        for (int c = 0; c <= originalLevels.size(); c++) {
            if (hiddenRow.getCell(c) != null) {
                hiddenRow.getCell(c).setCellStyle(lockedStyle);
            }
            // visibleRow cells already have headerStyle applied above
        }
        
        // Populate boundary rows if boundaries are provided
        if (boundaries != null && !boundaries.isEmpty()) {
            populateBoundaryRows(hierarchySheet, boundaries, originalLevels, hierarchyType, tenantId, requestInfo, localizationMap, unlockedStyle);
        }
        
        // Protect the sheet to enforce locking (headers locked by default, only hierarchy columns unlocked)
        hierarchySheet.protectSheet(config.getExcelSheetPassword());
        
        return workbook;
    }
    
    private CellStyle createHeaderStyle(Workbook workbook, String colorHex) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        
        // Set background color
        Color color = Color.decode(colorHex);
        XSSFColor xssfColor = new XSSFColor(color, null);
        style.setFillForegroundColor(xssfColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Set borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Center align text
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Bold font
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        
        // Lock the cell
        style.setLocked(true);
        
        return style;
    }
    
    private void populateBoundaryRows(Sheet sheet, List<Boundary> boundaries, List<String> levelTypes, 
                                     String hierarchyType, String tenantId, RequestInfo requestInfo,
                                     Map<String, String> localizationMap, CellStyle unlockedStyle) {
        
        // Fetch all boundary relationships to get children when includeAllChildren is true
        BoundarySearchResponse relationshipData = boundaryService.fetchBoundaryRelationship(tenantId, hierarchyType, requestInfo);
        
        // Build a map of code to EnrichedBoundary for quick lookup
        Map<String, EnrichedBoundary> codeToEnrichedBoundary = new HashMap<>();
        if (relationshipData != null && relationshipData.getTenantBoundary() != null) {
            for (HierarchyRelation hr : relationshipData.getTenantBoundary()) {
                buildCodeToBoundaryMap(hr.getBoundary(), codeToEnrichedBoundary);
            }
        }
        
        // Process boundaries and expand includeAllChildren
        List<BoundaryRowData> boundaryRows = new ArrayList<>();
        Set<String> processedCodes = new HashSet<>();
        
        // Find root boundary
        Boundary rootBoundary = boundaries.stream()
                .filter(b -> Boolean.TRUE.equals(b.getIsRoot()))
                .findFirst()
                .orElse(null);
                
        if (rootBoundary == null) {
            log.error("No root boundary found in the boundaries list");
            return;
        }
        
        // Process boundaries starting from root
        processBoundary(rootBoundary, boundaries, codeToEnrichedBoundary, boundaryRows, 
                       processedCodes, new ArrayList<>(), levelTypes);
        
        // Filter to keep only last level boundaries
        int lastLevelIndex = levelTypes.size() - 1;
        List<BoundaryRowData> lastLevelRows = boundaryRows.stream()
                .filter(row -> row.isLastLevel(lastLevelIndex))
                .collect(Collectors.toList());
        
        // Write rows to sheet
        int rowNum = 2; // Start after headers
        for (BoundaryRowData boundaryRowData : lastLevelRows) {
            Row row = sheet.createRow(rowNum++);
            
            // Columns 0 to n-1: Localized names for each level
            List<String> boundaryRow = boundaryRowData.getBoundaryPath();
            for (int i = 0; i < boundaryRow.size(); i++) {
                Cell cell = row.createCell(i);
                String boundaryCode = boundaryRow.get(i);
                if (boundaryCode != null) {
                    // Get localized name for the boundary code
                    String localizedName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                    cell.setCellValue(localizedName);
                    cell.setCellStyle(unlockedStyle);
                }
            }
            
            // Last column: Boundary code of the last level (hidden column)
            String lastLevelCode = boundaryRowData.getLastLevelCode();
            Cell codeCell = row.createCell(levelTypes.size());
            codeCell.setCellValue(lastLevelCode);
            codeCell.setCellStyle(unlockedStyle);
        }
    }
    
    private void buildCodeToBoundaryMap(List<EnrichedBoundary> boundaries, Map<String, EnrichedBoundary> codeMap) {
        if (boundaries == null) return;
        
        for (EnrichedBoundary boundary : boundaries) {
            codeMap.put(boundary.getCode(), boundary);
            if (boundary.getChildren() != null && !boundary.getChildren().isEmpty()) {
                buildCodeToBoundaryMap(boundary.getChildren(), codeMap);
            }
        }
    }
    
    private void processBoundary(Boundary boundary, List<Boundary> allBoundaries, 
                                Map<String, EnrichedBoundary> codeToEnrichedBoundary,
                                List<BoundaryRowData> boundaryRows, Set<String> processedCodes,
                                List<String> currentPath, List<String> levelTypes) {
        
        if (boundary == null || processedCodes.contains(boundary.getCode())) {
            return;
        }
        
        processedCodes.add(boundary.getCode());
        
        // Create new path with current boundary
        List<String> newPath = new ArrayList<>(currentPath);
        int levelIndex = getLevelIndex(boundary.getType(), levelTypes);
        
        // Ensure path has enough elements
        while (newPath.size() <= levelIndex) {
            newPath.add(null);
        }
        newPath.set(levelIndex, boundary.getCode());
        
        // If includeAllChildren is true, process all children from enriched boundary data
        if (Boolean.TRUE.equals(boundary.getIncludeAllChildren())) {
            EnrichedBoundary enrichedBoundary = codeToEnrichedBoundary.get(boundary.getCode());
            if (enrichedBoundary != null && enrichedBoundary.getChildren() != null) {
                processAllChildren(enrichedBoundary.getChildren(), codeToEnrichedBoundary, 
                                 boundaryRows, processedCodes, newPath, levelTypes);
            }
        } else {
            // Add current path as a row with boundary code
            boundaryRows.add(new BoundaryRowData(new ArrayList<>(newPath), boundary.getCode()));
            
            // Process only the boundaries that are in the input list and are children of current
            List<Boundary> children = allBoundaries.stream()
                    .filter(b -> boundary.getCode().equals(b.getParent()))
                    .collect(Collectors.toList());
                    
            for (Boundary child : children) {
                processBoundary(child, allBoundaries, codeToEnrichedBoundary, 
                              boundaryRows, processedCodes, newPath, levelTypes);
            }
        }
    }
    
    private void processAllChildren(List<EnrichedBoundary> children, 
                                   Map<String, EnrichedBoundary> codeToEnrichedBoundary,
                                   List<BoundaryRowData> boundaryRows, Set<String> processedCodes,
                                   List<String> currentPath, List<String> levelTypes) {
        
        if (children == null || children.isEmpty()) {
            return;
        }
        
        for (EnrichedBoundary child : children) {
            if (!processedCodes.contains(child.getCode())) {
                processedCodes.add(child.getCode());
                
                // Create new path with child
                List<String> newPath = new ArrayList<>(currentPath);
                int levelIndex = getLevelIndex(child.getBoundaryType(), levelTypes);
                
                // Ensure path has enough elements
                while (newPath.size() <= levelIndex) {
                    newPath.add(null);
                }
                newPath.set(levelIndex, child.getCode());
                
                // Add current path as a row with boundary code
                boundaryRows.add(new BoundaryRowData(new ArrayList<>(newPath), child.getCode()));
                
                // Recursively process children
                if (child.getChildren() != null && !child.getChildren().isEmpty()) {
                    processAllChildren(child.getChildren(), codeToEnrichedBoundary,
                                     boundaryRows, processedCodes, newPath, levelTypes);
                }
            }
        }
    }
    
    private int getLevelIndex(String boundaryType, List<String> levelTypes) {
        for (int i = 0; i < levelTypes.size(); i++) {
            if (levelTypes.get(i).equalsIgnoreCase(boundaryType)) {
                return i;
            }
        }
        return -1;
    }
    
    // Inner class to hold boundary row data
    private static class BoundaryRowData {
        private final List<String> boundaryPath;
        private final String lastLevelCode;
        
        public BoundaryRowData(List<String> boundaryPath, String lastLevelCode) {
            this.boundaryPath = boundaryPath;
            this.lastLevelCode = lastLevelCode;
        }
        
        public List<String> getBoundaryPath() {
            return boundaryPath;
        }
        
        public String getLastLevelCode() {
            return lastLevelCode;
        }
        
        public boolean isLastLevel(int lastLevelIndex) {
            // Check if the boundary path has a non-null value at the last level index
            return boundaryPath.size() > lastLevelIndex && boundaryPath.get(lastLevelIndex) != null;
        }
    }

}