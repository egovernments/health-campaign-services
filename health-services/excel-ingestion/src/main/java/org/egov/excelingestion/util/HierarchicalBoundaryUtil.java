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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility for creating cascading boundary dropdowns starting from 1st level
 * Creates a hidden sheet with boundary hierarchy and cascading dropdowns
 */
@Component
@Slf4j
public class HierarchicalBoundaryUtil {

    private static final String BOUNDARY_SEPARATOR = "#";
    private static final String HASH_PREFIX = "H_";
    private static final String HELPER_SUFFIX = "_HELPER";
    private static final String LIST_SUFFIX = "_LIST";
    private static final String SHA256_ALGORITHM = "SHA-256";

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
     * Converts a boundary combination string to an alphanumeric hash
     * This ensures safe lookup keys regardless of special characters in the combination
     * @param combination The boundary combination (e.g., "b1#b2#b3")
     * @return Alphanumeric hash with prefix (e.g., "H_abc123def456")
     */
    private String createHashedKey(String combination) {
        if (combination == null || combination.isEmpty()) {
            return HASH_PREFIX + "EMPTY";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] hashBytes = digest.digest(combination.getBytes(StandardCharsets.UTF_8));

            StringBuilder hashBuilder = new StringBuilder(HASH_PREFIX);
            for (byte b : hashBytes) {
                hashBuilder.append(String.format("%02x", b & 0xff));
            }

            // Truncate to reasonable length (Excel named range limit consideration)
            String fullHash = hashBuilder.toString();
            return fullHash.length() > 100 ? fullHash.substring(0, 100) : fullHash;

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available, falling back to simple hash", e);
            // Fallback to simple hash code with prefix
            return HASH_PREFIX + Math.abs(combination.hashCode());
        }
    }

    /**
     * Adds cascading boundary dropdown columns to an existing sheet
     * Creates multiple columns starting from 1st level with cascading dropdowns
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
        addHierarchicalBoundaryColumnWithData(workbook, sheetName, localizationMap, configuredBoundaries,
                hierarchyType, tenantId, requestInfo, null);
    }

    /**
     * Adds cascading boundary dropdown columns to an existing sheet with existing data population
     * Creates multiple columns starting from 1st level with cascading dropdowns
     * If existingData is provided, populates the first rows with that data
     *
     * @param workbook Excel workbook
     * @param sheetName Name of the sheet to add columns to
     * @param localizationMap Localization map for headers and values
     * @param configuredBoundaries List of configured boundaries from additionalDetails
     * @param hierarchyType Boundary hierarchy type
     * @param tenantId Tenant ID
     * @param requestInfo Request info for API calls
     * @param existingData Existing data to populate in the first rows
     */
    public void addHierarchicalBoundaryColumnWithData(XSSFWorkbook workbook, String sheetName, Map<String, String> localizationMap,
                                                      List<Boundary> configuredBoundaries, String hierarchyType,
                                                      String tenantId, RequestInfo requestInfo, List<Map<String, Object>> existingData) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            log.warn("Sheet '{}' not found, cannot add hierarchical boundary column", sheetName);
            return;
        }

        if (configuredBoundaries == null || configuredBoundaries.isEmpty()) {
            log.info("No boundaries configured for sheet '{}', skipping boundary column creation", sheetName);
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

        // Process enriched boundaries to get all data (using already enriched boundaries from campaign)
        List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                configuredBoundaries, codeToEnrichedBoundary, levelTypes);

        if (filteredBoundaries.isEmpty()) {
            log.info("No boundaries available for sheet '{}', skipping boundary column creation", sheetName);
            return;
        }

        // Calculate number of cascading columns (from 1st level to last level)
        int numCascadingColumns = levelTypes.size(); // Include all levels

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

        // Add cascading boundary columns (from 1st level to last level)
        for (int i = 0; i < numCascadingColumns; i++) {
            int levelIndex = i; // Start from 1st level (index 0)
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

        // Add the hidden boundary code column
        int boundaryCodeColIndex = lastSchemaCol + numCascadingColumns;
        hiddenRow.createCell(boundaryCodeColIndex).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        Cell boundaryCodeHeaderCell = visibleRow.createCell(boundaryCodeColIndex);
        String boundaryCodeHeader = localizationMap.getOrDefault("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        boundaryCodeHeaderCell.setCellValue(boundaryCodeHeader);
        boundaryCodeHeaderCell.setCellStyle(boundaryHeaderStyle);
        sheet.setColumnHidden(boundaryCodeColIndex, true); // Hide the column


        // Get first level boundaries for hardcoded dropdown
        Set<String> level1Boundaries = new LinkedHashSet<>();
        Map<String, String> level1DisplayToCodeMap = new HashMap<>();
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> path = boundary.getBoundaryPath();
            // Get level 1 boundaries (index 0)
            if (path.size() > 0 && path.get(0) != null) {
                String displayName = localizationMap.getOrDefault(path.get(0), path.get(0));
                String code = path.get(0);
                level1Boundaries.add(displayName);
                level1DisplayToCodeMap.put(displayName, code);
            }
        }

        // Create cascading boundary hierarchy sheet and get mapping result
        ParentChildrenMapping mappingResult = createCascadingBoundaryHierarchySheet(workbook, filteredBoundaries, levelTypes, localizationMap);

        // Add cascading data validations
        addCascadingBoundaryValidations(workbook, sheet, lastSchemaCol, numCascadingColumns,
                new ArrayList<>(level1Boundaries), mappingResult, localizationMap, level1DisplayToCodeMap);

        // Set column widths and styling
        for (int i = 0; i < numCascadingColumns; i++) {
            sheet.setColumnWidth(lastSchemaCol + i, 50 * 256);
        }
        // Set width for the hidden boundary code column
        sheet.setColumnWidth(boundaryCodeColIndex, 30 * 256);
        sheet.createFreezePane(0, 2); // Freeze header rows

        // Unlock cells for user input
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);

        // Create formula-based style for the boundary code column
        CellStyle formulaStyle = workbook.createCellStyle();
        formulaStyle.setLocked(true); // Keep locked to prevent user editing

        // Populate existing data first, then empty rows with dropdowns
        int dataRowsPopulated = 0;
        if (existingData != null && !existingData.isEmpty()) {
            dataRowsPopulated = populateExistingDataWithBoundaries(sheet, existingData, lastSchemaCol,
                    numCascadingColumns, boundaryCodeColIndex, levelTypes, hierarchyType,
                    filteredBoundaries, localizationMap, unlocked, formulaStyle);
        }

        // Create remaining empty rows with dropdowns
        for (int r = 2 + dataRowsPopulated; r <= config.getExcelRowLimit(); r++) {
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

            // Add formula to the boundary code column to show the selected boundary code
            Cell boundaryCodeCell = row.getCell(boundaryCodeColIndex);
            if (boundaryCodeCell == null)
                boundaryCodeCell = row.createCell(boundaryCodeColIndex);
            boundaryCodeCell.setCellStyle(formulaStyle);

            // Set formula to get the boundary code based on the last selected value
            String boundaryCodeFormula = createBoundaryCodeFormula(r + 1, lastSchemaCol, numCascadingColumns);
            boundaryCodeCell.setCellFormula(boundaryCodeFormula);
        }

        log.info("Added {} cascading boundary dropdown columns", numCascadingColumns);
    }

    /**
     * Creates a formula to get the boundary code based on the selected boundary values
     * Uses a simple approach - checks from right to left and returns code for first non-empty value
     */
    private String createBoundaryCodeFormula(int rowNumber, int firstColumnIndex, int numColumns) {
        StringBuilder formula = new StringBuilder();

        // Build nested IF statements from last column to first
        for (int i = numColumns - 1; i >= 0; i--) {
            String colRef = CellReference.convertNumToColString(firstColumnIndex + i) + rowNumber;

            formula.append("IF(").append(colRef).append("<>\"\",");

            // Use VLOOKUP to find the boundary code from the display name to code mapping in columns D:E
            // Use bounded range instead of whole-column reference for better Excel compatibility
            formula.append("IFERROR(VLOOKUP(").append(colRef).append(",_h_SimpleLookup_h_!$D$1:$E$50000,2,0),\"\")");

            if (i > 0) {
                formula.append(",");
            }
        }

        // Close all IF statements with empty string as final fallback
        if (numColumns > 0) {
            formula.append(",\"\"");
            for (int i = 0; i < numColumns; i++) {
                formula.append(")");
            }
        }

        return formula.toString();
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
        if (lookupSheet != null) {
            // Complete removal - deletes ALL internal POI structures
            int sheetIndex = workbook.getSheetIndex(lookupSheet);
            workbook.removeSheetAt(sheetIndex);
            log.info("Removed existing _h_SimpleLookup_h_ sheet to prevent named range accumulation (fixes LibreOffice/Excel corruption)");
        }

        // Always create fresh sheet - ensures zero accumulated metadata
        lookupSheet = workbook.createSheet("_h_SimpleLookup_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_SimpleLookup_h_"), true);
        log.info("Created fresh _h_SimpleLookup_h_ sheet for current generation");

        // Build parent-children mapping with hashed keys
        Map<String, Set<String>> parentChildrenMap = new HashMap<>();
        Map<String, String> codeToDisplayNameMap = new HashMap<>();
        Map<String, Set<String>> parentChildrenCodeMap = new HashMap<>();
        Map<String, String> hashToOriginalKeyMap = new HashMap<>();

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

        // Second pass: build parent-children relationships starting from level 0 (first level)
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();

            // Build hierarchical keys for each level starting from level 0
            for (int level = 0; level < path.size() - 1; level++) {
                if (path.get(level) != null && path.get(level + 1) != null) {
                    // Build key based on hierarchy path from level 0 onwards
                    StringBuilder keyBuilder = new StringBuilder();
                    for (int i = 0; i <= level; i++) {
                        if (i > 0) keyBuilder.append(BOUNDARY_SEPARATOR);
                        String displayName = codeToDisplayNameMap.get(path.get(i));
                        keyBuilder.append(displayName);
                    }
                    String originalKey = keyBuilder.toString();
                    String hashedKey = createHashedKey(originalKey);

                    // Store mapping from hash to original for debugging/reference
                    hashToOriginalKeyMap.put(hashedKey, originalKey);

                    String childDisplayName = codeToDisplayNameMap.get(path.get(level + 1));
                    String childCode = path.get(level + 1);
                    parentChildrenMap.computeIfAbsent(hashedKey, k -> new LinkedHashSet<>()).add(childDisplayName);
                    parentChildrenCodeMap.computeIfAbsent(hashedKey, k -> new LinkedHashSet<>()).add(childCode);
                }
            }
        }

        // Populate lookup sheet with key-value pairs (comma-separated children and codes)
        int rowNum = 0;
        for (Map.Entry<String, Set<String>> entry : parentChildrenMap.entrySet()) {
            String hashedKey = entry.getKey();
            Set<String> children = entry.getValue();
            Set<String> childrenCodes = parentChildrenCodeMap.get(hashedKey);

            Row row = lookupSheet.createRow(rowNum++);
            // Hashed key in column A
            row.createCell(0).setCellValue(hashedKey);

            // All children in column B as comma-separated values
            String childrenList = String.join(",", children);
            row.createCell(1).setCellValue(childrenList);

            // All children codes in column C as comma-separated values
            String childrenCodesList = String.join(",", childrenCodes);
            row.createCell(2).setCellValue(childrenCodesList);

            // Original key in column F for reference and debugging
            String originalKey = hashToOriginalKeyMap.get(hashedKey);
            if (originalKey != null) {
                row.createCell(5).setCellValue(originalKey);
            }
        }

        // Add a simple display name to code mapping after the main lookup data
        rowNum += 2; // Add some spacing

        // Create a simple mapping of all display names to their codes
        for (Map.Entry<String, String> entry : codeToDisplayNameMap.entrySet()) {
            String code = entry.getKey();
            String displayName = entry.getValue();

            Row mappingRow = lookupSheet.createRow(rowNum++);
            mappingRow.createCell(3).setCellValue(displayName); // Column D
            mappingRow.createCell(4).setCellValue(code);        // Column E
        }

        log.info("Created cascading boundary lookup sheet with {} hashed entries and {} display-to-code mappings",
                parentChildrenMap.size(), codeToDisplayNameMap.size());

        return new ParentChildrenMapping(parentChildrenMap, codeToDisplayNameMap, parentChildrenCodeMap, hashToOriginalKeyMap);
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
        final Map<String, Set<String>> parentChildrenCodeMap;
        final Map<String, String> hashToOriginalKeyMap;

        ParentChildrenMapping(Map<String, Set<String>> parentChildrenMap, Map<String, String> codeToDisplayNameMap, Map<String, Set<String>> parentChildrenCodeMap, Map<String, String> hashToOriginalKeyMap) {
            this.codeToDisplayNameMap = codeToDisplayNameMap;
            this.parentChildrenCodeMap = parentChildrenCodeMap;
            this.hashToOriginalKeyMap = hashToOriginalKeyMap;
        }
    }

    /**
     * Adds cascading data validations for all boundary columns
     */
    private void addCascadingBoundaryValidations(XSSFWorkbook workbook, Sheet sheet,
                                                 int startColumnIndex, int numColumns,
                                                 List<String> level1Boundaries,
                                                 ParentChildrenMapping mappingResult, Map<String, String> localizationMap,
                                                 Map<String, String> level1DisplayToCodeMap) {

        DataValidationHelper dvHelper = sheet.getDataValidationHelper();

        // First column: Use range-based validation for level1 boundaries to avoid 255-char limit
        addLevel1BoundaryValidation(workbook, sheet, dvHelper, startColumnIndex, level1Boundaries);

        // Get the lookup sheet and parent-children mapping
        Sheet lookupSheet = workbook.getSheet("_h_SimpleLookup_h_");
        if (lookupSheet == null) {
            log.error("Lookup sheet not found, cannot create cascading validations");
            return;
        }

        // Find where to add helper area (after existing content)
        int rowNum = ExcelUtil.findActualLastRowWithData(lookupSheet) + 3; // Add some spacing

        // For each parent-children mapping, create individual columns for children  
        Map<String, Integer> keyToHelperRowMap = new HashMap<>();
        Map<String, String> originalToHashedKeyMap = new HashMap<>();

        // We need to rebuild the parentChildrenMap since it's not accessible from mappingResult
        // Let's use a simplified approach by reading from the lookup sheet
        Map<String, Set<String>> parentChildrenMap = new HashMap<>();
        int actualLastRow = ExcelUtil.findActualLastRowWithData(lookupSheet);
        for (int i = 0; i <= actualLastRow; i++) {
            Row row = lookupSheet.getRow(i);
            if (row != null && row.getCell(0) != null && row.getCell(1) != null) {
                String hashedKey = row.getCell(0).getStringCellValue();
                String childrenStr = row.getCell(1).getStringCellValue();
                if (!hashedKey.isEmpty() && !childrenStr.isEmpty()) {
                    Set<String> children = new LinkedHashSet<>(Arrays.asList(childrenStr.split(",")));
                    parentChildrenMap.put(hashedKey, children);

                    // Also read the original key from column F if available for reverse mapping
                    if (row.getCell(5) != null) {
                        String originalKey = row.getCell(5).getStringCellValue();
                        if (!originalKey.isEmpty()) {
                            originalToHashedKeyMap.put(originalKey, hashedKey);
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry : parentChildrenMap.entrySet()) {
            String hashedKey = entry.getKey();
            Set<String> children = entry.getValue();

            Row helperRow = lookupSheet.createRow(rowNum);
            keyToHelperRowMap.put(hashedKey, rowNum + 1); // Excel row numbers are 1-based

            // Put the hashed key in first column for reference
            helperRow.createCell(0).setCellValue(hashedKey + HELPER_SUFFIX);

            // Put each child in separate columns
            int col = 1;
            for (String child : children) {
                helperRow.createCell(col++).setCellValue(child);
            }

            // Create a named range for this hashed key's children
            // Use the hashed key directly as it's already safe for Excel named ranges
            String rangeName = hashedKey + LIST_SUFFIX;
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
                log.error("Error creating children range for: {} - {}", hashedKey, e.getMessage());
            }

            rowNum++;
        }

        for (int colIdx = 1; colIdx < numColumns; colIdx++) {
            int actualColIndex = startColumnIndex + colIdx;

            // Create range for entire column (row 3 to rowLimit+1)
            // Row index 2 = Excel row 3 (first data row), accounts for 0-based vs 1-based indexing
            CellRangeAddressList cascadeRange = new CellRangeAddressList(2, config.getExcelRowLimit(), actualColIndex, actualColIndex);

            StringBuilder keyBuilder = new StringBuilder();
            for (int i = 0; i <= colIdx - 1; i++) {
                if (i > 0) keyBuilder.append(", \"" + BOUNDARY_SEPARATOR + "\", ");
                // Use relative cell reference (e.g., A3) - Excel automatically adjusts this
                // for each row when the validation formula is applied to a range.
                // IMPORTANT: Do NOT use INDIRECT("A"&ROW()) as it doesn't work in MS Excel
                // data validation (Excel evaluates ROW() only once, not per-cell).
                String colRef = CellReference.convertNumToColString(startColumnIndex + i) + "3";
                keyBuilder.append(colRef);
            }

            // Create formula that uses INDEX/MATCH to find the hashed key from the original key
            // Then use INDIRECT with that hashed key to get the named range
            // MATCH finds the original key in column F, INDEX returns corresponding hashed key from column A
            // Use bounded ranges instead of whole-column references for better Excel compatibility
            String formula = "IFERROR(INDIRECT(INDEX(_h_SimpleLookup_h_!$A$1:$A$50000,MATCH(CONCATENATE(" + keyBuilder +
                    "),_h_SimpleLookup_h_!$F$1:$F$50000,0)) & \"" + LIST_SUFFIX + "\"),\"\")";

            log.debug("Optimized cascade formula for column {}: length={}", actualColIndex, formula.length());

            try {
                DataValidationConstraint cascadeConstraint = dvHelper.createFormulaListConstraint(formula);
                DataValidation cascadeValidation = dvHelper.createValidation(cascadeConstraint, cascadeRange);
                cascadeValidation.setShowErrorBox(false);
                cascadeValidation.setEmptyCellAllowed(true);
                sheet.addValidationData(cascadeValidation);
            } catch (Exception e) {
                log.error("Error creating cascade validation for column {} with formula length {}: {}",
                        actualColIndex, formula.length(), e.getMessage());
            }
        }

        log.info("Created simplified cascading boundary validation with {} parent keys and {} helper rows",
                parentChildrenMap.size(), keyToHelperRowMap.size());
    }

    /**
     * Add level 1 boundary validation using range reference to avoid 255-character limit
     * Creates a list in the hidden sheet and references it for validation
     */
    private void addLevel1BoundaryValidation(XSSFWorkbook workbook, Sheet sheet, DataValidationHelper dvHelper,
                                             int startColumnIndex, List<String> level1Boundaries) {
        try {
            // Get the lookup sheet
            Sheet lookupSheet = workbook.getSheet("_h_SimpleLookup_h_");
            if (lookupSheet == null) {
                log.error("Lookup sheet not found, cannot create level1 boundary validation");
                return;
            }

            // Calculate total length to decide validation approach
            int totalLength = level1Boundaries.stream()
                    .mapToInt(s -> s.length() + 1) // +1 for separator
                    .sum();

            if (totalLength <= 250) { // Leave some buffer under 255 limit
                // Use explicit list for small lists
                String[] level1Array = level1Boundaries.toArray(new String[0]);
                // Use actual data row count or config limit, whichever is larger to cover all rows
                // ExcelUtil.findActualLastRowWithData(sheet) is 0-based, so add 1 to get actual count, then compare with limit
                int actualDataRows = ExcelUtil.findActualLastRowWithData(sheet) + 1;
                int maxRow = Math.max(actualDataRows, config.getExcelRowLimit());
                CellRangeAddressList level1Range = new CellRangeAddressList(2, maxRow, startColumnIndex, startColumnIndex);
                DataValidationConstraint level1Constraint = dvHelper.createExplicitListConstraint(level1Array);
                DataValidation level1Validation = dvHelper.createValidation(level1Constraint, level1Range);
                level1Validation.setShowErrorBox(true);
                sheet.addValidationData(level1Validation);
                log.info("Applied explicit list validation for {} level1 boundaries (total length: {})", level1Boundaries.size(), totalLength);
            } else {
                // Use range reference for large lists
                addLevel1BoundariesRangeValidation(workbook, sheet, dvHelper, startColumnIndex, level1Boundaries, lookupSheet);
            }

        } catch (Exception e) {
            log.error("Failed to add level1 boundary validation: {}", e.getMessage(), e);
            // Fall back to no validation rather than failing completely
            log.warn("Continuing without level1 boundary validation due to error");
        }
    }

    /**
     * Add level 1 boundaries to lookup sheet and create range-based validation
     */
    private void addLevel1BoundariesRangeValidation(XSSFWorkbook workbook, Sheet sheet, DataValidationHelper dvHelper,
                                                    int startColumnIndex, List<String> level1Boundaries, Sheet lookupSheet) {

        // Find a good location in the lookup sheet to add level1 boundaries list
        int startRow = Math.max(ExcelUtil.findActualLastRowWithData(sheet) + 5, 1); // Add some spacing

        // Add level1 boundaries to column G in lookup sheet
        int level1Column = 6; // Column G (0-indexed)
        for (int i = 0; i < level1Boundaries.size(); i++) {
            Row row = lookupSheet.getRow(startRow + i);
            if (row == null) {
                row = lookupSheet.createRow(startRow + i);
            }
            Cell cell = row.getCell(level1Column);
            if (cell == null) {
                cell = row.createCell(level1Column);
            }
            cell.setCellValue(level1Boundaries.get(i));
        }

        // Create named range for level1 boundaries
        String rangeName = "Level1_Boundaries";
        try {
            // Remove existing range if it exists
            Name existingRange = workbook.getName(rangeName);
            if (existingRange != null) {
                workbook.removeName(existingRange);
            }

            // Create new range
            Name level1Range = workbook.createName();
            level1Range.setNameName(rangeName);
            String rangeFormula = "_h_SimpleLookup_h_!$G$" + (startRow + 1) + ":$G$" + (startRow + level1Boundaries.size());
            level1Range.setRefersToFormula(rangeFormula);

            log.info("Created named range '{}' for {} level1 boundaries: {}", rangeName, level1Boundaries.size(), rangeFormula);

            // Apply validation using the named range
            // Use actual data row count or config limit, whichever is larger to cover all rows
            // ExcelUtil.findActualLastRowWithData(sheet) is 0-based, so add 1 to get actual count, then compare with limit
            int actualDataRows = ExcelUtil.findActualLastRowWithData(sheet) + 1;
            int maxRow = Math.max(actualDataRows, config.getExcelRowLimit());
            CellRangeAddressList validationRange = new CellRangeAddressList(2, maxRow, startColumnIndex, startColumnIndex);
            DataValidationConstraint rangeConstraint = dvHelper.createFormulaListConstraint(rangeName);
            DataValidation rangeValidation = dvHelper.createValidation(rangeConstraint, validationRange);
            rangeValidation.setShowErrorBox(true);
            rangeValidation.setEmptyCellAllowed(true);
            sheet.addValidationData(rangeValidation);

            log.info("Applied range-based validation for {} level1 boundaries using named range", level1Boundaries.size());

        } catch (Exception e) {
            log.error("Failed to create named range for level1 boundaries: {}", e.getMessage());
            // Try formula reference without named range as fallback
            try {
                String directFormula = "_h_SimpleLookup_h_!$G$" + (startRow + 1) + ":$G$" + (startRow + level1Boundaries.size());
                // Use actual data row count or config limit, whichever is larger to cover all rows
                // ExcelUtil.findActualLastRowWithData(sheet) is 0-based, so add 1 to get actual count, then compare with limit
                int actualDataRows = ExcelUtil.findActualLastRowWithData(sheet) + 1;
                int maxRow = Math.max(actualDataRows, config.getExcelRowLimit());
                CellRangeAddressList validationRange = new CellRangeAddressList(2, maxRow, startColumnIndex, startColumnIndex);
                DataValidationConstraint formulaConstraint = dvHelper.createFormulaListConstraint(directFormula);
                DataValidation formulaValidation = dvHelper.createValidation(formulaConstraint, validationRange);
                formulaValidation.setShowErrorBox(true);
                formulaValidation.setEmptyCellAllowed(true);
                sheet.addValidationData(formulaValidation);

                log.info("Applied direct formula validation for level1 boundaries: {}", directFormula);
            } catch (Exception e2) {
                log.error("Failed to apply direct formula validation as fallback: {}", e2.getMessage());
                throw new RuntimeException("Unable to create level1 boundary validation", e2);
            }
        }
    }

    /**
     * Populates existing data rows with boundary information and sets up dropdowns
     *
     * @param sheet The sheet to populate
     * @param existingData List of existing data records
     * @param lastSchemaCol Index of last schema column
     * @param numCascadingColumns Number of cascading boundary columns
     * @param boundaryCodeColIndex Index of boundary code column
     * @param levelTypes List of boundary level types
     * @param hierarchyType Boundary hierarchy type
     * @param filteredBoundaries List of filtered boundary row data
     * @param localizationMap Localization map
     * @param unlocked Cell style for unlocked cells
     * @param formulaStyle Cell style for formula cells
     * @return Number of data rows populated
     */
    private int populateExistingDataWithBoundaries(Sheet sheet, List<Map<String, Object>> existingData,
                                                   int lastSchemaCol, int numCascadingColumns, int boundaryCodeColIndex,
                                                   List<String> levelTypes, String hierarchyType,
                                                   List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                                   Map<String, String> localizationMap,
                                                   CellStyle unlocked, CellStyle formulaStyle) {

        int rowsPopulated = 0;

        for (int i = 0; i < existingData.size() && i < (config.getExcelRowLimit() - 2); i++) {
            Map<String, Object> dataRow = existingData.get(i);
            int excelRowIndex = 2 + i; // Start from row 3 (index 2)

            Row row = sheet.getRow(excelRowIndex);
            if (row == null) {
                row = sheet.createRow(excelRowIndex);
            }

            // Get boundary information for this data row
            String boundaryCode = extractBoundaryCodeFromData(dataRow);
            List<String> boundaryPath = null;

            if (boundaryCode != null && !boundaryCode.isEmpty()) {
                // Find boundary path from filtered boundaries data
                for (BoundaryUtil.BoundaryRowData boundaryRowData : filteredBoundaries) {
                    if (boundaryCode.equals(boundaryRowData.getLastLevelCode())) {
                        boundaryPath = boundaryRowData.getBoundaryPath();
                        break;
                    }
                }
            }

            // Populate cascading boundary columns
            for (int j = 0; j < numCascadingColumns; j++) {
                Cell cell = row.getCell(lastSchemaCol + j);
                if (cell == null) {
                    cell = row.createCell(lastSchemaCol + j);
                }
                cell.setCellStyle(unlocked);

                // Fill boundary value if available (include all levels starting from first level)
                int boundaryLevelIndex = j; // Include first level
                if (boundaryPath != null && boundaryLevelIndex < boundaryPath.size()) {
                    String boundaryCodeAtLevel = boundaryPath.get(boundaryLevelIndex);
                    if (boundaryCodeAtLevel != null && !boundaryCodeAtLevel.isEmpty()) {
                        // Get localized display name
                        String displayName = localizationMap.getOrDefault(boundaryCodeAtLevel, boundaryCodeAtLevel);
                        cell.setCellValue(displayName);
                    }
                }
            }

            // Set boundary code in hidden column
            Cell boundaryCodeCell = row.getCell(boundaryCodeColIndex);
            if (boundaryCodeCell == null) {
                boundaryCodeCell = row.createCell(boundaryCodeColIndex);
            }
            boundaryCodeCell.setCellStyle(formulaStyle);

            // ALWAYS use formula for boundary code column to enable cascading updates
            // This ensures that when users change boundary selections, the hidden code updates automatically
            String boundaryCodeFormula = createBoundaryCodeFormula(excelRowIndex + 1, lastSchemaCol, numCascadingColumns);
            boundaryCodeCell.setCellFormula(boundaryCodeFormula);

            rowsPopulated++;
        }

        log.info("Populated {} existing data rows with boundary information", rowsPopulated);
        return rowsPopulated;
    }

    /**
     * Extracts boundary code from existing data row
     * Looks for common boundary code field names
     */
    private String extractBoundaryCodeFromData(Map<String, Object> dataRow) {
        // Try common boundary code field names
        String[] possibleFields = {
                "HCM_ADMIN_CONSOLE_BOUNDARY_CODE",
                "boundaryCode",
                "boundary_code",
                "BOUNDARY_CODE",
                "administrativeUnit"
        };

        for (String field : possibleFields) {
            Object value = dataRow.get(field);
            if (value != null && !value.toString().isEmpty()) {
                return value.toString();
            }
        }

        return null;
    }
}