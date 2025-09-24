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
import org.egov.excelingestion.util.LocalizationUtil;

import java.util.*;

/**
 * Utility class for adding boundary columns to Excel sheets
 * Handles level, boundary name, and boundary code columns with validations
 */
@Component
@Slf4j
public class BoundaryColumnUtil {

    private final ExcelIngestionConfig config;
    private final BoundaryService boundaryService;
    private final BoundaryUtil boundaryUtil;
    private final ExcelStyleHelper excelStyleHelper;

    public BoundaryColumnUtil(ExcelIngestionConfig config, BoundaryService boundaryService,
                            BoundaryUtil boundaryUtil, ExcelStyleHelper excelStyleHelper) {
        this.config = config;
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
        this.excelStyleHelper = excelStyleHelper;
    }

    /**
     * Adds boundary columns (level, boundary name, boundary code) to an existing sheet
     * 
     * @param workbook Excel workbook
     * @param sheetName Name of the sheet to add columns to
     * @param localizationMap Localization map for headers
     * @param configuredBoundaries List of configured boundaries from additionalDetails
     * @param hierarchyType Boundary hierarchy type
     * @param tenantId Tenant ID
     * @param requestInfo Request info for API calls
     */
    public void addBoundaryColumnsToSheet(XSSFWorkbook workbook, String sheetName, Map<String, String> localizationMap,
                                         List<Boundary> configuredBoundaries, String hierarchyType, 
                                         String tenantId, RequestInfo requestInfo) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            log.warn("Sheet '{}' not found, cannot add boundary columns", sheetName);
            return;
        }
        
        // Check if boundaries are configured in additionalDetails
        if (configuredBoundaries == null || configuredBoundaries.isEmpty()) {
            log.info("No boundaries configured in additionalDetails for sheet '{}', skipping boundary column creation", sheetName);
            return;
        }
        
        // Fetch boundary relationship data to get enriched boundary information
        BoundarySearchResponse relationshipData = boundaryService.fetchBoundaryRelationship(tenantId, hierarchyType, requestInfo);
        Map<String, EnrichedBoundary> codeToEnrichedBoundary = boundaryUtil.buildCodeToBoundaryMap(relationshipData);
        
        // Fetch boundary hierarchy data to get level types
        BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);
        if (hierarchyData == null || hierarchyData.getBoundaryHierarchy() == null || hierarchyData.getBoundaryHierarchy().isEmpty()) {
            log.error("Boundary hierarchy data is null or empty for type: {}", hierarchyType);
            return;
        }
        
        List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();
        List<String> levelTypes = new ArrayList<>();
        for (BoundaryHierarchyChild hierarchyRelation : hierarchyRelations) {
            levelTypes.add(hierarchyRelation.getBoundaryType());
        }
        
        // Process boundaries based on configuration (this already handles includeAllChildren enrichment)
        List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                configuredBoundaries, codeToEnrichedBoundary, levelTypes);
        
        // If after filtering we have no boundaries, don't add boundary columns
        if (filteredBoundaries.isEmpty()) {
            log.info("No boundaries available after filtering for sheet '{}', skipping boundary column creation", sheetName);
            return;
        }

        // Add boundary columns after schema columns
        // Row 0 contains technical names (hidden), Row 1 contains visible headers
        Row hiddenRow = sheet.getRow(0);
        Row visibleRow = sheet.getRow(1);
        
        if (hiddenRow == null) {
            hiddenRow = sheet.createRow(0);
        }
        if (visibleRow == null) {
            visibleRow = sheet.createRow(1);
        }
        
        // Find the last used column from schema
        int lastSchemaCol = visibleRow.getLastCellNum();
        if (lastSchemaCol < 0) lastSchemaCol = 0;
        
        // Add level, boundary name, and boundary code columns after schema columns
        // Add technical names to hidden row
        hiddenRow.createCell(lastSchemaCol).setCellValue("BOUNDARY_LEVEL");
        hiddenRow.createCell(lastSchemaCol + 1).setCellValue("BOUNDARY_NAME");
        hiddenRow.createCell(lastSchemaCol + 2).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        
        // Create header style for boundary columns (left-aligned to match schema columns)
        CellStyle boundaryHeaderStyle = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, config.getDefaultHeaderColor());
        
        // Add localized headers to visible row with styling
        Cell levelHeaderCell = visibleRow.createCell(lastSchemaCol);
        levelHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_INGESTION_LEVEL_COLUMN", "HCM_INGESTION_LEVEL_COLUMN"));
        levelHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        Cell boundaryHeaderCell = visibleRow.createCell(lastSchemaCol + 1);
        boundaryHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_INGESTION_BOUNDARY_COLUMN", "HCM_INGESTION_BOUNDARY_COLUMN"));
        boundaryHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        Cell boundaryCodeHeaderCell = visibleRow.createCell(lastSchemaCol + 2);
        boundaryCodeHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
        boundaryCodeHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        // Create level and boundary dropdowns with "Boundary (Parent)" format to avoid duplicates
        createLevelAndBoundaryDropdowns(workbook, filteredBoundaries, levelTypes, hierarchyType, localizationMap);
        
        // Add boundary code mapping BEFORE data validations to ensure named range exists
        addBoundaryCodeMapping(workbook, filteredBoundaries, localizationMap);
        
        // Add data validation for level and boundary columns
        addLevelAndBoundaryDataValidations(workbook, sheet, lastSchemaCol, levelTypes, hierarchyType, localizationMap);

        // Column widths & freeze - level, boundary name, and boundary code columns
        sheet.setColumnWidth(lastSchemaCol, 50 * 256); // Level column
        sheet.setColumnWidth(lastSchemaCol + 1, 50 * 256); // Boundary column (wider for "Boundary (Parent)" format)
        sheet.setColumnWidth(lastSchemaCol + 2, 25 * 256); // Boundary code column (narrower)
        sheet.createFreezePane(0, 2); // Freeze after row 2 since schema creator uses row 1 for technical names

        // Unlock cells for user input - level and boundary columns only (boundary code is auto-populated)
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);
        for (int r = 2; r <= config.getExcelRowLimit(); r++) { // Start from row 2 to skip hidden technical row
            Row row = sheet.getRow(r);
            if (row == null)
                row = sheet.createRow(r);
            // Only unlock level and boundary name columns (not boundary code)
            for (int c = lastSchemaCol; c < lastSchemaCol + 2; c++) {
                Cell cell = row.getCell(c);
                if (cell == null)
                    cell = row.createCell(c);
                cell.setCellStyle(unlocked);
            }
        }
        
        // Hide the boundary code column
        sheet.setColumnHidden(lastSchemaCol + 2, true);
    }

    /**
     * Creates level dropdown and level-specific boundary dropdowns with "boundary (parent)" format to avoid duplicates
     */
    private void createLevelAndBoundaryDropdowns(XSSFWorkbook workbook, List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                               List<String> levelTypes, String hierarchyType, Map<String, String> localizationMap) {
        
        // Build levels and boundary options by level
        Set<String> availableLevels = new LinkedHashSet<>();
        Map<String, Set<String>> boundariesByLevel = new HashMap<>();
        
        // Create all levels based on the full hierarchy using hierarchyType_boundaryType pattern
        for (int i = 0; i < levelTypes.size(); i++) {
            String boundaryType = levelTypes.get(i);
            String levelKey = (hierarchyType + "_" + boundaryType).toUpperCase();
            String localizedLevel = localizationMap.getOrDefault(levelKey, levelKey);
            availableLevels.add(localizedLevel);
            boundariesByLevel.put(localizedLevel, new TreeSet<>());
        }
        
        // Debug: Log the boundary data we're processing
        log.info("Processing {} filtered boundaries for dropdown creation", filteredBoundaries.size());
        
        // Process filtered boundaries and group by level with "Boundary (Parent)" format
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i) != null) {
                    String boundaryCode = path.get(i);
                    String boundaryName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                    String boundaryType = levelTypes.get(i);
                    String levelKey = (hierarchyType + "_" + boundaryType).toUpperCase();
                    String localizedLevel = localizationMap.getOrDefault(levelKey, levelKey);
                    
                    // Get parent name if exists  
                    String parentName = "";
                    if (i > 0 && path.get(i-1) != null) {
                        String parentCode = path.get(i-1);
                        parentName = localizationMap.getOrDefault(parentCode, parentCode);
                    }
                    
                    // Create display format: "Boundary (Parent)" or just "Boundary" if no parent
                    String displayText = parentName.isEmpty() ? boundaryName : boundaryName + " (" + parentName + ")";
                    
                    boundariesByLevel.get(localizedLevel).add(displayText);
                }
            }
        }
        
        
        // Create boundaries sheet with level-specific columns
        Sheet boundarySheet = workbook.getSheet("_h_Boundaries_h_");
        if (boundarySheet == null) {
            boundarySheet = workbook.createSheet("_h_Boundaries_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_Boundaries_h_"), true);
        } else {
            // Clear existing content
            if (ExcelUtil.findActualLastRowWithData(boundarySheet) >= 0) {
                for (int i = ExcelUtil.findActualLastRowWithData(boundarySheet); i >= 0; i--) {
                    Row row = boundarySheet.getRow(i);
                    if (row != null) {
                        boundarySheet.removeRow(row);
                    }
                }
            }
        }
        
        // Create header row and populate boundary data by level
        int maxBoundaries = 0;
        int colIndex = 0;
        Row header = null;
        for (String level : availableLevels) {
            header = boundarySheet.getRow(0) != null ? boundarySheet.getRow(0) : boundarySheet.createRow(0);
            header.createCell(colIndex).setCellValue(level);
            
            Set<String> boundaries = boundariesByLevel.get(level);
            List<String> sortedBoundaries = new ArrayList<>(boundaries);
            Collections.sort(sortedBoundaries, String.CASE_INSENSITIVE_ORDER);
            
            int rowNum = 1;
            for (String boundaryOption : sortedBoundaries) {
                Row row = boundarySheet.getRow(rowNum) != null ? boundarySheet.getRow(rowNum) : boundarySheet.createRow(rowNum);
                row.createCell(colIndex).setCellValue(boundaryOption);
                rowNum++;
            }
            maxBoundaries = Math.max(maxBoundaries, sortedBoundaries.size());
            
            // Create named range for this level
            if (!sortedBoundaries.isEmpty()) {
                String levelSanitized = "Level_" + (colIndex + 1);
                Name namedRange = workbook.getName(levelSanitized);
                if (namedRange == null) {
                    namedRange = workbook.createName();
                    namedRange.setNameName(levelSanitized);
                }
                String colLetter = CellReference.convertNumToColString(colIndex);
                namedRange.setRefersToFormula("_h_Boundaries_h_!$" + colLetter + "$2:$" + colLetter + "$" + (sortedBoundaries.size() + 1));
            }
            colIndex++;
        }
        
        // Create named range "Levels" pointing to _h_Boundaries_h_ header row
        // Note: Previously used a separate _h_Levels_h_ sheet, but that was redundant 
        // since _h_Boundaries_h_ header row already contains the same level names
        if (!availableLevels.isEmpty()) {
            Name levelsNamedRange = workbook.getName("Levels");
            if (levelsNamedRange == null) {
                levelsNamedRange = workbook.createName();
                levelsNamedRange.setNameName("Levels");
            }
            String lastColLetter = CellReference.convertNumToColString(availableLevels.size() - 1);
            levelsNamedRange.setRefersToFormula("_h_Boundaries_h_!$A$1:$" + lastColLetter + "$1");
        }
        
        log.info("Created level and boundary dropdowns for {} levels", availableLevels.size());
    }
    
    /**
     * Creates boundary code mapping sheet for automatic code population
     */
    private void addBoundaryCodeMapping(XSSFWorkbook workbook, List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                      Map<String, String> localizationMap) {
        // Check if boundary code mapping sheet already exists (since this method may be called for multiple sheets)
        Sheet boundaryCodeMapSheet = workbook.getSheet("_h_BoundaryCodeMap_h_");
        if (boundaryCodeMapSheet != null) {
            // Sheet already exists, no need to recreate
            return;
        }
        
        // Create hidden sheet for boundary display name -> code mapping
        boundaryCodeMapSheet = workbook.createSheet("_h_BoundaryCodeMap_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_"), true);
        
        // Header row
        Row headerRow = boundaryCodeMapSheet.createRow(0);
        headerRow.createCell(0).setCellValue("BoundaryDisplay");
        headerRow.createCell(1).setCellValue("BoundaryCode");
        
        // Build mapping from display name to boundary code
        Map<String, String> displayToCodeMap = new HashMap<>();
        
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i) != null) {
                    String boundaryCode = path.get(i);
                    String boundaryName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                    
                    // Get parent name if exists for display format
                    String parentName = "";
                    if (i > 0 && path.get(i-1) != null) {
                        String parentCode = path.get(i-1);
                        parentName = localizationMap.getOrDefault(parentCode, parentCode);
                    }
                    
                    // Create display format: "Boundary (Parent)" or just "Boundary" if no parent
                    String displayText = parentName.isEmpty() ? boundaryName : boundaryName + " (" + parentName + ")";
                    
                    // Store mapping
                    displayToCodeMap.put(displayText, boundaryCode);
                }
            }
        }
        
        // Populate mapping sheet
        int rowNum = 1;
        for (Map.Entry<String, String> entry : displayToCodeMap.entrySet()) {
            Row row = boundaryCodeMapSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());   // Display name
            row.createCell(1).setCellValue(entry.getValue()); // Code
        }
        
        // Create named range for the mapping
        if (!displayToCodeMap.isEmpty()) {
            Name boundaryCodeMapRange = workbook.createName();
            boundaryCodeMapRange.setNameName("BoundaryCodeMap");
            boundaryCodeMapRange.setRefersToFormula("_h_BoundaryCodeMap_h_!$A$1:$B$" + (displayToCodeMap.size() + 1));
        }
    }

    /**
     * Adds data validations for level and boundary columns
     */
    private void addLevelAndBoundaryDataValidations(XSSFWorkbook workbook, Sheet sheet, int lastSchemaCol, 
                                                  List<String> levelTypes, String hierarchyType, Map<String, String> localizationMap) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Add data validation for rows starting from row 2
        for (int rowIndex = 2; rowIndex <= config.getExcelRowLimit(); rowIndex++) {
            // Level dropdown uses "Levels" named range
            DataValidationConstraint levelConstraint = dvHelper.createFormulaListConstraint("Levels");
            CellRangeAddressList levelAddr = new CellRangeAddressList(rowIndex, rowIndex, lastSchemaCol, lastSchemaCol);
            DataValidation levelValidation = dvHelper.createValidation(levelConstraint, levelAddr);
            levelValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            levelValidation.setShowErrorBox(true);
            levelValidation.createErrorBox(
                LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_LEVEL", "Invalid Level"),
                LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_LEVEL_MESSAGE", "Please select a valid level from the dropdown list.")
            );
            levelValidation.setShowPromptBox(false);
            sheet.addValidationData(levelValidation);
            
            // Boundary dropdown: depends on level selected, shows "Boundary (Parent)" format 
            String levelCellRef = CellReference.convertNumToColString(lastSchemaCol) + (rowIndex + 1);
            String boundaryFormula = "IF(" + levelCellRef + "=\"\", \"\", INDIRECT(\"Level_\"&MATCH(" + levelCellRef + ", Levels, 0)))";
            DataValidationConstraint boundaryConstraint = dvHelper.createFormulaListConstraint(boundaryFormula);
            CellRangeAddressList boundaryAddr = new CellRangeAddressList(rowIndex, rowIndex, lastSchemaCol + 1, lastSchemaCol + 1);
            DataValidation boundaryValidation = dvHelper.createValidation(boundaryConstraint, boundaryAddr);
            boundaryValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            boundaryValidation.setShowErrorBox(true);
            boundaryValidation.createErrorBox(
                LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_BOUNDARY", "Invalid Boundary"),
                LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_BOUNDARY_MESSAGE", "Please select a valid boundary from the dropdown list.")
            );
            boundaryValidation.setShowPromptBox(false);
            sheet.addValidationData(boundaryValidation);
            
            // Add VLOOKUP formula to boundary code column for automatic population
            String boundaryNameCellRef = CellReference.convertNumToColString(lastSchemaCol + 1) + (rowIndex + 1);
            String vlookupFormula = "IF(" + boundaryNameCellRef + "=\"\", \"\", VLOOKUP(" + boundaryNameCellRef + ", BoundaryCodeMap, 2, FALSE))";
            
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);
            Cell boundaryCodeCell = row.createCell(lastSchemaCol + 2);
            boundaryCodeCell.setCellFormula(vlookupFormula);
        }
    }

}