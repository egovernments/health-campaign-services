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
                                                 String localizedSheetName) {
        
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
        
        // Add columns for each level
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
        for (int c = 0; c < originalLevels.size(); c++) {
            // Set default style for entire hierarchy columns as unlocked
            hierarchySheet.setDefaultColumnStyle(c, unlockedStyle);
        }
        
        // Lock only the header rows (row 0 and row 1) by applying locked style
        // Note: visibleRow cells already have headerStyle which includes locking
        for (int c = 0; c < originalLevels.size(); c++) {
            hiddenRow.getCell(c).setCellStyle(lockedStyle);
            // visibleRow cells already have headerStyle applied above
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

}