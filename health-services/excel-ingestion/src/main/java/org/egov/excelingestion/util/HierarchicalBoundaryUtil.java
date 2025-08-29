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
 * Utility for creating cascading boundary dropdowns starting from 2nd level
 * Creates a hidden sheet with boundary hierarchy and cascading dropdowns
 */
@Component
@Slf4j
public class HierarchicalBoundaryUtil {

    private final ExcelIngestionConfig config;
    private final BoundaryService boundaryService;
    private final BoundaryUtil boundaryUtil;
    private final ExcelStyleHelper excelStyleHelper;

    public HierarchicalBoundaryUtil(ExcelIngestionConfig config, BoundaryService boundaryService,
                                   BoundaryUtil boundaryUtil, ExcelStyleHelper excelStyleHelper) {
        this.config = config;
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
        this.excelStyleHelper = excelStyleHelper;
    }

    /**
     * Adds cascading boundary dropdown columns to an existing sheet
     * Creates multiple columns starting from 2nd level with cascading dropdowns
     * 
     * @param workbook Excel workbook
     * @param sheetName Name of the sheet to add columns to
     * @param localizationMap Localization map for headers and values
     * @param configuredBoundaries List of configured boundaries from additionalDetails
     * @param hierarchyType Boundary hierarchy type
     * @param tenantId Tenant ID
     * @param requestInfo Request info for API calls
     */
    public void addHierarchicalBoundaryColumn(XSSFWorkbook workbook, String sheetName, Map<String, String> localizationMap,
                                             List<Boundary> configuredBoundaries, String hierarchyType, 
                                             String tenantId, RequestInfo requestInfo) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            log.warn("Sheet '{}' not found, cannot add hierarchical boundary column", sheetName);
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
            log.warn("Hierarchy has less than 2 levels, cannot create cascading boundary dropdowns");
            return;
        }
        
        // Process boundaries to get all data
        List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                configuredBoundaries, codeToEnrichedBoundary, levelTypes);
        
        if (filteredBoundaries.isEmpty()) {
            log.info("No boundaries available for sheet '{}', skipping boundary column creation", sheetName);
            return;
        }
        
        // Calculate number of cascading columns (from 2nd level to last level)
        int numCascadingColumns = levelTypes.size() - 1; // Exclude 1st level
        
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
        
        // Create header style
        CellStyle boundaryHeaderStyle = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, config.getDefaultHeaderColor());
        
        // Add cascading boundary columns (from 2nd level to last level)
        for (int i = 0; i < numCascadingColumns; i++) {
            int levelIndex = i + 1; // Start from 2nd level (index 1)
            int colIndex = lastSchemaCol + i;
            
            // Add technical name to hidden row
            hiddenRow.createCell(colIndex).setCellValue("BOUNDARY_LEVEL_" + (levelIndex + 1));
            
            // Add localized header
            Cell headerCell = visibleRow.createCell(colIndex);
            String levelKey = "HCM_CAMP_CONF_LEVEL_" + (levelIndex + 1);
            String levelName = localizationMap.getOrDefault(levelKey, "Level " + (levelIndex + 1));
            headerCell.setCellValue(levelName);
            headerCell.setCellStyle(boundaryHeaderStyle);
        }
        
        // Add final boundary code column (hidden)
        int codeColumnIndex = lastSchemaCol + numCascadingColumns;
        hiddenRow.createCell(codeColumnIndex).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        Cell boundaryCodeHeaderCell = visibleRow.createCell(codeColumnIndex);
        boundaryCodeHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
        boundaryCodeHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        // Create cascading boundary hierarchy sheet
        createCascadingBoundaryHierarchySheet(workbook, filteredBoundaries, levelTypes, localizationMap);
        
        // Add cascading data validations
        addCascadingBoundaryValidations(workbook, sheet, lastSchemaCol, numCascadingColumns, filteredBoundaries, localizationMap);
        
        // Set column widths and styling
        for (int i = 0; i < numCascadingColumns; i++) {
            sheet.setColumnWidth(lastSchemaCol + i, 50 * 256);
        }
        sheet.setColumnWidth(codeColumnIndex, 25 * 256); // Boundary code column
        sheet.createFreezePane(0, 2); // Freeze header rows
        
        // Hide the boundary code column
        sheet.setColumnHidden(codeColumnIndex, true);
        
        // Unlock cells for user input (exclude the boundary code column)
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);
        for (int r = 2; r <= config.getExcelRowLimit(); r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                row = sheet.createRow(r);
            
            // Unlock cascading boundary columns
            for (int i = 0; i < numCascadingColumns; i++) {
                Cell cell = row.getCell(lastSchemaCol + i);
                if (cell == null)
                    cell = row.createCell(lastSchemaCol + i);
                cell.setCellStyle(unlocked);
            }
        }
        
        log.info("Added {} cascading boundary dropdown columns", numCascadingColumns);
    }
    
    /**
     * Creates a hidden sheet with cascading boundary hierarchy
     * Each column represents a boundary level with all its children listed below
     */
    private void createCascadingBoundaryHierarchySheet(XSSFWorkbook workbook, 
            List<BoundaryUtil.BoundaryRowData> boundaries,
            List<String> levelTypes,
            Map<String, String> localizationMap) {
        
        // Create or get the hidden hierarchy sheet
        Sheet hierarchySheet = workbook.getSheet("_h_CascadingBoundaries_h_");
        if (hierarchySheet == null) {
            hierarchySheet = workbook.createSheet("_h_CascadingBoundaries_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_CascadingBoundaries_h_"), true);
        } else {
            // Clear existing content
            for (int i = hierarchySheet.getLastRowNum(); i >= 0; i--) {
                Row row = hierarchySheet.getRow(i);
                if (row != null) {
                    hierarchySheet.removeRow(row);
                }
            }
        }
        
        // Build parent-children mapping for each level
        Map<String, Set<String>> parentChildrenMap = buildParentChildrenMapping(boundaries, levelTypes, localizationMap);
        
        // Create columns for each boundary starting from 2nd level
        Map<String, Integer> boundaryColumnMap = new HashMap<>();
        int colIndex = 0;
        
        // Process only boundaries that have children to create columns
        for (Map.Entry<String, Set<String>> entry : parentChildrenMap.entrySet()) {
            String parentCode = entry.getKey();
            Set<String> children = entry.getValue();
            String parentName = localizationMap.getOrDefault(parentCode, parentCode);
            
            // Only create column for boundaries that have children and aren't already processed
            if (!children.isEmpty() && !boundaryColumnMap.containsKey(parentName)) {
                boundaryColumnMap.put(parentName, colIndex);
                
                // Create header row with boundary name
                Row headerRow = hierarchySheet.getRow(0);
                if (headerRow == null) {
                    headerRow = hierarchySheet.createRow(0);
                }
                headerRow.createCell(colIndex).setCellValue(parentName);
                
                // Add children of this boundary
                int rowIndex = 1;
                for (String child : children) {
                    Row row = hierarchySheet.getRow(rowIndex);
                    if (row == null) {
                        row = hierarchySheet.createRow(rowIndex);
                    }
                    row.createCell(colIndex).setCellValue(child);
                    rowIndex++;
                }
                
                // Create named range for this boundary's children
                String sanitizedName = sanitizeName(parentName);
                Name namedRange = workbook.getName(sanitizedName);
                if (namedRange == null) {
                    namedRange = workbook.createName();
                    namedRange.setNameName(sanitizedName);
                }
                String colLetter = CellReference.convertNumToColString(colIndex);
                namedRange.setRefersToFormula("_h_CascadingBoundaries_h_!$" + colLetter + "$2:$" + colLetter + "$" + (children.size() + 1));
                
                colIndex++;
            }
        }
        
        // Create Level2Boundaries named range for the first dropdown
        createLevel2BoundariesRange(workbook, boundaries, localizationMap);
        
        log.info("Created cascading boundary hierarchy sheet with {} boundary columns", boundaryColumnMap.size());
    }
    
    /**
     * Creates a named range for all 2nd level boundaries (for the first dropdown)
     */
    private void createLevel2BoundariesRange(XSSFWorkbook workbook, 
            List<BoundaryUtil.BoundaryRowData> boundaries,
            Map<String, String> localizationMap) {
        
        // Extract unique 2nd level boundaries
        Set<String> level2Boundaries = new TreeSet<>();
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            if (path.size() > 1 && path.get(1) != null) {
                String boundaryCode = path.get(1);
                String boundaryName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                level2Boundaries.add(boundaryName);
            }
        }
        
        // Create a dedicated sheet for Level 2 boundaries
        Sheet level2Sheet = workbook.getSheet("_h_Level2Boundaries_h_");
        if (level2Sheet == null) {
            level2Sheet = workbook.createSheet("_h_Level2Boundaries_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_Level2Boundaries_h_"), true);
        } else {
            // Clear existing content
            for (int i = level2Sheet.getLastRowNum(); i >= 0; i--) {
                Row row = level2Sheet.getRow(i);
                if (row != null) {
                    level2Sheet.removeRow(row);
                }
            }
        }
        
        // Add level 2 boundaries to the sheet
        int rowIndex = 0;
        for (String boundaryName : level2Boundaries) {
            Row row = level2Sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(boundaryName);
        }
        
        // Create named range
        if (!level2Boundaries.isEmpty()) {
            Name level2Range = workbook.getName("Level2Boundaries");
            if (level2Range == null) {
                level2Range = workbook.createName();
                level2Range.setNameName("Level2Boundaries");
            }
            level2Range.setRefersToFormula("_h_Level2Boundaries_h_!$A$1:$A$" + level2Boundaries.size());
        }
    }
    
    /**
     * Builds a mapping of parent boundary code to its children names
     */
    private Map<String, Set<String>> buildParentChildrenMapping(
            List<BoundaryUtil.BoundaryRowData> boundaries,
            List<String> levelTypes,
            Map<String, String> localizationMap) {
        
        Map<String, Set<String>> parentChildrenMap = new HashMap<>();
        
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            
            // For each level, map parent to children
            for (int level = 0; level < path.size() - 1; level++) {
                if (path.get(level) != null && path.get(level + 1) != null) {
                    String parentCode = path.get(level);
                    String childCode = path.get(level + 1);
                    String childName = localizationMap.getOrDefault(childCode, childCode);
                    
                    parentChildrenMap.computeIfAbsent(parentCode, k -> new TreeSet<>()).add(childName);
                }
            }
        }
        
        return parentChildrenMap;
    }
    
    /**
     * Sanitizes boundary name for use as Excel named range
     */
    private String sanitizeName(String name) {
        // Replace spaces and special characters with underscores
        return name.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_{2,}", "_");
    }
    
    /**
     * Creates a mapping sheet for boundary name to code conversion
     */
    private void createBoundaryCodeMappingSheet(XSSFWorkbook workbook,
            List<BoundaryUtil.BoundaryRowData> boundaries,
            Map<String, String> localizationMap) {
        
        // Create code mapping sheet
        Sheet codeMapSheet = workbook.getSheet("_h_BoundaryCodeMap_h_");
        if (codeMapSheet == null) {
            codeMapSheet = workbook.createSheet("_h_BoundaryCodeMap_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_"), true);
        } else {
            // Clear existing content
            for (int i = codeMapSheet.getLastRowNum(); i >= 0; i--) {
                Row row = codeMapSheet.getRow(i);
                if (row != null) {
                    codeMapSheet.removeRow(row);
                }
            }
        }
        
        // Build mapping from boundary name to code
        Map<String, String> nameToCodeMap = new HashMap<>();
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (String boundaryCode : path) {
                if (boundaryCode != null) {
                    String boundaryName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                    nameToCodeMap.put(boundaryName, boundaryCode);
                }
            }
        }
        
        // Add mapping data
        Row headerRow = codeMapSheet.createRow(0);
        headerRow.createCell(0).setCellValue("BoundaryName");
        headerRow.createCell(1).setCellValue("BoundaryCode");
        
        int rowNum = 1;
        for (Map.Entry<String, String> entry : nameToCodeMap.entrySet()) {
            Row row = codeMapSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        
        // Create named range for the mapping
        if (!nameToCodeMap.isEmpty()) {
            Name codeMapRange = workbook.getName("BoundaryCodeMap");
            if (codeMapRange == null) {
                codeMapRange = workbook.createName();
                codeMapRange.setNameName("BoundaryCodeMap");
            }
            codeMapRange.setRefersToFormula("_h_BoundaryCodeMap_h_!$A$1:$B$" + (nameToCodeMap.size() + 1));
        }
    }
    
    /**
     * Adds cascading data validations for all boundary columns
     */
    private void addCascadingBoundaryValidations(XSSFWorkbook workbook, Sheet sheet, 
            int startColumnIndex, int numColumns, 
            List<BoundaryUtil.BoundaryRowData> boundaries,
            Map<String, String> localizationMap) {
        
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Create boundary code mapping sheet
        createBoundaryCodeMappingSheet(workbook, boundaries, localizationMap);
        
        // Add data validation for each row
        for (int rowIndex = 2; rowIndex <= config.getExcelRowLimit(); rowIndex++) {
            
            // Add cascading validations for each boundary column
            for (int colIdx = 0; colIdx < numColumns; colIdx++) {
                int actualColIndex = startColumnIndex + colIdx;
                
                if (colIdx == 0) {
                    // First column: get all 2nd level boundaries
                    // Create a named range for all 2nd level boundaries
                    String formula = "INDIRECT(\"Level2Boundaries\")";
                    
                    DataValidationConstraint constraint = dvHelper.createFormulaListConstraint(formula);
                    CellRangeAddressList addr = new CellRangeAddressList(rowIndex, rowIndex, actualColIndex, actualColIndex);
                    DataValidation validation = dvHelper.createValidation(constraint, addr);
                    validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                    validation.setShowErrorBox(true);
                    validation.createErrorBox("Invalid Selection", "Please select a valid boundary from the dropdown list.");
                    sheet.addValidationData(validation);
                    
                } else {
                    // Subsequent columns: use INDIRECT to get children of previous selection
                    String prevColRef = CellReference.convertNumToColString(actualColIndex - 1) + (rowIndex + 1);
                    String formula = "IF(" + prevColRef + "=\"\",\"\",INDIRECT(SUBSTITUTE(" + prevColRef + ",\" \",\"_\")))";
                    
                    DataValidationConstraint constraint = dvHelper.createFormulaListConstraint(formula);
                    CellRangeAddressList addr = new CellRangeAddressList(rowIndex, rowIndex, actualColIndex, actualColIndex);
                    DataValidation validation = dvHelper.createValidation(constraint, addr);
                    validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
                    validation.setShowErrorBox(true);
                    validation.createErrorBox("Invalid Selection", "Please select a valid child boundary.");
                    sheet.addValidationData(validation);
                }
            }
            
            // Add VLOOKUP formula to boundary code column (last selected boundary)
            int codeColumnIndex = startColumnIndex + numColumns;
            String lastBoundaryColRef = CellReference.convertNumToColString(startColumnIndex + numColumns - 1) + (rowIndex + 1);
            String vlookupFormula = "IF(" + lastBoundaryColRef + "=\"\", \"\", VLOOKUP(" + lastBoundaryColRef + ", BoundaryCodeMap, 2, FALSE))";
            
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);
            Cell boundaryCodeCell = row.createCell(codeColumnIndex);
            boundaryCodeCell.setCellFormula(vlookupFormula);
        }
    }
}