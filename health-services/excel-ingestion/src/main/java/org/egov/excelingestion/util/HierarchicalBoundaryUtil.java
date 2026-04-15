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
 *
 * Architecture: Uses helper columns to separate complex formula logic from data validation.
 * - Helper columns contain cell formulas that compute the lookup key hash
 * - Data validation formulas simply reference the helper column with a relative reference
 * - This approach works in both LibreOffice Calc and MS Excel
 */
@Component
@Slf4j
public class HierarchicalBoundaryUtil {

    private static final String BOUNDARY_SEPARATOR = "#";
    private static final String HASH_PREFIX = "H_";
    private static final String LIST_SUFFIX = "_LIST";
    private static final String SHA256_ALGORITHM = "SHA-256";
    // Column indices for the separate key-to-hash mapping table
    // This table is stored in a completely separate section of the lookup sheet
    private static final int KEY_HASH_TABLE_KEY_COLUMN = 7;   // Column H: Original key
    private static final int KEY_HASH_TABLE_HASH_COLUMN = 8;  // Column I: Hashed key

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

        if (levelTypes.size() < 1) {
            log.warn("Hierarchy has less than 1 level, skipping boundary column creation");
            return;
        }

        List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                configuredBoundaries, codeToEnrichedBoundary, levelTypes);

        if (filteredBoundaries.isEmpty()) {
            log.info("No boundaries available for sheet '{}', skipping boundary column creation", sheetName);
            return;
        }

        Row hiddenRow = sheet.getRow(0);
        Row visibleRow = sheet.getRow(1);
        if (hiddenRow == null) hiddenRow = sheet.createRow(0);
        if (visibleRow == null) visibleRow = sheet.createRow(1);

        int lastSchemaCol = visibleRow.getLastCellNum();
        if (lastSchemaCol < 0) lastSchemaCol = 0;

        CellStyle boundaryHeaderStyle = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, config.getDefaultHeaderColor());

        // This list will hold the column indices of the VISIBLE boundary dropdowns
        List<Integer> visibleColIndices = new ArrayList<>();
        int currentColIndex = lastSchemaCol;

        for (int i = 0; i < levelTypes.size(); i++) {
            String boundaryType = hierarchyRelations.get(i).getBoundaryType();
            String columnName = (hierarchyType + "_" + boundaryType).toUpperCase();

            // For levels 2+, first add the hidden helper column
            if (i > 0) {
                String helperColumnName = columnName + "_HELPER";
                hiddenRow.createCell(currentColIndex).setCellValue(helperColumnName);
                visibleRow.createCell(currentColIndex).setCellValue(helperColumnName);
                sheet.setColumnHidden(currentColIndex, true);
                currentColIndex++;
            }

            // Add the visible column for all levels
            hiddenRow.createCell(currentColIndex).setCellValue(columnName);
            Cell headerCell = visibleRow.createCell(currentColIndex);
            headerCell.setCellValue(localizationMap.getOrDefault(columnName, columnName));
            headerCell.setCellStyle(boundaryHeaderStyle);
            sheet.setColumnWidth(currentColIndex, 50 * 256);
            visibleColIndices.add(currentColIndex);
            currentColIndex++;
        }

        // Add the final boundary code column
        int boundaryCodeColIndex = currentColIndex;
        hiddenRow.createCell(boundaryCodeColIndex).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        Cell boundaryCodeHeaderCell = visibleRow.createCell(boundaryCodeColIndex);
        boundaryCodeHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
        boundaryCodeHeaderCell.setCellStyle(boundaryHeaderStyle);
        sheet.setColumnHidden(boundaryCodeColIndex, true);
        sheet.setColumnWidth(boundaryCodeColIndex, 30 * 256);

        Set<String> level1Boundaries = new LinkedHashSet<>();
        filteredBoundaries.forEach(b -> {
            if (b.getBoundaryPath().size() > 0 && b.getBoundaryPath().get(0) != null) {
                level1Boundaries.add(localizationMap.getOrDefault(b.getBoundaryPath().get(0), b.getBoundaryPath().get(0)));
            }
        });

        // Create the hidden sheet with all lookup data
        ParentChildrenMapping mappingResult = createCascadingBoundaryHierarchySheet(workbook, filteredBoundaries, levelTypes, localizationMap);

        // Add validations using the helper column architecture
        addCascadingBoundaryValidations(workbook, sheet, lastSchemaCol, levelTypes.size(),
                new ArrayList<>(level1Boundaries), mappingResult, localizationMap, visibleColIndices);

        sheet.createFreezePane(0, 2);
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);
        CellStyle formulaStyle = workbook.createCellStyle();
        formulaStyle.setLocked(true);

        int dataRowsPopulated = 0;
        if (existingData != null && !existingData.isEmpty()) {
            dataRowsPopulated = populateExistingDataWithBoundaries(sheet, existingData, lastSchemaCol,
                    levelTypes.size(), boundaryCodeColIndex, levelTypes, hierarchyType,
                    filteredBoundaries, localizationMap, unlocked, formulaStyle, visibleColIndices,
                    mappingResult.displayNameMappingStartRow, mappingResult.displayNameMappingEndRow);
        }

        // Create remaining empty rows with dropdowns and formulas
        for (int r = 2 + dataRowsPopulated; r <= config.getExcelRowLimit(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) row = sheet.createRow(r);
            for (int colIdx : visibleColIndices) {
                Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                cell.setCellStyle(unlocked);
            }
            Cell boundaryCodeCell = row.getCell(boundaryCodeColIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            boundaryCodeCell.setCellStyle(formulaStyle);
            String boundaryCodeFormula = createBoundaryCodeFormula(r + 1, visibleColIndices,
                    mappingResult.displayNameMappingStartRow, mappingResult.displayNameMappingEndRow);
            boundaryCodeCell.setCellFormula(boundaryCodeFormula);
        }

        log.info("Added {} cascading boundary dropdown columns with helper columns.", levelTypes.size());
    }

    /**
     * Creates a formula to get the boundary code based on the selected boundary values
     * Uses a simple approach - checks from right to left and returns code for first non-empty value
     * @param rowNumber The Excel row number (1-based)
     * @param visibleColIndices List of visible column indices
     * @param displayNameMappingStartRow Start row of display name to code mapping (0-based)
     * @param displayNameMappingEndRow End row of display name to code mapping (0-based)
     */
    private String createBoundaryCodeFormula(int rowNumber, List<Integer> visibleColIndices,
                                             int displayNameMappingStartRow, int displayNameMappingEndRow) {
        StringBuilder formula = new StringBuilder();

        // Convert 0-based row indices to 1-based Excel row numbers
        int excelStartRow = displayNameMappingStartRow + 1;
        int excelEndRow = displayNameMappingEndRow;

        // Build nested IF statements from last column to first
        for (int i = visibleColIndices.size() - 1; i >= 0; i--) {
            String colRef = CellReference.convertNumToColString(visibleColIndices.get(i)) + rowNumber;

            formula.append("IF(").append(colRef).append("<>\"\",");

            // Use VLOOKUP to find the boundary code from the display name to code mapping in columns D:E
            // Use the correct row range where the mapping data actually exists
            formula.append("IFERROR(VLOOKUP(").append(colRef)
                   .append(",_h_SimpleLookup_h_!$D$").append(excelStartRow)
                   .append(":$E$").append(excelEndRow).append(",2,0),\"\")");

            if (i > 0) {
                formula.append(",");
            }
        }

        // Close all IF statements with empty string as final fallback
        if (!visibleColIndices.isEmpty()) {
            formula.append(",\"\"");
            for (int i = 0; i < visibleColIndices.size(); i++) {
                formula.append(")");
            }
        }

        return formula.toString();
    }

    /**
     * Creates a hidden sheet with cascading boundary hierarchy
     * Single hidden lookup sheet with parent#child structure
     */
    private ParentChildrenMapping createCascadingBoundaryHierarchySheet(XSSFWorkbook workbook,
                                                                        List<BoundaryUtil.BoundaryRowData> boundaries,
                                                                        List<String> levelTypes,
                                                                        Map<String, String> localizationMap) {

        // Create or get the hidden lookup sheet
        Sheet lookupSheet = workbook.getSheet("_h_SimpleLookup_h_");
        if (lookupSheet != null) {
            int sheetIndex = workbook.getSheetIndex(lookupSheet);
            workbook.removeSheetAt(sheetIndex);
            log.info("Removed existing _h_SimpleLookup_h_ sheet to prevent named range accumulation");
        }

        lookupSheet = workbook.createSheet("_h_SimpleLookup_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_SimpleLookup_h_"), true);
        log.info("Created fresh _h_SimpleLookup_h_ sheet for current generation");

        // Build parent-children mapping with hashed keys
        Map<String, Set<String>> parentChildrenMap = new LinkedHashMap<>();
        Map<String, String> codeToDisplayNameMap = new HashMap<>();
        Map<String, Set<String>> parentChildrenCodeMap = new HashMap<>();
        Map<String, String> hashToOriginalKeyMap = new HashMap<>();

        // First pass: collect all boundary codes with their display names
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (String code : path) {
                if (code != null) {
                    codeToDisplayNameMap.putIfAbsent(code, localizationMap.getOrDefault(code, code));
                }
            }
        }

        // Second pass: build parent-children relationships
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (int level = 0; level < path.size() - 1; level++) {
                if (path.get(level) != null && path.get(level + 1) != null) {
                    StringBuilder keyBuilder = new StringBuilder();
                    for (int i = 0; i <= level; i++) {
                        if (i > 0) keyBuilder.append(BOUNDARY_SEPARATOR);
                        keyBuilder.append(codeToDisplayNameMap.get(path.get(i)));
                    }
                    String originalKey = keyBuilder.toString();
                    String hashedKey = createHashedKey(originalKey);
                    hashToOriginalKeyMap.put(hashedKey, originalKey);

                    String childDisplayName = codeToDisplayNameMap.get(path.get(level + 1));
                    parentChildrenMap.computeIfAbsent(hashedKey, k -> new LinkedHashSet<>()).add(childDisplayName);
                    parentChildrenCodeMap.computeIfAbsent(hashedKey, k -> new LinkedHashSet<>()).add(path.get(level + 1));
                }
            }
        }

        // SECTION 1: Children data (Rows 1-N)
        // Structure: Column A = Hash, Columns B onwards = Children (NO original key here!)
        int rowNum = 0;
        for (Map.Entry<String, Set<String>> entry : parentChildrenMap.entrySet()) {
            String hashedKey = entry.getKey();
            Set<String> children = entry.getValue();

            Row row = lookupSheet.createRow(rowNum);
            // Column A: Hashed key
            row.createCell(0).setCellValue(hashedKey);

            // Columns B onwards: Children ONLY (one per column)
            int col = 1;
            for (String child : children) {
                row.createCell(col++).setCellValue(child);
            }

            // Create a named range for this row of children (columns B to last child column)
            // NO original key in this row - children only!
            String rangeName = hashedKey + LIST_SUFFIX;
            try {
                Name existingName = workbook.getName(rangeName);
                if (existingName != null) {
                    workbook.removeName(existingName);
                }

                Name childrenRange = workbook.createName();
                childrenRange.setNameName(rangeName);
                // Range from column B to the last child column (col-1 since col was incremented after last child)
                String rangeFormula = String.format("_h_SimpleLookup_h_!$B$%d:$%s$%d",
                        rowNum + 1,
                        CellReference.convertNumToColString(col - 1),
                        rowNum + 1);
                childrenRange.setRefersToFormula(rangeFormula);
            } catch (Exception e) {
                log.error("Error creating children named range for: {} - {}", hashedKey, e.getMessage());
            }

            rowNum++;
        }
        int childrenSectionEndRow = rowNum;

        // SECTION 2: Display name to code mapping (for boundary code VLOOKUP)
        // Structure: Column D = Display name, Column E = Code
        rowNum += 2; // Add spacing
        int displayNameMappingStartRow = rowNum;
        for (Map.Entry<String, String> entry : codeToDisplayNameMap.entrySet()) {
            Row mappingRow = lookupSheet.createRow(rowNum++);
            mappingRow.createCell(3).setCellValue(entry.getValue()); // Column D: Display name
            mappingRow.createCell(4).setCellValue(entry.getKey());    // Column E: Code
        }
        int displayNameMappingEndRow = rowNum;

        // SECTION 3: Key-to-Hash mapping table (COMPLETELY SEPARATE from children!)
        // Structure: Column H = Original key (e.g., "INDIA#Karnataka"), Column I = Hash
        // This is used by the helper formula to find the hash for a given key combination
        rowNum += 2; // Add spacing
        int keyHashTableStartRow = rowNum;
        for (Map.Entry<String, String> entry : hashToOriginalKeyMap.entrySet()) {
            String hashedKey = entry.getKey();
            String originalKey = entry.getValue();

            Row keyHashRow = lookupSheet.createRow(rowNum++);
            keyHashRow.createCell(KEY_HASH_TABLE_KEY_COLUMN).setCellValue(originalKey);   // Column H: Original key
            keyHashRow.createCell(KEY_HASH_TABLE_HASH_COLUMN).setCellValue(hashedKey);    // Column I: Hash
        }
        int keyHashTableEndRow = rowNum;

        log.info("Created cascading boundary lookup sheet: {} children rows, {} display-name mappings (rows {}-{}), {} key-hash mappings (rows {}-{})",
                childrenSectionEndRow, codeToDisplayNameMap.size(), displayNameMappingStartRow + 1, displayNameMappingEndRow,
                hashToOriginalKeyMap.size(), keyHashTableStartRow + 1, keyHashTableEndRow);

        return new ParentChildrenMapping(parentChildrenMap, codeToDisplayNameMap, parentChildrenCodeMap,
                hashToOriginalKeyMap, keyHashTableStartRow, keyHashTableEndRow,
                displayNameMappingStartRow, displayNameMappingEndRow);
    }

    /**
     * Helper class to hold parent-children mapping results
     */
    private static class ParentChildrenMapping {
        final Map<String, Set<String>> parentChildrenMap;
        final Map<String, String> codeToDisplayNameMap;
        final Map<String, Set<String>> parentChildrenCodeMap;
        final Map<String, String> hashToOriginalKeyMap;
        final int keyHashTableStartRow;  // Start row of the key-to-hash mapping table
        final int keyHashTableEndRow;    // End row of the key-to-hash mapping table
        final int displayNameMappingStartRow;  // Start row of display name to code mapping (Section 2)
        final int displayNameMappingEndRow;    // End row of display name to code mapping (Section 2)

        ParentChildrenMapping(Map<String, Set<String>> parentChildrenMap, Map<String, String> codeToDisplayNameMap,
                            Map<String, Set<String>> parentChildrenCodeMap, Map<String, String> hashToOriginalKeyMap,
                            int keyHashTableStartRow, int keyHashTableEndRow,
                            int displayNameMappingStartRow, int displayNameMappingEndRow) {
            this.parentChildrenMap = parentChildrenMap;
            this.codeToDisplayNameMap = codeToDisplayNameMap;
            this.parentChildrenCodeMap = parentChildrenCodeMap;
            this.hashToOriginalKeyMap = hashToOriginalKeyMap;
            this.keyHashTableStartRow = keyHashTableStartRow;
            this.keyHashTableEndRow = keyHashTableEndRow;
            this.displayNameMappingStartRow = displayNameMappingStartRow;
            this.displayNameMappingEndRow = displayNameMappingEndRow;
        }
    }

    /**
     * Adds cascading data validations for all boundary columns using helper column architecture
     *
     * Architecture:
     * - Level 1 (Country): Direct list validation
     * - Level 2+ (State, District, etc.):
     *   - Helper column: Contains formula to compute the hash key from parent selections
     *   - Visible column: Data validation references helper column with simple relative reference
     *
     * This separation is critical for MS Excel compatibility because:
     * - INDIRECT("A"&ROW()) works in cell formulas but NOT in data validation formulas in Excel
     * - Simple relative references (like G3) work correctly in data validation
     */
    private void addCascadingBoundaryValidations(XSSFWorkbook workbook, Sheet sheet,
                                                 int startColumnIndex, int numLevels,
                                                 List<String> level1Boundaries,
                                                 ParentChildrenMapping mappingResult, Map<String, String> localizationMap,
                                                 List<Integer> visibleColIndices) {

        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        Sheet lookupSheet = workbook.getSheet("_h_SimpleLookup_h_");
        if (lookupSheet == null) {
            log.error("Lookup sheet not found, cannot create cascading validations");
            return;
        }

        // Create a named range for an empty cell to gracefully handle formula errors
        Name emptyListRange = workbook.getName("_h_EmptyList");
        if (emptyListRange == null) {
            emptyListRange = workbook.createName();
            emptyListRange.setNameName("_h_EmptyList");
            emptyListRange.setRefersToFormula("_h_SimpleLookup_h_!$ZZ$1"); // Point to a guaranteed empty cell
        }

        // Level 1 Validation (First visible column) - direct list
        addLevel1BoundaryValidation(workbook, sheet, dvHelper, visibleColIndices.get(0), level1Boundaries);

        // Cascading Validations for Levels 2+ using Helper Column Architecture
        // Key-to-Hash lookup table is in a SEPARATE section (columns H and I)
        // This ensures children data (cols B onwards) never overlaps with the lookup keys
        int keyHashStartRow = mappingResult.keyHashTableStartRow + 1; // Excel rows are 1-based
        int keyHashEndRow = mappingResult.keyHashTableEndRow;
        String keyColLetter = CellReference.convertNumToColString(KEY_HASH_TABLE_KEY_COLUMN);   // Column H
        String hashColLetter = CellReference.convertNumToColString(KEY_HASH_TABLE_HASH_COLUMN); // Column I

        // Lookup ranges for the separate key-to-hash table
        String lookupRangeKeys = String.format("_h_SimpleLookup_h_!$%s$%d:$%s$%d",
                keyColLetter, keyHashStartRow, keyColLetter, keyHashEndRow);
        String lookupRangeHashes = String.format("_h_SimpleLookup_h_!$%s$%d:$%s$%d",
                hashColLetter, keyHashStartRow, hashColLetter, keyHashEndRow);

        for (int level = 1; level < numLevels; level++) {
            int currentVisibleColIdx = visibleColIndices.get(level);
            // The helper column is located immediately to the left of the visible column
            int helperColIdx = currentVisibleColIdx - 1;

            // Build the CONCATENATE formula to create the lookup key from previous selections
            // e.g., for District: CONCATENATE(Country, "#", State)
            StringBuilder keyBuilder = new StringBuilder();
            for (int i = 0; i < level; i++) {
                if (i > 0) keyBuilder.append(", \"").append(BOUNDARY_SEPARATOR).append("\", ");
                String colLetter = CellReference.convertNumToColString(visibleColIndices.get(i));
                // Use INDIRECT("A"&ROW()) to get value from the correct row dynamically
                // This works in CELL FORMULAS (helper column) but NOT in data validation
                keyBuilder.append("INDIRECT(\"").append(colLetter).append("\"&ROW())");
            }

            // Helper formula: finds the concatenated key in Col H (keys), returns hash from Col I (hashes)
            // Uses the SEPARATE key-to-hash table, NOT the children rows
            String helperFormula = String.format("IFERROR(INDEX(%s,MATCH(CONCATENATE(%s),%s,0)),\"\")",
                                                 lookupRangeHashes, keyBuilder.toString(), lookupRangeKeys);

            // Data validation formula: simple relative reference to helper column
            // Uses row 3 as template - Excel automatically adjusts for each row
            String helperColLetter = CellReference.convertNumToColString(helperColIdx);
            String validationFormula = String.format("INDIRECT(IF(%s3<>\"\", %s3 & \"%s\", \"_h_EmptyList\"))",
                                                     helperColLetter, helperColLetter, LIST_SUFFIX);

            // Apply the helper formula to every cell in the helper column
            for (int r = 2; r <= config.getExcelRowLimit(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) row = sheet.createRow(r);
                Cell helperCell = row.createCell(helperColIdx);
                helperCell.setCellFormula(helperFormula);
            }

            // Apply data validation to the visible column
            CellRangeAddressList validationRange = new CellRangeAddressList(2, config.getExcelRowLimit(), currentVisibleColIdx, currentVisibleColIdx);
            DataValidationConstraint dvConstraint = dvHelper.createFormulaListConstraint(validationFormula);
            DataValidation validation = dvHelper.createValidation(dvConstraint, validationRange);
            validation.setShowErrorBox(false);
            validation.setEmptyCellAllowed(true);
            sheet.addValidationData(validation);

            log.debug("Applied helper-based cascade validation for level {} (visible col: {}, helper col: {})",
                     level, currentVisibleColIdx, helperColIdx);
        }

        log.info("Finished applying helper-column based cascading validations for {} levels.", numLevels);
    }

    /**
     * Add level 1 boundary validation using range reference to avoid 255-character limit
     */
    private void addLevel1BoundaryValidation(XSSFWorkbook workbook, Sheet sheet, DataValidationHelper dvHelper,
                                             int startColumnIndex, List<String> level1Boundaries) {
        try {
            Sheet lookupSheet = workbook.getSheet("_h_SimpleLookup_h_");
            if (lookupSheet == null) {
                log.error("Lookup sheet not found, cannot create level1 boundary validation");
                return;
            }

            int totalLength = level1Boundaries.stream()
                    .mapToInt(s -> s.length() + 1)
                    .sum();

            if (totalLength <= 250) {
                // Use explicit list for small lists
                String[] level1Array = level1Boundaries.toArray(new String[0]);
                int actualDataRows = ExcelUtil.findActualLastRowWithData(sheet) + 1;
                int maxRow = Math.max(actualDataRows, config.getExcelRowLimit());
                CellRangeAddressList level1Range = new CellRangeAddressList(2, maxRow, startColumnIndex, startColumnIndex);
                DataValidationConstraint level1Constraint = dvHelper.createExplicitListConstraint(level1Array);
                DataValidation level1Validation = dvHelper.createValidation(level1Constraint, level1Range);
                level1Validation.setShowErrorBox(true);
                sheet.addValidationData(level1Validation);
                log.info("Applied explicit list validation for {} level1 boundaries", level1Boundaries.size());
            } else {
                // Use range reference for large lists
                addLevel1BoundariesRangeValidation(workbook, sheet, dvHelper, startColumnIndex, level1Boundaries, lookupSheet);
            }

        } catch (Exception e) {
            log.error("Failed to add level1 boundary validation: {}", e.getMessage(), e);
            log.warn("Continuing without level1 boundary validation due to error");
        }
    }

    /**
     * Add level 1 boundaries to lookup sheet and create range-based validation
     */
    private void addLevel1BoundariesRangeValidation(XSSFWorkbook workbook, Sheet sheet, DataValidationHelper dvHelper,
                                                    int startColumnIndex, List<String> level1Boundaries, Sheet lookupSheet) {

        int startRow = Math.max(ExcelUtil.findActualLastRowWithData(lookupSheet) + 5, 1);

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
            Name existingRange = workbook.getName(rangeName);
            if (existingRange != null) {
                workbook.removeName(existingRange);
            }

            Name level1Range = workbook.createName();
            level1Range.setNameName(rangeName);
            String rangeFormula = "_h_SimpleLookup_h_!$G$" + (startRow + 1) + ":$G$" + (startRow + level1Boundaries.size());
            level1Range.setRefersToFormula(rangeFormula);

            log.info("Created named range '{}' for {} level1 boundaries", rangeName, level1Boundaries.size());

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
            try {
                String directFormula = "_h_SimpleLookup_h_!$G$" + (startRow + 1) + ":$G$" + (startRow + level1Boundaries.size());
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
     * Populates existing data rows with boundary information
     */
    private int populateExistingDataWithBoundaries(Sheet sheet, List<Map<String, Object>> existingData,
                                                   int lastSchemaCol, int numCascadingColumns, int boundaryCodeColIndex,
                                                   List<String> levelTypes, String hierarchyType,
                                                   List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                                   Map<String, String> localizationMap,
                                                   CellStyle unlocked, CellStyle formulaStyle,
                                                   List<Integer> visibleColIndices,
                                                   int displayNameMappingStartRow, int displayNameMappingEndRow) {

        int rowsPopulated = 0;

        for (int i = 0; i < existingData.size() && i < (config.getExcelRowLimit() - 2); i++) {
            Map<String, Object> dataRow = existingData.get(i);
            int excelRowIndex = 2 + i;

            Row row = sheet.getRow(excelRowIndex);
            if (row == null) {
                row = sheet.createRow(excelRowIndex);
            }

            String boundaryCode = extractBoundaryCodeFromData(dataRow);
            List<String> boundaryPath = null;

            if (boundaryCode != null && !boundaryCode.isEmpty()) {
                for (BoundaryUtil.BoundaryRowData boundaryRowData : filteredBoundaries) {
                    if (boundaryCode.equals(boundaryRowData.getLastLevelCode())) {
                        boundaryPath = boundaryRowData.getBoundaryPath();
                        break;
                    }
                }
            }

            // Populate visible boundary columns
            for (int j = 0; j < visibleColIndices.size(); j++) {
                int colIdx = visibleColIndices.get(j);
                Cell cell = row.getCell(colIdx);
                if (cell == null) {
                    cell = row.createCell(colIdx);
                }
                cell.setCellStyle(unlocked);

                if (boundaryPath != null && j < boundaryPath.size()) {
                    String boundaryCodeAtLevel = boundaryPath.get(j);
                    if (boundaryCodeAtLevel != null && !boundaryCodeAtLevel.isEmpty()) {
                        String displayName = localizationMap.getOrDefault(boundaryCodeAtLevel, boundaryCodeAtLevel);
                        cell.setCellValue(displayName);
                    }
                }
            }

            // Set boundary code formula in hidden column
            Cell boundaryCodeCell = row.getCell(boundaryCodeColIndex);
            if (boundaryCodeCell == null) {
                boundaryCodeCell = row.createCell(boundaryCodeColIndex);
            }
            boundaryCodeCell.setCellStyle(formulaStyle);
            String boundaryCodeFormula = createBoundaryCodeFormula(excelRowIndex + 1, visibleColIndices,
                    displayNameMappingStartRow, displayNameMappingEndRow);
            boundaryCodeCell.setCellFormula(boundaryCodeFormula);

            rowsPopulated++;
        }

        log.info("Populated {} existing data rows with boundary information", rowsPopulated);
        return rowsPopulated;
    }

    /**
     * Extracts boundary code from existing data row
     */
    private String extractBoundaryCodeFromData(Map<String, Object> dataRow) {
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
