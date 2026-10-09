package org.egov.excelingestion.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.request.RequestInfo;
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
 * Architecture: NO per-row scaffold. Each cascade level's dropdown is a single data-validation
 * formula (applied to the whole column range) that resolves the parent selection to that parent's
 * children named range, evaluated by Excel/LibreOffice only when a dropdown is opened:
 *   INDIRECT(IFERROR("_hL"&MATCH(&lt;parent path&gt;, lookupKeyColumn, 0), "_h_EmptyList"))
 * - The lookup sheet holds one row per parent path: column A = display-name path key, columns B..
 *   = the children; a positional named range (_hL&lt;row&gt;) spans exactly the children cells.
 * - Relative references in the validation formula (row 3 template) auto-adjust per row in both
 *   MS Excel and LibreOffice; MATCH compares plain strings, so no name sanitization/hashing is
 *   needed for the lookup itself.
 * - The hidden boundary-code column carries VALUES only for prefilled rows; codes for user-entered
 *   rows are resolved server-side on upload ({@link BoundaryCodeResolver}) from the same lookup
 *   sheet's display-path-to-code mapping, so this util writes no per-row VLOOKUP formulas either.
 *   (The attendance-register template is the one exception: it needs the code live in the sheet so
 *   Register ID auto-fills on selection, so its generator adds that lookup itself from the range
 *   reported in {@link BoundaryColumnLayout} — deliberately not here, to keep other templates flat.)
 * This keeps the generated file size and open/parse cost independent of the row limit (previously
 * ~6 formulas x rowLimit x 2 sheets made 20k-row templates multi-MB, slow to open, and truncated
 * to 1000 scaffold rows by a LibreOffice save).
 */
@Component
@Slf4j
public class HierarchicalBoundaryUtil {

    /** Separator joining path segments in lookup keys; shared with {@link BoundaryCodeResolver}. */
    public static final String BOUNDARY_SEPARATOR = "#";
    private static final String HASH_PREFIX = "H_";
    private static final String SHA256_ALGORITHM = "SHA-256";
    /** Name of the hidden lookup sheet holding cascade children + display-path-to-code mapping. */
    public static final String LOOKUP_SHEET_NAME = "_h_SimpleLookup_h_";
    /** Positional per-parent children named ranges: _hL1, _hL2, ... (row number in the lookup sheet). */
    public static final String CHILD_LIST_NAME_PREFIX = "_hL";
    /** Named range pointing at a guaranteed-empty cell; cascade fallback when the parent is not selected. */
    public static final String EMPTY_LIST_NAME = "_h_EmptyList";
    // Display-path key -> boundary code mapping (Section 2 of the lookup sheet); read back on upload
    // by BoundaryCodeResolver to resolve codes for user-entered rows.
    public static final int CODE_MAPPING_KEY_COLUMN = 3;   // Column D: display-path key
    public static final int CODE_MAPPING_CODE_COLUMN = 4;  // Column E: boundary code

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
    public BoundaryColumnLayout addHierarchicalBoundaryColumn(XSSFWorkbook workbook, String sheetName, Map<String, String> localizationMap,
                                              List<Boundary> configuredBoundaries, String hierarchyType,
                                              String tenantId, RequestInfo requestInfo) {
        return addHierarchicalBoundaryColumnWithData(workbook, sheetName, localizationMap, configuredBoundaries,
                hierarchyType, tenantId, requestInfo, null);
    }

