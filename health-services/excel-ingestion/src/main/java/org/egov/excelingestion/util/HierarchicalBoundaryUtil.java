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
            
            // Get boundary type for this level from hierarchyRelations
            String boundaryType = hierarchyRelations.get(levelIndex).getBoundaryType();
            // Create column name using hierarchyType + "_" + boundaryType pattern (same as boundary sheet)
            String columnName = (hierarchyType + "_" + boundaryType).toUpperCase();
            
            // Add technical name to hidden row
            hiddenRow.createCell(colIndex).setCellValue(columnName);
            
            // Add localized header using the same pattern as boundary sheet
            Cell headerCell = visibleRow.createCell(colIndex);
            String localizedHeaderName = localizationMap.getOrDefault(columnName, columnName);
            headerCell.setCellValue(localizedHeaderName);
            headerCell.setCellStyle(boundaryHeaderStyle);
        }
        
        // Add final boundary code column (hidden)
        int codeColumnIndex = lastSchemaCol + numCascadingColumns;
        hiddenRow.createCell(codeColumnIndex).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        Cell boundaryCodeHeaderCell = visibleRow.createCell(codeColumnIndex);
        boundaryCodeHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
        boundaryCodeHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        // Create cascading boundary hierarchy sheet and get mapping result
        ParentChildrenMapping mappingResult = createCascadingBoundaryHierarchySheet(workbook, filteredBoundaries, levelTypes, localizationMap);
        
        // Create boundary name to sanitized name lookup
        createBoundaryNameLookupSheet(workbook, mappingResult.codeToDisplayNameMap);
        
        // Add cascading data validations
        addCascadingBoundaryValidations(workbook, sheet, lastSchemaCol, numCascadingColumns, filteredBoundaries, mappingResult.codeToDisplayNameMap);
        
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
     * Handles name clashes by using "boundary (parent)" format
     */
    private ParentChildrenMapping createCascadingBoundaryHierarchySheet(XSSFWorkbook workbook, 
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
        
        // Build parent-children mapping with disambiguation for name clashes
        ParentChildrenMapping mappingResult = buildParentChildrenMappingWithDisambiguation(boundaries, levelTypes, localizationMap);
        Map<String, Set<String>> parentChildrenMap = mappingResult.parentChildrenMap;
        Map<String, String> codeToDisplayNameMap = mappingResult.codeToDisplayNameMap;
        
        // Create columns for each boundary starting from 2nd level
        Map<String, Integer> boundaryColumnMap = new HashMap<>();
        int colIndex = 0;
        
        // Process only boundaries that have children to create columns
        for (Map.Entry<String, Set<String>> entry : parentChildrenMap.entrySet()) {
            String parentCode = entry.getKey();
            Set<String> children = entry.getValue();
            String parentDisplayName = codeToDisplayNameMap.get(parentCode);
            
            // Only create column for boundaries that have children and aren't already processed
            if (!children.isEmpty() && !boundaryColumnMap.containsKey(parentDisplayName)) {
                boundaryColumnMap.put(parentDisplayName, colIndex);
                
                // Create header row with disambiguated boundary name
                Row headerRow = hierarchySheet.getRow(0);
                if (headerRow == null) {
                    headerRow = hierarchySheet.createRow(0);
                }
                headerRow.createCell(colIndex).setCellValue(parentDisplayName);
                
                // Add children of this boundary (also use disambiguated names)
                int rowIndex = 1;
                for (String childDisplayName : children) {
                    Row row = hierarchySheet.getRow(rowIndex);
                    if (row == null) {
                        row = hierarchySheet.createRow(rowIndex);
                    }
                    row.createCell(colIndex).setCellValue(childDisplayName);
                    rowIndex++;
                }
                
                // Create named range for this boundary's children
                String sanitizedName = sanitizeName(parentDisplayName);
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
        
        // Create Level2Boundaries named range for the first dropdown with disambiguation
        createLevel2BoundariesRange(workbook, boundaries, codeToDisplayNameMap);
        
        log.info("Created cascading boundary hierarchy sheet with {} boundary columns", boundaryColumnMap.size());
        
        return mappingResult;
    }
    
    /**
     * Creates a named range for all 2nd level boundaries (for the first dropdown)
     */
    private void createLevel2BoundariesRange(XSSFWorkbook workbook, 
            List<BoundaryUtil.BoundaryRowData> boundaries,
            Map<String, String> codeToDisplayNameMap) {
        
        // Extract unique 2nd level boundaries using disambiguated names
        Set<String> level2Boundaries = new TreeSet<>();
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            if (path.size() > 1 && path.get(1) != null) {
                String boundaryCode = path.get(1);
                String displayName = codeToDisplayNameMap.get(boundaryCode);
                if (displayName != null) {
                    level2Boundaries.add(displayName);
                }
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
     * Builds a mapping of parent boundary code to its children names with disambiguation
     * Handles name clashes by using "boundary (parent)" format
     */
    private ParentChildrenMapping buildParentChildrenMappingWithDisambiguation(
            List<BoundaryUtil.BoundaryRowData> boundaries,
            List<String> levelTypes,
            Map<String, String> localizationMap) {
        
        // First, collect all boundary codes with their paths for disambiguation
        Map<String, List<String>> codeToPathMap = new HashMap<>();
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i) != null) {
                    codeToPathMap.put(path.get(i), path);
                }
            }
        }
        
        // Create disambiguated display names
        Map<String, String> codeToDisplayNameMap = createDisambiguatedDisplayNames(codeToPathMap, localizationMap);
        
        // Build parent-children mapping using disambiguated names
        Map<String, Set<String>> parentChildrenMap = new HashMap<>();
        
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            
            // For each level, map parent to children
            for (int level = 0; level < path.size() - 1; level++) {
                if (path.get(level) != null && path.get(level + 1) != null) {
                    String parentCode = path.get(level);
                    String childCode = path.get(level + 1);
                    String childDisplayName = codeToDisplayNameMap.get(childCode);
                    
                    parentChildrenMap.computeIfAbsent(parentCode, k -> new TreeSet<>()).add(childDisplayName);
                }
            }
        }
        
        return new ParentChildrenMapping(parentChildrenMap, codeToDisplayNameMap);
    }
    
    /**
     * Creates disambiguated display names for boundaries to handle name clashes
     */
    private Map<String, String> createDisambiguatedDisplayNames(
            Map<String, List<String>> codeToPathMap, 
            Map<String, String> localizationMap) {
        
        Map<String, String> codeToDisplayNameMap = new HashMap<>();
        Map<String, List<String>> nameToCodesMap = new HashMap<>();
        
        // Group boundary codes by their localized names
        for (Map.Entry<String, List<String>> entry : codeToPathMap.entrySet()) {
            String code = entry.getKey();
            String localizedName = localizationMap.getOrDefault(code, code);
            
            nameToCodesMap.computeIfAbsent(localizedName, k -> new ArrayList<>()).add(code);
        }
        
        // Create display names with disambiguation for duplicates
        for (Map.Entry<String, List<String>> entry : nameToCodesMap.entrySet()) {
            String localizedName = entry.getKey();
            List<String> codes = entry.getValue();
            
            if (codes.size() == 1) {
                // No clash, use simple name
                codeToDisplayNameMap.put(codes.get(0), localizedName);
            } else {
                // Name clash, disambiguate using parent names
                for (String code : codes) {
                    List<String> path = codeToPathMap.get(code);
                    String displayName = createDisambiguatedName(code, path, localizationMap);
                    codeToDisplayNameMap.put(code, displayName);
                }
            }
        }
        
        return codeToDisplayNameMap;
    }
    
    /**
     * Creates a disambiguated name using "boundary (parent)" format
     */
    private String createDisambiguatedName(String code, List<String> path, Map<String, String> localizationMap) {
        String boundaryName = localizationMap.getOrDefault(code, code);
        
        // Find the index of this boundary in the path
        int boundaryIndex = -1;
        for (int i = 0; i < path.size(); i++) {
            if (code.equals(path.get(i))) {
                boundaryIndex = i;
                break;
            }
        }
        
        // If we have a parent, use "boundary (parent)" format
        if (boundaryIndex > 0) {
            String parentCode = path.get(boundaryIndex - 1);
            String parentName = localizationMap.getOrDefault(parentCode, parentCode);
            return boundaryName + " (" + parentName + ")";
        }
        
        // No parent, use simple name
        return boundaryName;
    }
    
    /**
     * Helper class to hold parent-children mapping results
     */
    private static class ParentChildrenMapping {
        final Map<String, Set<String>> parentChildrenMap;
        final Map<String, String> codeToDisplayNameMap;
        
        ParentChildrenMapping(Map<String, Set<String>> parentChildrenMap, Map<String, String> codeToDisplayNameMap) {
            this.parentChildrenMap = parentChildrenMap;
            this.codeToDisplayNameMap = codeToDisplayNameMap;
        }
    }
    
    /**
     * Sanitizes boundary name for use as Excel named range
     * Must match Excel naming rules and be consistent with INDIRECT formula
     */
    private String sanitizeName(String name) {
        // Replace spaces and special characters with underscores, ensure no consecutive underscores
        String sanitized = name.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_{2,}", "_");
        
        // Remove leading/trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        
        // Ensure it starts with a letter (Excel requirement)
        if (!sanitized.isEmpty() && !Character.isLetter(sanitized.charAt(0))) {
            sanitized = "B_" + sanitized;
        }
        
        // Ensure it's not empty
        if (sanitized.isEmpty()) {
            sanitized = "Boundary";
        }
        
        return sanitized;
    }
    
    /**
     * Creates a lookup sheet that maps boundary display names to their sanitized named range names
     */
    private void createBoundaryNameLookupSheet(XSSFWorkbook workbook, Map<String, String> codeToDisplayNameMap) {
        // Create hidden lookup sheet
        Sheet lookupSheet = workbook.getSheet("_h_BoundaryLookup_h_");
        if (lookupSheet == null) {
            lookupSheet = workbook.createSheet("_h_BoundaryLookup_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryLookup_h_"), true);
        } else {
            // Clear existing content
            for (int i = lookupSheet.getLastRowNum(); i >= 0; i--) {
                Row row = lookupSheet.getRow(i);
                if (row != null) {
                    lookupSheet.removeRow(row);
                }
            }
        }
        
        // Create header
        Row headerRow = lookupSheet.createRow(0);
        headerRow.createCell(0).setCellValue("DisplayName");
        headerRow.createCell(1).setCellValue("SanitizedName");
        
        // Populate lookup data
        int rowIndex = 1;
        Set<String> uniqueDisplayNames = new HashSet<>(codeToDisplayNameMap.values());
        for (String displayName : uniqueDisplayNames) {
            String sanitizedName = sanitizeName(displayName);
            Row row = lookupSheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(displayName);
            row.createCell(1).setCellValue(sanitizedName);
        }
        
        // Create named range for lookup
        if (rowIndex > 1) {
            Name lookupRange = workbook.getName("BoundaryLookup");
            if (lookupRange == null) {
                lookupRange = workbook.createName();
                lookupRange.setNameName("BoundaryLookup");
            }
            lookupRange.setRefersToFormula("_h_BoundaryLookup_h_!$A$1:$B$" + (rowIndex - 1));
        }
    }
    
    /**
     * Creates a mapping sheet for boundary name to code conversion
     */
    private void createBoundaryCodeMappingSheet(XSSFWorkbook workbook,
            List<BoundaryUtil.BoundaryRowData> boundaries,
            Map<String, String> codeToDisplayNameMap) {
        
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
        
        // Build mapping from disambiguated display name to code
        Map<String, String> displayNameToCodeMap = new HashMap<>();
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (String boundaryCode : path) {
                if (boundaryCode != null) {
                    String displayName = codeToDisplayNameMap.get(boundaryCode);
                    if (displayName != null) {
                        displayNameToCodeMap.put(displayName, boundaryCode);
                    }
                }
            }
        }
        
        // Add mapping data
        Row headerRow = codeMapSheet.createRow(0);
        headerRow.createCell(0).setCellValue("BoundaryName");
        headerRow.createCell(1).setCellValue("BoundaryCode");
        
        int rowNum = 1;
        for (Map.Entry<String, String> entry : displayNameToCodeMap.entrySet()) {
            Row row = codeMapSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());   // Disambiguated display name
            row.createCell(1).setCellValue(entry.getValue()); // Code
        }
        
        // Create named range for the mapping
        if (!displayNameToCodeMap.isEmpty()) {
            Name codeMapRange = workbook.getName("BoundaryCodeMap");
            if (codeMapRange == null) {
                codeMapRange = workbook.createName();
                codeMapRange.setNameName("BoundaryCodeMap");
            }
            codeMapRange.setRefersToFormula("_h_BoundaryCodeMap_h_!$A$1:$B$" + (displayNameToCodeMap.size() + 1));
        }
    }
    
    /**
     * Adds cascading data validations for all boundary columns
     */
    private void addCascadingBoundaryValidations(XSSFWorkbook workbook, Sheet sheet, 
            int startColumnIndex, int numColumns, 
            List<BoundaryUtil.BoundaryRowData> boundaries,
            Map<String, String> codeToDisplayNameMap) {
        
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Create boundary code mapping sheet
        createBoundaryCodeMappingSheet(workbook, boundaries, codeToDisplayNameMap);
        
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
                    // Use VLOOKUP to get sanitized name from lookup table, then INDIRECT
                    String formula = "IF(" + prevColRef + "=\"\",\"\",INDIRECT(VLOOKUP(" + prevColRef + ",BoundaryLookup,2,FALSE)))";
                    
                    DataValidationConstraint constraint = dvHelper.createFormulaListConstraint(formula);
                    CellRangeAddressList addr = new CellRangeAddressList(rowIndex, rowIndex, actualColIndex, actualColIndex);
                    DataValidation validation = dvHelper.createValidation(constraint, addr);
                    validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
                    validation.setShowErrorBox(true);
                    validation.createErrorBox("Invalid Selection", "Please select a valid child boundary.");
                    sheet.addValidationData(validation);
                }
            }
            
            // Add formula to boundary code column for the last selected level
            int codeColumnIndex = startColumnIndex + numColumns;
            
            // Build simple nested IF statements from rightmost to leftmost
            // This checks the last column first, then works backwards
            StringBuilder formula = new StringBuilder();
            
            for (int colIdx = numColumns - 1; colIdx >= 0; colIdx--) {
                int actualColIndex = startColumnIndex + colIdx;
                String colRef = CellReference.convertNumToColString(actualColIndex) + (rowIndex + 1);
                
                if (colIdx == numColumns - 1) {
                    // Start with the rightmost column
                    formula.append("IF(").append(colRef).append("<>\"\",VLOOKUP(").append(colRef).append(",BoundaryCodeMap,2,FALSE),");
                } else if (colIdx == 0) {
                    // End with the leftmost column and close all IFs
                    formula.append("IF(").append(colRef).append("<>\"\",VLOOKUP(").append(colRef).append(",BoundaryCodeMap,2,FALSE),\"\")");
                    // Close all the IF statements
                    for (int j = 0; j < numColumns - 1; j++) {
                        formula.append(")");
                    }
                } else {
                    // Middle columns
                    formula.append("IF(").append(colRef).append("<>\"\",VLOOKUP(").append(colRef).append(",BoundaryCodeMap,2,FALSE),");
                }
            }
            
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);
            Cell boundaryCodeCell = row.createCell(codeColumnIndex);
            boundaryCodeCell.setCellFormula(formula.toString());
        }
    }
}