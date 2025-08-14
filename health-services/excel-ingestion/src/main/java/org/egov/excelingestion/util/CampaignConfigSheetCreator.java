package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.LocalizationService;
import org.egov.excelingestion.web.models.BoundaryHierarchyResponse;
import org.egov.excelingestion.web.models.BoundaryHierarchy;
import org.egov.excelingestion.web.models.RequestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CampaignConfigSheetCreator {

    @Autowired
    private ExcelIngestionConfig config;
    
    @Autowired
    private BoundaryService boundaryService;
    
    @Autowired
    private LocalizationService localizationService;
    
    @Autowired
    private CustomExceptionHandler exceptionHandler;

    /**
     * Creates a campaign configuration sheet with sections and editable cells
     * 
     * @param workbook The workbook to add the sheet to
     * @param sheetName The localized name for the sheet
     * @param configData The configuration data from MDMS
     * @param localizationMap The localization map for translating keys
     * @param tenantId The tenant ID for fetching boundary data
     * @param hierarchyType The hierarchy type for boundary levels
     * @param requestInfo The request info for API calls
     * @return The updated workbook
     */
    public Workbook createCampaignConfigSheet(XSSFWorkbook workbook, String sheetName, 
                                            Map<String, Object> configData, 
                                            Map<String, String> localizationMap,
                                            String tenantId, String hierarchyType, 
                                            RequestInfo requestInfo) {
        
        log.info("Creating campaign configuration sheet: {}", sheetName);
        
        Sheet configSheet = workbook.createSheet(sheetName);
        
        // Extract sections from config data
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) configData.get("sections");
        String highlightColor = (String) configData.getOrDefault("highlightColor", "#00FF00");
        String headerInfo = (String) configData.get("headerInfo");
        
        int currentRow = 0;
        
        // Add header info if present
        if (headerInfo != null) {
            String localizedHeaderInfo = localizationMap.getOrDefault(headerInfo, headerInfo);
            Row headerRow = configSheet.createRow(currentRow++);
            Cell headerCell = headerRow.createCell(0);
            headerCell.setCellValue(localizedHeaderInfo);
            headerCell.setCellStyle(createHeaderInfoStyle(workbook));
            
            // Merge cells across 3 columns for header
            configSheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                currentRow - 1, currentRow - 1, 0, 2));
            
            currentRow++; // Add blank row after header
        }
        
        // Process each section
        for (Map<String, Object> section : sections) {
            currentRow = createSection(workbook, configSheet, section, localizationMap, 
                                     highlightColor, currentRow, tenantId, hierarchyType, requestInfo);
            currentRow += 2; // Add spacing between sections
        }
        
        // Set column widths
        configSheet.setColumnWidth(0, 40 * 256); // Column A - wider for labels
        configSheet.setColumnWidth(1, 25 * 256); // Column B - editable values
        configSheet.setColumnWidth(2, 50 * 256); // Column C - examples
        
        // Freeze panes to keep headers visible
        configSheet.createFreezePane(0, 0);
        
        // Apply sheet protection with unlocked editable cells
        protectSheetWithEditableCells(workbook, configSheet);
        
        log.info("Campaign configuration sheet created successfully");
        return workbook;
    }
    
    private int createSection(Workbook workbook, Sheet sheet, Map<String, Object> section,
                            Map<String, String> localizationMap, String highlightColor, int startRow,
                            String tenantId, String hierarchyType, RequestInfo requestInfo) {
        
        String sectionTitle = (String) section.get("title");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) section.get("columns");
        @SuppressWarnings("unchecked")
        List<List<String>> rows = (List<List<String>>) section.get("rows");
        
        int currentRow = startRow;
        
        // Create section title row
        if (sectionTitle != null) {
            Row titleRow = sheet.createRow(currentRow++);
            Cell titleCell = titleRow.createCell(0);
            String localizedTitle = localizationMap.getOrDefault(sectionTitle, sectionTitle);
            titleCell.setCellValue(localizedTitle);
            titleCell.setCellStyle(createSectionTitleStyle(workbook));
            
            // Merge title across all columns
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                currentRow - 1, currentRow - 1, 0, columns.size() - 1));
            
            currentRow++; // Blank row after title
        }
        
        // Create column headers
        Row headerRow = sheet.createRow(currentRow++);
        for (int col = 0; col < columns.size(); col++) {
            Map<String, Object> column = columns.get(col);
            String headerKey = (String) column.get("header");
            String localizedHeader = localizationMap.getOrDefault(headerKey, headerKey);
            
            Cell headerCell = headerRow.createCell(col);
            headerCell.setCellValue(localizedHeader);
            headerCell.setCellStyle(createColumnHeaderStyle(workbook));
        }
        
        // Check if any column has areBoundaryLevels: true
        boolean hasBoundaryLevels = columns.stream()
            .anyMatch(col -> Boolean.TRUE.equals(col.get("areBoundaryLevels")));
        
        List<String> boundaryLevelNames = null;
        if (hasBoundaryLevels) {
            boundaryLevelNames = fetchBoundaryLevelNames(tenantId, hierarchyType, requestInfo);
            
            // Validate rows count should not be less than boundary levels count
            if (rows.size() < boundaryLevelNames.size()) {
                String errorMessage = String.format("Number of rows (%d) is less than boundary hierarchy levels (%d). Minimum required rows: %d, found: %d rows in MDMS configuration.", 
                    rows.size(), boundaryLevelNames.size(), boundaryLevelNames.size(), rows.size());
                exceptionHandler.throwCustomException(
                    ErrorConstants.MISMATCH_ROWS_FOR_LEVELS,
                    errorMessage,
                    new IllegalStateException("Row count is less than boundary levels")
                );
            }
        }
        
        // Create data rows
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> rowData = rows.get(rowIndex);
            Row dataRow = sheet.createRow(currentRow++);
            
            for (int col = 0; col < rowData.size() && col < columns.size(); col++) {
                Map<String, Object> columnDef = columns.get(col);
                boolean isEditable = Boolean.TRUE.equals(columnDef.get("editable"));
                boolean isHighlighted = Boolean.TRUE.equals(columnDef.get("highlight"));
                boolean shouldLocalize = Boolean.TRUE.equals(columnDef.get("localize"));
                boolean areBoundaryLevels = Boolean.TRUE.equals(columnDef.get("areBoundaryLevels"));
                
                Cell dataCell = dataRow.createCell(col);
                String cellValue = rowData.get(col);
                
                // Handle boundary levels column
                if (areBoundaryLevels && boundaryLevelNames != null && rowIndex < boundaryLevelNames.size()) {
                    String boundaryLevelName = boundaryLevelNames.get(rowIndex);
                    String localizedBoundaryLevel = localizationMap.getOrDefault(boundaryLevelName, boundaryLevelName);
                    dataCell.setCellValue(localizedBoundaryLevel);
                } else {
                    // Only localize the cell value if the column has localize: true
                    String displayValue = shouldLocalize ? 
                        localizationMap.getOrDefault(cellValue, cellValue) : cellValue;
                    dataCell.setCellValue(displayValue);
                }
                
                // Apply appropriate style
                if (isEditable && isHighlighted) {
                    dataCell.setCellStyle(createEditableHighlightedStyle(workbook, highlightColor));
                } else if (isEditable) {
                    dataCell.setCellStyle(createEditableStyle(workbook));
                } else {
                    dataCell.setCellStyle(createReadOnlyStyle(workbook));
                }
            }
        }
        
        return currentRow;
    }
    
    private CellStyle createHeaderInfoStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setLocked(true);
        
        return style;
    }
    
    private CellStyle createSectionTitleStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.DARK_RED.getIndex());
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setLocked(true);
        
        // Add borders
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        
        return style;
    }
    
    private CellStyle createColumnHeaderStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        
        // Set background color
        Color headerColor = Color.decode(config.getDefaultHeaderColor());
        XSSFColor xssfColor = new XSSFColor(headerColor, null);
        style.setFillForegroundColor(xssfColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Add borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        style.setLocked(true);
        
        return style;
    }
    
    private CellStyle createEditableHighlightedStyle(Workbook workbook, String highlightColor) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        
        // Set highlight background color
        try {
            Color color = Color.decode(highlightColor);
            XSSFColor xssfColor = new XSSFColor(color, null);
            style.setFillForegroundColor(xssfColor);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        } catch (Exception e) {
            log.warn("Invalid highlight color: {}, using default", highlightColor);
            style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        
        // Add borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Make cell unlocked for editing
        style.setLocked(false);
        
        return style;
    }
    
    private CellStyle createEditableStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // Add subtle border to indicate editability
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Make cell unlocked for editing
        style.setLocked(false);
        
        return style;
    }
    
    private CellStyle createReadOnlyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        Font font = workbook.createFont();
        font.setColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Lock the cell
        style.setLocked(true);
        
        return style;
    }
    
    
    private void protectSheetWithEditableCells(Workbook workbook, Sheet sheet) {
        // The sheet is protected, but cells with unlocked style can be edited
        sheet.protectSheet(config.getExcelSheetPassword());
    }
    
    /**
     * Fetches boundary level names from boundary hierarchy definition
     * 
     * @param tenantId The tenant ID
     * @param hierarchyType The hierarchy type
     * @param requestInfo The request info for API calls
     * @return List of boundary level names in order
     */
    private List<String> fetchBoundaryLevelNames(String tenantId, String hierarchyType, RequestInfo requestInfo) {
        log.info("Fetching boundary level names for tenantId: {}, hierarchyType: {}", tenantId, hierarchyType);
        
        try {
            BoundaryHierarchyResponse boundaryResponse = boundaryService.fetchBoundaryHierarchy(
                tenantId, hierarchyType, requestInfo);
            
            if (boundaryResponse == null || boundaryResponse.getBoundaryHierarchy() == null 
                || boundaryResponse.getBoundaryHierarchy().isEmpty()) {
                exceptionHandler.throwCustomException(
                    ErrorConstants.BOUNDARY_HIERARCHY_NOT_FOUND,
                    String.format(ErrorConstants.BOUNDARY_HIERARCHY_NOT_FOUND_MESSAGE, hierarchyType),
                    new IllegalStateException("Boundary hierarchy response is null or empty")
                );
                return null; // Never reached
            }
            
            BoundaryHierarchy hierarchy = boundaryResponse.getBoundaryHierarchy().get(0);
            
            if (hierarchy.getBoundaryHierarchy() == null || hierarchy.getBoundaryHierarchy().isEmpty()) {
                exceptionHandler.throwCustomException(
                    ErrorConstants.BOUNDARY_LEVELS_NOT_FOUND,
                    ErrorConstants.BOUNDARY_LEVELS_NOT_FOUND_MESSAGE,
                    new IllegalStateException("Boundary levels not found in hierarchy definition")
                );
                return null; // Never reached
            }
            
            // Extract boundary level names from hierarchy definition
            List<String> boundaryLevelNames = hierarchy.getBoundaryHierarchy().stream()
                .map(level -> level.getBoundaryType())
                .toList();
            
            log.info("Successfully fetched {} boundary levels: {}", boundaryLevelNames.size(), boundaryLevelNames);
            return boundaryLevelNames;
            
        } catch (Exception e) {
            log.error("Error fetching boundary level names: {}", e.getMessage(), e);
            if (!(e instanceof RuntimeException)) {
                exceptionHandler.throwCustomException(
                    ErrorConstants.BOUNDARY_SERVICE_ERROR,
                    ErrorConstants.BOUNDARY_SERVICE_ERROR_MESSAGE,
                    e
                );
            }
            throw e; // Re-throw runtime exceptions (like CustomException)
        }
    }
}