    /**
     * Adds cascading boundary dropdown columns to an existing sheet with existing data population
     * Creates multiple columns starting from 1st level with cascading dropdowns
     * If existingData is provided, populates the first rows with that data
     */
    public BoundaryColumnLayout addHierarchicalBoundaryColumnWithData(XSSFWorkbook workbook, String sheetName, Map<String, String> localizationMap,
                                                      List<Boundary> configuredBoundaries, String hierarchyType,
                                                      String tenantId, RequestInfo requestInfo, List<Map<String, Object>> existingData) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            log.warn("Sheet '{}' not found, cannot add hierarchical boundary column", sheetName);
            return null;
        }

        if (configuredBoundaries == null || configuredBoundaries.isEmpty()) {
            log.info("No boundaries configured for sheet '{}', skipping boundary column creation", sheetName);
            return null;
        }

        // Fetch boundary relationship data
        BoundarySearchResponse relationshipData = boundaryService.fetchBoundaryRelationship(tenantId, hierarchyType, requestInfo);
        Map<String, EnrichedBoundary> codeToEnrichedBoundary = boundaryUtil.buildCodeToBoundaryMap(relationshipData);

        // Fetch boundary hierarchy data
        BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);
        if (hierarchyData == null || hierarchyData.getBoundaryHierarchy() == null || hierarchyData.getBoundaryHierarchy().isEmpty()) {
            log.error("Boundary hierarchy data is null or empty for type: {}", hierarchyType);
            return null;
        }

        List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();
        List<String> levelTypes = hierarchyRelations.stream()
                .map(BoundaryHierarchyChild::getBoundaryType)
                .collect(Collectors.toList());

        if (levelTypes.size() < 1) {
            log.warn("Hierarchy has less than 1 level, skipping boundary column creation");
            return null;
        }

        List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                configuredBoundaries, codeToEnrichedBoundary, levelTypes);

        if (filteredBoundaries.isEmpty()) {
            log.info("No boundaries available for sheet '{}', skipping boundary column creation", sheetName);
            return null;
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

            // Add the visible column for all levels (no hidden helper columns: the cascade is
            // resolved directly inside each level's data-validation formula)
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

        Map<String, String> codeToUniqueName = buildCodeToUniqueNameMap(filteredBoundaries, localizationMap);

        Set<String> level1Boundaries = new LinkedHashSet<>();
        filteredBoundaries.forEach(b -> {
            if (b.getBoundaryPath().size() > 0 && b.getBoundaryPath().get(0) != null) {
                String code = b.getBoundaryPath().get(0);
                level1Boundaries.add(codeToUniqueName.getOrDefault(code, localizationMap.getOrDefault(code, code)));
            }
        });

        // Create the hidden sheet with all lookup data
        ParentChildrenMapping mappingResult = createCascadingBoundaryHierarchySheet(workbook, filteredBoundaries, levelTypes, localizationMap, codeToUniqueName);

        // Add validations using the helper column architecture
        addCascadingBoundaryValidations(workbook, sheet, lastSchemaCol, levelTypes.size(),
                new ArrayList<>(level1Boundaries), mappingResult, localizationMap, visibleColIndices, codeToUniqueName);

        sheet.createFreezePane(0, 2);
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);
        CellStyle formulaStyle = workbook.createCellStyle();
        formulaStyle.setLocked(true);

        int dataRowsPopulated = 0;
        if (existingData != null && !existingData.isEmpty()) {
            dataRowsPopulated = populateExistingDataWithBoundaries(sheet, existingData,
                    boundaryCodeColIndex, filteredBoundaries, localizationMap, unlocked, formulaStyle,
                    visibleColIndices, codeToUniqueName);
        }

        // The empty paste area of the VISIBLE dropdown columns used to be MATERIALIZED here as one
        // unlocked-styled cell per column per row up to excelRowLimit. Apply the unlock intent ONCE
        // per column via the sheet's DEFAULT COLUMN STYLE instead (same pattern as
        // CellProtectionManager): empty cells inherit the column default, so the paste area stays
        // editable under sheet protection without materializing a single dropdown cell.
        for (int colIdx : visibleColIndices) {
            sheet.setDefaultColumnStyle(colIdx, unlocked);
        }

        // The hidden boundary-code column carries no per-row formulas: prefilled rows got their code
        // VALUE in populateExistingDataWithBoundaries, and codes for rows the user fills later are
        // resolved server-side on upload by BoundaryCodeResolver from this workbook's own
        // display-path-to-code mapping (lookup sheet Section 2). Locking intent for the untouched
        // tail is expressed once via the column default style - no cells are materialized.
        sheet.setDefaultColumnStyle(boundaryCodeColIndex, formulaStyle);

        log.info("Added {} cascading boundary dropdown columns (no per-row scaffold).", levelTypes.size());

        return new BoundaryColumnLayout(boundaryCodeColIndex, visibleColIndices,
                mappingResult.codeMappingStartRow, mappingResult.codeMappingEndRow);
    }

    /**
     * Creates a hidden sheet with cascading boundary hierarchy
     * Single hidden lookup sheet with parent#child structure
     */
    private ParentChildrenMapping createCascadingBoundaryHierarchySheet(XSSFWorkbook workbook,
                                                                        List<BoundaryUtil.BoundaryRowData> boundaries,
                                                                        List<String> levelTypes,
                                                                        Map<String, String> localizationMap,
                                                                        Map<String, String> codeToUniqueName) {

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
            for (int i = 0; i < path.size(); i++) {
                String code = path.get(i);
                if (code != null) {
                    String displayName = codeToUniqueName.getOrDefault(code, localizationMap.getOrDefault(code, code));
                    String levelTypeDisplayName = localizationMap.getOrDefault(levelTypes.get(i), levelTypes.get(i));
                    codeToDisplayNameMap.putIfAbsent(code, displayName + " - " + levelTypeDisplayName);
                }
            }
        }

        // Second pass: build parent-children relationships
        // Use CODES for hash keys (codes are always unique, unlike display names)
        // This ensures disambiguation even when multiple boundaries share the same name
        Map<String, String> childDisplayNameMap = new HashMap<>();
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (int level = 0; level < path.size() - 1; level++) {
                if (path.get(level) != null && path.get(level + 1) != null) {
                    StringBuilder keyBuilder = new StringBuilder();
                    for (int i = 0; i <= level; i++) {
                        if (i > 0) keyBuilder.append(BOUNDARY_SEPARATOR);
                        // Use CODES for hash key - codes are guaranteed to be unique
                        keyBuilder.append(path.get(i));
                    }
                    String originalKey = keyBuilder.toString();
                    String hashedKey = createHashedKey(originalKey);
                    hashToOriginalKeyMap.put(hashedKey, originalKey);

                    // Store child code for later lookup
                    String childCode = path.get(level + 1);
                    parentChildrenCodeMap.computeIfAbsent(hashedKey, k -> new LinkedHashSet<>()).add(childCode);

                    // Children dropdown should show plain display name WITHOUT type suffix
                    if (!childDisplayNameMap.containsKey(childCode)) {
                        childDisplayNameMap.put(childCode, codeToUniqueName.getOrDefault(childCode, localizationMap.getOrDefault(childCode, childCode)));
                    }
                }
            }
        }

        // Build parentChildrenMap with plain display names (no type suffix) for dropdown visibility
        for (Map.Entry<String, Set<String>> entry : parentChildrenCodeMap.entrySet()) {
            String hashedKey = entry.getKey();
            Set<String> codes = entry.getValue();
            Set<String> displayNames = new LinkedHashSet<>();
            for (String code : codes) {
                displayNames.add(childDisplayNameMap.get(code));
            }
            parentChildrenMap.put(hashedKey, displayNames);
        }

        // Stale positional names from any previous generation into this workbook would point at the
        // deleted lookup sheet - remove them all before recreating (names are workbook-scoped).
        List<Name> staleNames = new ArrayList<>();
        for (Name name : workbook.getAllNames()) {
            if (name.getNameName() != null && name.getNameName().startsWith(CHILD_LIST_NAME_PREFIX)) {
                staleNames.add(name);
            }
        }
        staleNames.forEach(workbook::removeName);

        // SECTION 1: Children data (Rows 1-N), one row per parent path.
        // Structure: Column A = display-name path key (what the sheet's parent cells contain, joined
        // with '#'), Columns B onwards = children display names. A positional named range (_hL<row>)
        // spans exactly the children cells; the data-validation formula finds the row by MATCHing the
        // parent path against column A and INDIRECTs to the positional name. Display-path keys are
        // unique because sibling names are uniquified per parent (buildCodeToUniqueNameMap).
        int rowNum = 0;
        for (Map.Entry<String, Set<String>> entry : parentChildrenMap.entrySet()) {
            String hashedKey = entry.getKey();
            Set<String> children = entry.getValue();

            // Display-name version of this parent's code path - the MATCH key.
            StringBuilder displayKeyBuilder = new StringBuilder();
            String[] codes = hashToOriginalKeyMap.get(hashedKey).split(BOUNDARY_SEPARATOR, -1);
            for (int i = 0; i < codes.length; i++) {
                if (i > 0) displayKeyBuilder.append(BOUNDARY_SEPARATOR);
                displayKeyBuilder.append(codeToUniqueName.getOrDefault(codes[i], localizationMap.getOrDefault(codes[i], codes[i])));
            }

            Row row = lookupSheet.createRow(rowNum);
            row.createCell(0).setCellValue(displayKeyBuilder.toString());

            // Columns B onwards: Children ONLY (one per column)
            int col = 1;
            for (String child : children) {
                row.createCell(col++).setCellValue(child);
            }

            // Positional named range over exactly the children cells of this row
            String rangeName = CHILD_LIST_NAME_PREFIX + (rowNum + 1);
            try {
                Name childrenRange = workbook.createName();
                childrenRange.setNameName(rangeName);
                String rangeFormula = String.format("%s!$B$%d:$%s$%d",
                        LOOKUP_SHEET_NAME,
                        rowNum + 1,
                        CellReference.convertNumToColString(col - 1),
                        rowNum + 1);
                childrenRange.setRefersToFormula(rangeFormula);
            } catch (Exception e) {
                log.error("Error creating children named range {}: {}", rangeName, e.getMessage());
            }

            rowNum++;
        }
        int childrenSectionEndRow = rowNum;

        // SECTION 2: Display name combination to code mapping.
        // Structure: Column D = Combination String, Column E = Code (plain display names, no type
        // suffix, matching what the dropdowns write into the sheet). Read back on upload by
        // BoundaryCodeResolver to resolve boundary codes for user-entered rows.
        rowNum += 2; // Add spacing
        int displayNameMappingStartRow = rowNum;

        // Build map of combination strings to codes - use plain display names for dropdown matching
        Map<String, String> comboToCodeMap = new HashMap<>();
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            StringBuilder comb = new StringBuilder();
            for (String code : boundary.getBoundaryPath()) {
                if (code != null) {
                    if (comb.length() > 0) comb.append(BOUNDARY_SEPARATOR);
                    // Use plain display name (without type suffix) to match dropdown values
                    comb.append(codeToUniqueName.getOrDefault(code, localizationMap.getOrDefault(code, code)));
                    comboToCodeMap.put(comb.toString(), code);
                }
            }
        }

        for (Map.Entry<String, String> entry : comboToCodeMap.entrySet()) {
            Row mappingRow = lookupSheet.createRow(rowNum++);
            mappingRow.createCell(CODE_MAPPING_KEY_COLUMN).setCellValue(entry.getKey());   // Column D: Combination String
            mappingRow.createCell(CODE_MAPPING_CODE_COLUMN).setCellValue(entry.getValue()); // Column E: Code
        }
        int displayNameMappingEndRow = rowNum;

        log.info("Created cascading boundary lookup sheet: {} children rows, {} display-name mappings (rows {}-{})",
                childrenSectionEndRow, comboToCodeMap.size(), displayNameMappingStartRow + 1, displayNameMappingEndRow);

        return new ParentChildrenMapping(parentChildrenMap, childrenSectionEndRow,
                comboToCodeMap.isEmpty() ? 0 : displayNameMappingStartRow + 1,
                comboToCodeMap.isEmpty() ? 0 : displayNameMappingEndRow);
    }

    /**
     * Helper class to hold parent-children mapping results
     */
    private static class ParentChildrenMapping {
        final Map<String, Set<String>> parentChildrenMap;
        /** Number of Section-1 rows: the MATCH range of every cascade validation is $A$1:$A$<this>. */
        final int childrenSectionEndRow;
        /** 1-based inclusive row bounds of Section 2 (display-path -> code). 0 when empty. */
        final int codeMappingStartRow;
        final int codeMappingEndRow;

        ParentChildrenMapping(Map<String, Set<String>> parentChildrenMap, int childrenSectionEndRow,
                              int codeMappingStartRow, int codeMappingEndRow) {
            this.parentChildrenMap = parentChildrenMap;
            this.childrenSectionEndRow = childrenSectionEndRow;
            this.codeMappingStartRow = codeMappingStartRow;
            this.codeMappingEndRow = codeMappingEndRow;
        }
    }

    /**
     * Layout of the boundary columns a generator just had added, so a caller can attach its own
     * per-row formulas without re-deriving column positions or the lookup sheet's section bounds.
     * Deliberately data-only: no formulas are written here, so templates that do not ask for them
     * (facility / user / unified-console) are unaffected.
     */
    @Getter
    public static class BoundaryColumnLayout {
        private final int boundaryCodeColIndex;
        private final List<Integer> visibleBoundaryColIndices;
        /** 1-based inclusive row bounds of the display-path -> code mapping; 0 when there is none. */
        private final int codeMappingStartRow;
        private final int codeMappingEndRow;

        public BoundaryColumnLayout(int boundaryCodeColIndex, List<Integer> visibleBoundaryColIndices,
                                    int codeMappingStartRow, int codeMappingEndRow) {
            this.boundaryCodeColIndex = boundaryCodeColIndex;
            // Copy: the caller hands over its live working list, and this layout is a value.
            this.visibleBoundaryColIndices = visibleBoundaryColIndices == null
                    ? List.of() : List.copyOf(visibleBoundaryColIndices);
            this.codeMappingStartRow = codeMappingStartRow;
            this.codeMappingEndRow = codeMappingEndRow;
        }
    }

    /**
     * Adds cascading data validations for all boundary columns.
     *
     * Architecture:
     * - Level 1 (Country): Direct list validation
     * - Level 2+ (State, District, etc.): ONE data-validation formula per column (no per-row cells):
     *     INDIRECT(IFERROR("_hL"&MATCH(&lt;parent path&gt;, lookup!$A$1:$A$N, 0), "_h_EmptyList"))
     *   The parent path is built from relative references to the parent VISIBLE cells (row 3 is the
     *   template row of the applied range, so Excel/LibreOffice auto-adjust it per row). MATCH finds
     *   the parent's row in the lookup sheet; the positional name _hL&lt;row&gt; spans exactly that
     *   parent's children. An unselected/invalid parent chain makes MATCH fail, and IFERROR degrades
     *   the name to _h_EmptyList (an empty list), exactly like the old helper-based behaviour.
     *   The formula is evaluated only when a dropdown is opened - nothing is computed on file open.
     */
    private void addCascadingBoundaryValidations(XSSFWorkbook workbook, Sheet sheet,
                                                 int startColumnIndex, int numLevels,
                                                 List<String> level1Boundaries,
                                                 ParentChildrenMapping mappingResult, Map<String, String> localizationMap,
                                                 List<Integer> visibleColIndices,
                                                 Map<String, String> codeToUniqueName) {

        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        Sheet lookupSheet = workbook.getSheet(LOOKUP_SHEET_NAME);
        if (lookupSheet == null) {
            log.error("Lookup sheet not found, cannot create cascading validations");
            return;
        }

        // Create a named range for an empty cell to gracefully handle formula errors
        Name emptyListRange = workbook.getName(EMPTY_LIST_NAME);
        if (emptyListRange == null) {
            emptyListRange = workbook.createName();
            emptyListRange.setNameName(EMPTY_LIST_NAME);
            emptyListRange.setRefersToFormula(LOOKUP_SHEET_NAME + "!$ZZ$1"); // Point to a guaranteed empty cell
        }

        // Level 1 Validation (First visible column) - direct list
        addLevel1BoundaryValidation(workbook, sheet, dvHelper, visibleColIndices.get(0), level1Boundaries);

        if (mappingResult.childrenSectionEndRow < 1) {
            log.info("No parent-children rows in the lookup sheet; skipping cascade validations for {} levels.",
                    numLevels - 1);
            return;
        }
        String matchKeyRange = String.format("%s!$A$1:$A$%d", LOOKUP_SHEET_NAME, mappingResult.childrenSectionEndRow);

        for (int level = 1; level < numLevels; level++) {
            int currentVisibleColIdx = visibleColIndices.get(level);

            // Parent path from relative refs to the parent visible cells, e.g. A3&"#"&B3. Row 3 is the
            // first row of the applied range, so the references shift per row automatically.
            StringBuilder parentPath = new StringBuilder();
            for (int i = 0; i < level; i++) {
                if (i > 0) parentPath.append("&\"").append(BOUNDARY_SEPARATOR).append("\"&");
                parentPath.append(CellReference.convertNumToColString(visibleColIndices.get(i))).append("3");
            }
            String validationFormula = String.format(
                    "INDIRECT(IFERROR(\"%s\"&MATCH(%s,%s,0),\"%s\"))",
                    CHILD_LIST_NAME_PREFIX, parentPath, matchKeyRange, EMPTY_LIST_NAME);

            // Apply data validation to the visible column
            // last row = excelRowLimit + 1 (data starts at row index 2) so all excelRowLimit paste rows are covered
            CellRangeAddressList validationRange = new CellRangeAddressList(2, config.getExcelRowLimit() + 1, currentVisibleColIdx, currentVisibleColIdx);
            DataValidationConstraint dvConstraint = dvHelper.createFormulaListConstraint(validationFormula);
            DataValidation validation = dvHelper.createValidation(dvConstraint, validationRange);
            validation.setShowErrorBox(false);
            validation.setEmptyCellAllowed(true);
            sheet.addValidationData(validation);

            log.debug("Applied cascade validation for level {} (visible col: {}): {}",
                     level, currentVisibleColIdx, validationFormula);
        }

        log.info("Finished applying cascading validations for {} levels (no helper columns).", numLevels);
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
                int maxRow = Math.max(actualDataRows, config.getExcelRowLimit() + 1);
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
            int maxRow = Math.max(actualDataRows, config.getExcelRowLimit() + 1);
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
                int maxRow = Math.max(actualDataRows, config.getExcelRowLimit() + 1);
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
                                                   int boundaryCodeColIndex,
                                                   List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                                   Map<String, String> localizationMap,
                                                   CellStyle unlocked, CellStyle formulaStyle,
                                                   List<Integer> visibleColIndices,
                                                   Map<String, String> codeToUniqueName) {

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
                        String displayName = codeToUniqueName.getOrDefault(boundaryCodeAtLevel, localizationMap.getOrDefault(boundaryCodeAtLevel, boundaryCodeAtLevel));
                        cell.setCellValue(displayName);
                    }
                }
            }

            // Write the boundary code VALUE into the hidden column (the code is known server-side;
            // no per-row formula). Left blank when the code did not resolve to a configured boundary
            // path - identical to what the old VLOOKUP formula produced for unmatched selections.
            Cell boundaryCodeCell = row.getCell(boundaryCodeColIndex);
            if (boundaryCodeCell == null) {
                boundaryCodeCell = row.createCell(boundaryCodeColIndex);
            }
            boundaryCodeCell.setCellStyle(formulaStyle);
            if (boundaryPath != null && boundaryCode != null && !boundaryCode.isEmpty()) {
                boundaryCodeCell.setCellValue(boundaryCode);
            }

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
                "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY",
                "boundaryCode",
                "boundary_code",
                "BOUNDARY_CODE",
                "administrativeUnit"
        };

        for (String field : possibleFields) {
            Object value = dataRow.get(field);
            if (value != null && !value.toString().isEmpty()) {
                String code = value.toString().trim();
                int comma = code.indexOf(',');
                return comma >= 0 ? code.substring(0, comma).trim() : code;
            }
        }

        return null;
    }

    private Map<String, String> buildCodeToUniqueNameMap(List<BoundaryUtil.BoundaryRowData> boundaries,
                                                         Map<String, String> localizationMap) {
        Map<String, String> codeToUniqueName = new HashMap<>();
        Map<String, Set<String>> parentHashToNames = new HashMap<>();
        for (BoundaryUtil.BoundaryRowData boundary : boundaries) {
            List<String> path = boundary.getBoundaryPath();
            StringBuilder pathKeyBuilder = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                String code = path.get(i);
                if (code == null) continue;
                if (!codeToUniqueName.containsKey(code)) {
                    String originalName = localizationMap.getOrDefault(code, code);
                    String parentKey = i == 0 ? "ROOT" : createHashedKey(pathKeyBuilder.toString());
                    Set<String> usedNames = parentHashToNames.computeIfAbsent(parentKey, k -> new HashSet<>());
                    String uniqueName = originalName;
                    while (usedNames.contains(uniqueName)) { uniqueName += "\u200B"; }
                    usedNames.add(uniqueName);
                    codeToUniqueName.put(code, uniqueName);
                }
                if (i > 0) pathKeyBuilder.append(BOUNDARY_SEPARATOR);
                pathKeyBuilder.append(code);
            }
        }
        return codeToUniqueName;
    }
}
