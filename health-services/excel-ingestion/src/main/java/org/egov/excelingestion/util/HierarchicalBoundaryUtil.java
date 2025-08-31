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
        
        
        // Get second level boundaries for hardcoded dropdown
        Set<String> level2Boundaries = new LinkedHashSet<>();
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> path = boundary.getBoundaryPath();
            // Get level 2 boundaries (index 1)
            if (path.size() > 1 && path.get(1) != null) {
                String displayName = localizationMap.getOrDefault(path.get(1), path.get(1));
                level2Boundaries.add(displayName);
            }
        }
        
        // Create cascading boundary hierarchy sheet and get mapping result
        ParentChildrenMapping mappingResult = createCascadingBoundaryHierarchySheet(workbook, filteredBoundaries, levelTypes, localizationMap);
        
        // Add cascading data validations
        addCascadingBoundaryValidations(workbook, sheet, lastSchemaCol, numCascadingColumns, 
                                       new ArrayList<>(level2Boundaries), mappingResult, localizationMap);
        
        // Set column widths and styling
        for (int i = 0; i < numCascadingColumns; i++) {
            sheet.setColumnWidth(lastSchemaCol + i, 50 * 256);
        }
        sheet.createFreezePane(0, 2); // Freeze header rows
        
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
     * Creates a hidden sheet with cascading boundary hierarchy using exact logic from example
     * Single hidden lookup sheet with parent#child structure
     */
    private ParentChildrenMapping createCascadingBoundaryHierarchySheet(XSSFWorkbook workbook, 
            List<BoundaryUtil.BoundaryRowData> boundaries,
            List<String> levelTypes,
            Map<String, String> localizationMap) {
        
        // Create or get the hidden lookup sheet
        Sheet lookupSheet = workbook.getSheet("_h_SimpleLookup_h_");
        if (lookupSheet == null) {
            lookupSheet = workbook.createSheet("_h_SimpleLookup_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_SimpleLookup_h_"), true);
        } else {
            // Clear existing content
            for (int i = lookupSheet.getLastRowNum(); i >= 0; i--) {
                Row row = lookupSheet.getRow(i);
                if (row != null) {
                    lookupSheet.removeRow(row);
                }
            }
        }
        
        // Build parent-children mapping
        Map<String, Set<String>> parentChildrenMap = new HashMap<>();
        Map<String, String> codeToDisplayNameMap = new HashMap<>();
        
        // First pass: collect all boundary codes with their display names
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (String code : path) {
                if (code != null) {
                    String displayName = localizationMap.getOrDefault(code, code);
                    codeToDisplayNameMap.put(code, displayName);
                }
            }
        }
        
        // Second pass: build parent-children relationships starting from level 1 (second level)
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            
            // Build hierarchical keys for each level starting from level 1
            for (int level = 1; level < path.size() - 1; level++) {
                if (path.get(level) != null && path.get(level + 1) != null) {
                    // Build key based on hierarchy path from level 1 onwards
                    StringBuilder keyBuilder = new StringBuilder();
                    for (int i = 1; i <= level; i++) {
                        if (i > 1) keyBuilder.append("#");
                        String displayName = codeToDisplayNameMap.get(path.get(i));
                        keyBuilder.append(displayName);
                    }
                    String key = keyBuilder.toString();
                    
                    String childDisplayName = codeToDisplayNameMap.get(path.get(level + 1));
                    parentChildrenMap.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(childDisplayName);
                }
            }
        }
        
        // Populate lookup sheet with key-value pairs (comma-separated children)
        int rowNum = 0;
        for (Map.Entry<String, Set<String>> entry : parentChildrenMap.entrySet()) {
            String key = entry.getKey();
            Set<String> children = entry.getValue();
            
            Row row = lookupSheet.createRow(rowNum++);
            // Key in column A
            row.createCell(0).setCellValue(key);
            
            // All children in column B as comma-separated values
            String childrenList = String.join(",", children);
            row.createCell(1).setCellValue(childrenList);
        }
        
        log.info("Created cascading boundary lookup sheet with {} entries", parentChildrenMap.size());
        
        return new ParentChildrenMapping(parentChildrenMap, codeToDisplayNameMap);
    }
    
    /**
     * Sanitizes boundary name for use in keys and lookups
     * Replaces ALL non-alphanumeric characters with underscores
     */
    private String sanitizeForKey(String name) {
        if (name == null || name.isEmpty()) {
            return "Empty";
        }
        
        // Replace ALL non-alphanumeric characters with underscores
        String sanitized = name.replaceAll("[^a-zA-Z0-9]", "_");
        
        // Remove multiple consecutive underscores
        sanitized = sanitized.replaceAll("_+", "_");
        
        // Remove leading and trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        
        // Return sanitized name or "Empty" if result is empty
        return sanitized.isEmpty() ? "Empty" : sanitized;
    }
    
    /**
     * Sanitizes name for use as Excel named range
     * Replaces ALL non-alphanumeric characters with underscores to match formula behavior
     */
    private String sanitizeNameForRange(String name) {
        if (name == null || name.isEmpty()) {
            return "EmptyRange";
        }
        
        // Replace ALL non-alphanumeric characters with underscores (same as sanitizeForKey)
        String sanitized = name.replaceAll("[^a-zA-Z0-9]", "_");
        
        // Remove multiple consecutive underscores
        sanitized = sanitized.replaceAll("_+", "_");
        
        // Remove leading and trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        
        // Ensure it starts with a letter or underscore (Excel requirement)
        if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0))) {
            sanitized = "L_" + sanitized;
        }
        
        // Limit length to 255 characters (Excel limit)
        if (sanitized.length() > 255) {
            // Create a hash-based name to ensure uniqueness
            sanitized = "Range_" + Math.abs(name.hashCode()) + "_" + sanitized.substring(0, Math.min(240, sanitized.length()));
        }
        
        // Ensure it's not empty after all processing
        if (sanitized.isEmpty()) {
            sanitized = "DefaultRange";
        }
        
        return sanitized;
    }
    
    /**
     * Helper class to hold parent-children mapping results
     */
    private static class ParentChildrenMapping {
        final Map<String, String> codeToDisplayNameMap;
        
        ParentChildrenMapping(Map<String, Set<String>> parentChildrenMap, Map<String, String> codeToDisplayNameMap) {
            this.codeToDisplayNameMap = codeToDisplayNameMap;
        }
    }
    
    /**
     * Adds cascading data validations for all boundary columns
     */
    private void addCascadingBoundaryValidations(XSSFWorkbook workbook, Sheet sheet, 
            int startColumnIndex, int numColumns, 
            List<String> level2Boundaries,
            ParentChildrenMapping mappingResult, Map<String, String> localizationMap) {
        
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // First column: hardcoded dropdown with level2 boundaries
        String[] level2Array = level2Boundaries.toArray(new String[0]);
        CellRangeAddressList level2Range = new CellRangeAddressList(2, config.getExcelRowLimit(), startColumnIndex, startColumnIndex);
        DataValidationConstraint level2Constraint = dvHelper.createExplicitListConstraint(level2Array);
        DataValidation level2Validation = dvHelper.createValidation(level2Constraint, level2Range);
        level2Validation.setShowErrorBox(true);
        sheet.addValidationData(level2Validation);
        
        // Get the lookup sheet and parent-children mapping
        Sheet lookupSheet = workbook.getSheet("_h_SimpleLookup_h_");
        if (lookupSheet == null) {
            log.error("Lookup sheet not found, cannot create cascading validations");
            return;
        }
        
        // Find where to add helper area (after existing content)
        int rowNum = lookupSheet.getLastRowNum() + 3; // Add some spacing
        
        // For each parent-children mapping, create individual columns for children  
        Map<String, Integer> keyToHelperRowMap = new HashMap<>();
        
        // We need to rebuild the parentChildrenMap since it's not accessible from mappingResult
        // Let's use a simplified approach by reading from the lookup sheet
        Map<String, Set<String>> parentChildrenMap = new HashMap<>();
        for (int i = 0; i <= lookupSheet.getLastRowNum(); i++) {
            Row row = lookupSheet.getRow(i);
            if (row != null && row.getCell(0) != null && row.getCell(1) != null) {
                String key = row.getCell(0).getStringCellValue();
                String childrenStr = row.getCell(1).getStringCellValue();
                if (!key.isEmpty() && !childrenStr.isEmpty()) {
                    Set<String> children = new LinkedHashSet<>(Arrays.asList(childrenStr.split(",")));
                    parentChildrenMap.put(key, children);
                }
            }
        }
        
        for (Map.Entry<String, Set<String>> entry : parentChildrenMap.entrySet()) {
            String key = entry.getKey();
            Set<String> children = entry.getValue();
            
            Row helperRow = lookupSheet.createRow(rowNum);
            keyToHelperRowMap.put(key, rowNum + 1); // Excel row numbers are 1-based
            
            // Put the key in first column for reference
            helperRow.createCell(0).setCellValue(key + "_HELPER");
            
            // Put each child in separate columns
            int col = 1;
            for (String child : children) {
                helperRow.createCell(col++).setCellValue(child);
            }
            
            // Create a named range for this key's children
            String rangeName = sanitizeNameForRange(key.replace("#", "_") + "_LIST");
            try {
                Name childrenRange = workbook.getName(rangeName);
                if (childrenRange != null) {
                    workbook.removeName(childrenRange);
                }
                childrenRange = workbook.createName();
                childrenRange.setNameName(rangeName);
                String rangeFormula = "_h_SimpleLookup_h_!$B$" + (rowNum + 1) + ":$" + 
                                     CellReference.convertNumToColString(col - 1) + "$" + (rowNum + 1);
                childrenRange.setRefersToFormula(rangeFormula);
                log.debug("Created children range: {} -> {}", rangeName, rangeFormula);
            } catch (Exception e) {
                log.error("Error creating children range for: {} - {}", key, e.getMessage());
            }
            
            rowNum++;
        }
        
        // Create validation for subsequent columns using simple INDIRECT
        for (int row = 2; row <= Math.min(100, config.getExcelRowLimit()); row++) {
            for (int colIdx = 1; colIdx < numColumns; colIdx++) {
                int actualColIndex = startColumnIndex + colIdx;
                CellRangeAddressList cascadeRange = new CellRangeAddressList(row, row, actualColIndex, actualColIndex);
                
                // Build the key for lookup
                StringBuilder keyBuilder = new StringBuilder();
                for (int i = 0; i <= colIdx - 1; i++) {
                    if (i > 0) keyBuilder.append(", \"#\", ");
                    String colRef = CellReference.convertNumToColString(startColumnIndex + i) + (row + 1);
                    keyBuilder.append(colRef);
                }
                
                // Handle more special characters including apostrophe, slash, period, comma, colon, semicolon
                // Wrap in IFERROR to show empty dropdown instead of #REF when lookup fails
                String formula = "IFERROR(INDIRECT(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(CONCATENATE(" 
                    + keyBuilder + "),\" \",\"_\"),\"-\",\"_\"),\"(\",\"_\"),\")\",\"_\"),\"'\",\"_\"),\"/\",\"_\"),\".\",\"_\"),\",\",\"_\"),\":\",\"_\"),\"#\",\"_\") & \"_LIST\"),\"\")";
                
                log.debug("Cascade formula for column {} row {}: length={}", actualColIndex, row, formula.length());
                
                try {
                    // Check formula length before creating constraint
                    if (formula.length() > 255) {
                        // If formula is too long, use a simpler version with fewer substitutions but still wrap in IFERROR
                        formula = "IFERROR(INDIRECT(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(CONCATENATE(" 
                            + keyBuilder + "),\" \",\"_\"),\"-\",\"_\"),\"'\",\"_\"),\"(\",\"_\"),\"#\",\"_\") & \"_LIST\"),\"\")";
                        log.debug("Using simplified formula with length: {}", formula.length());
                    }
                    
                    DataValidationConstraint cascadeConstraint = dvHelper.createFormulaListConstraint(formula);
                    DataValidation cascadeValidation = dvHelper.createValidation(cascadeConstraint, cascadeRange);
                    cascadeValidation.setShowErrorBox(false);
                    cascadeValidation.setEmptyCellAllowed(true);
                    sheet.addValidationData(cascadeValidation);
                } catch (Exception e) {
                    log.error("Error creating cascade validation for column {} row {} with formula length {}: {}", 
                             actualColIndex, row, formula.length(), e.getMessage());
                }
            }
        }
        
        log.info("Created simplified cascading boundary validation with {} parent keys and {} helper rows", 
                parentChildrenMap.size(), keyToHelperRowMap.size());
    }
}