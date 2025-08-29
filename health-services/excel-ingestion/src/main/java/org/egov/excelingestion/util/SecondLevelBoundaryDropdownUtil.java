package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.web.models.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility for creating a single column dropdown with 2nd level localized boundaries
 * This simplifies the boundary selection to just one dropdown instead of level + boundary
 */
@Component
@Slf4j
public class SecondLevelBoundaryDropdownUtil {

    private final ExcelIngestionConfig config;
    private final BoundaryService boundaryService;
    private final BoundaryUtil boundaryUtil;
    private final ExcelStyleHelper excelStyleHelper;

    public SecondLevelBoundaryDropdownUtil(ExcelIngestionConfig config, BoundaryService boundaryService,
                                          BoundaryUtil boundaryUtil, ExcelStyleHelper excelStyleHelper) {
        this.config = config;
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
        this.excelStyleHelper = excelStyleHelper;
    }

    /**
     * Adds a single boundary dropdown column with 2nd level boundaries to an existing sheet
     * 
     * @param workbook Excel workbook
     * @param sheetName Name of the sheet to add column to
     * @param localizationMap Localization map for headers and values
     * @param configuredBoundaries List of configured boundaries from additionalDetails
     * @param hierarchyType Boundary hierarchy type
     * @param tenantId Tenant ID
     * @param requestInfo Request info for API calls
     */
    public void addSecondLevelBoundaryColumn(XSSFWorkbook workbook, String sheetName, Map<String, String> localizationMap,
                                            List<Boundary> configuredBoundaries, String hierarchyType, 
                                            String tenantId, RequestInfo requestInfo) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            log.warn("Sheet '{}' not found, cannot add boundary column", sheetName);
            return;
        }
        
        if (configuredBoundaries == null || configuredBoundaries.isEmpty()) {
            log.info("No boundaries configured in additionalDetails for sheet '{}', skipping boundary column creation", sheetName);
            return;
        }
        
        // Fetch boundary relationship data
        BoundarySearchResponse relationshipData = boundaryService.fetchBoundaryRelationship(tenantId, hierarchyType, requestInfo);
        Map<String, EnrichedBoundary> codeToEnrichedBoundary = boundaryUtil.buildCodeToBoundaryMap(relationshipData);
        
        // Fetch boundary hierarchy data
        BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);
        if (hierarchyData == null || hierarchyData.getBoundaryHierarchy() == null || hierarchyData.getBoundaryHierarchy().isEmpty()) {
            log.error("Boundary hierarchy data is null or empty for type: {}", hierarchyType);
            return;
        }
        
        List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();
        List<String> levelTypes = hierarchyRelations.stream()
                .map(BoundaryHierarchyChild::getBoundaryType)
                .collect(Collectors.toList());
        
        // Check if we have at least 2 levels
        if (levelTypes.size() < 2) {
            log.warn("Hierarchy has less than 2 levels, cannot create 2nd level boundary dropdown");
            return;
        }
        
        // Process boundaries to get all 2nd level boundaries
        List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                configuredBoundaries, codeToEnrichedBoundary, levelTypes);
        
        // Extract unique 2nd level boundaries
        Set<String> secondLevelBoundaries = new TreeSet<>(); // TreeSet for natural sorting
        Map<String, String> boundaryCodeMap = new HashMap<>();
        
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> path = boundary.getBoundaryPath();
            // Check if we have a 2nd level boundary (index 1)
            if (path.size() > 1 && path.get(1) != null) {
                String boundaryCode = path.get(1);
                String boundaryName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                secondLevelBoundaries.add(boundaryName);
                boundaryCodeMap.put(boundaryName, boundaryCode);
            }
        }
        
        if (secondLevelBoundaries.isEmpty()) {
            log.info("No 2nd level boundaries available for sheet '{}', skipping boundary column creation", sheetName);
            return;
        }
        
        // Add boundary column after schema columns
        Row hiddenRow = sheet.getRow(0);
        Row visibleRow = sheet.getRow(1);
        
        if (hiddenRow == null) {
            hiddenRow = sheet.createRow(0);
        }
        if (visibleRow == null) {
            visibleRow = sheet.createRow(1);
        }
        
        int lastSchemaCol = visibleRow.getLastCellNum();
        if (lastSchemaCol < 0) lastSchemaCol = 0;
        
        // Add technical name to hidden row
        hiddenRow.createCell(lastSchemaCol).setCellValue("BOUNDARY_NAME");
        hiddenRow.createCell(lastSchemaCol + 1).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        
        // Create header style
        CellStyle boundaryHeaderStyle = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, config.getDefaultHeaderColor());
        
        // Add localized header for 2nd level - use the localized name for level 2
        Cell boundaryHeaderCell = visibleRow.createCell(lastSchemaCol);
        String level2Key = "HCM_CAMP_CONF_LEVEL_2";
        String level2Name = localizationMap.getOrDefault(level2Key, level2Key);
        boundaryHeaderCell.setCellValue(level2Name);
        boundaryHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        // Add hidden boundary code column header
        Cell boundaryCodeHeaderCell = visibleRow.createCell(lastSchemaCol + 1);
        boundaryCodeHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
        boundaryCodeHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        // Create dropdown data and mapping
        createSecondLevelBoundaryDropdown(workbook, secondLevelBoundaries, boundaryCodeMap);
        
        // Add data validation for boundary column
        addSecondLevelBoundaryValidation(workbook, sheet, lastSchemaCol, secondLevelBoundaries);
        
        // Set column widths
        sheet.setColumnWidth(lastSchemaCol, 50 * 256); // Boundary name column
        sheet.setColumnWidth(lastSchemaCol + 1, 25 * 256); // Boundary code column (hidden)
        sheet.createFreezePane(0, 2); // Freeze header rows
        
        // Hide the boundary code column
        sheet.setColumnHidden(lastSchemaCol + 1, true);
        
        // Unlock cells for user input
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);
        for (int r = 2; r <= config.getExcelRowLimit(); r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                row = sheet.createRow(r);
            Cell cell = row.getCell(lastSchemaCol);
            if (cell == null)
                cell = row.createCell(lastSchemaCol);
            cell.setCellStyle(unlocked);
        }
        
        log.info("Added single column 2nd level boundary dropdown with {} options", secondLevelBoundaries.size());
    }
    
    /**
     * Creates hidden sheet with 2nd level boundary dropdown data
     */
    private void createSecondLevelBoundaryDropdown(XSSFWorkbook workbook, Set<String> boundaryNames, 
                                                  Map<String, String> boundaryCodeMap) {
        
        // Create or get the hidden boundaries sheet
        Sheet boundarySheet = workbook.getSheet("_h_SecondLevelBoundaries_h_");
        if (boundarySheet == null) {
            boundarySheet = workbook.createSheet("_h_SecondLevelBoundaries_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_SecondLevelBoundaries_h_"), true);
        } else {
            // Clear existing content
            for (int i = boundarySheet.getLastRowNum(); i >= 0; i--) {
                Row row = boundarySheet.getRow(i);
                if (row != null) {
                    boundarySheet.removeRow(row);
                }
            }
        }
        
        // Add boundary names to column A
        int rowNum = 0;
        for (String boundaryName : boundaryNames) {
            Row row = boundarySheet.createRow(rowNum++);
            row.createCell(0).setCellValue(boundaryName);
        }
        
        // Create named range for the boundary list
        if (!boundaryNames.isEmpty()) {
            Name namedRange = workbook.getName("SecondLevelBoundaries");
            if (namedRange == null) {
                namedRange = workbook.createName();
                namedRange.setNameName("SecondLevelBoundaries");
            }
            namedRange.setRefersToFormula("_h_SecondLevelBoundaries_h_!$A$1:$A$" + boundaryNames.size());
        }
        
        // Create code mapping sheet
        Sheet codeMapSheet = workbook.getSheet("_h_SecondLevelCodeMap_h_");
        if (codeMapSheet == null) {
            codeMapSheet = workbook.createSheet("_h_SecondLevelCodeMap_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_SecondLevelCodeMap_h_"), true);
        } else {
            // Clear existing content
            for (int i = codeMapSheet.getLastRowNum(); i >= 0; i--) {
                Row row = codeMapSheet.getRow(i);
                if (row != null) {
                    codeMapSheet.removeRow(row);
                }
            }
        }
        
        // Add mapping data
        Row headerRow = codeMapSheet.createRow(0);
        headerRow.createCell(0).setCellValue("BoundaryName");
        headerRow.createCell(1).setCellValue("BoundaryCode");
        
        rowNum = 1;
        for (Map.Entry<String, String> entry : boundaryCodeMap.entrySet()) {
            Row row = codeMapSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        
        // Create named range for the mapping
        if (!boundaryCodeMap.isEmpty()) {
            Name codeMapRange = workbook.getName("SecondLevelCodeMap");
            if (codeMapRange == null) {
                codeMapRange = workbook.createName();
                codeMapRange.setNameName("SecondLevelCodeMap");
            }
            codeMapRange.setRefersToFormula("_h_SecondLevelCodeMap_h_!$A$1:$B$" + (boundaryCodeMap.size() + 1));
        }
    }
    
    /**
     * Adds data validation for the 2nd level boundary column
     */
    private void addSecondLevelBoundaryValidation(XSSFWorkbook workbook, Sheet sheet, int columnIndex, 
                                                 Set<String> boundaryNames) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Add data validation for all data rows
        for (int rowIndex = 2; rowIndex <= config.getExcelRowLimit(); rowIndex++) {
            // Boundary dropdown using named range
            DataValidationConstraint boundaryConstraint = dvHelper.createFormulaListConstraint("SecondLevelBoundaries");
            CellRangeAddressList boundaryAddr = new CellRangeAddressList(rowIndex, rowIndex, columnIndex, columnIndex);
            DataValidation boundaryValidation = dvHelper.createValidation(boundaryConstraint, boundaryAddr);
            boundaryValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            boundaryValidation.setShowErrorBox(true);
            boundaryValidation.createErrorBox("Invalid Boundary", "Please select a valid boundary from the dropdown list.");
            boundaryValidation.setShowPromptBox(true);
            boundaryValidation.createPromptBox("Select Boundary", "Choose a boundary from the dropdown list.");
            sheet.addValidationData(boundaryValidation);
            
            // Add VLOOKUP formula to boundary code column for automatic population
            String boundaryNameCellRef = CellReference.convertNumToColString(columnIndex) + (rowIndex + 1);
            String vlookupFormula = "IF(" + boundaryNameCellRef + "=\"\", \"\", VLOOKUP(" + boundaryNameCellRef + ", SecondLevelCodeMap, 2, FALSE))";
            
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);
            Cell boundaryCodeCell = row.createCell(columnIndex + 1);
            boundaryCodeCell.setCellFormula(vlookupFormula);
        }
    }
}