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
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BoundaryHierarchySheetCreator {

    private final BoundaryService boundaryService;
    private final ExcelIngestionConfig config;
    private final BoundaryUtil boundaryUtil;
    private final CustomExceptionHandler exceptionHandler;
    
    public BoundaryHierarchySheetCreator(BoundaryService boundaryService,
                                        ExcelIngestionConfig config,
                                        BoundaryUtil boundaryUtil,
                                        CustomExceptionHandler exceptionHandler) {
        this.boundaryService = boundaryService;
        this.config = config;
        this.boundaryUtil = boundaryUtil;
        this.exceptionHandler = exceptionHandler;
    }

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
        Map<String, EnrichedBoundary> codeToEnrichedBoundary = boundaryUtil.buildCodeToBoundaryMap(relationshipData);
        
        // Process boundaries and expand includeAllChildren using BoundaryUtil
        List<BoundaryUtil.BoundaryRowData> boundaryRows = boundaryUtil.processBoundariesWithEnrichment(
                boundaries, codeToEnrichedBoundary, levelTypes);
        
        // Filter to keep only last level boundaries
        int lastLevelIndex = levelTypes.size() - 1;
        List<BoundaryUtil.BoundaryRowData> lastLevelRows = boundaryRows.stream()
                .filter(row -> row.isLastLevel(lastLevelIndex))
                .collect(Collectors.toList());
        
        // Write rows to sheet
        int rowNum = 2; // Start after headers
        for (BoundaryUtil.BoundaryRowData boundaryRowData : lastLevelRows) {
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

}