package org.egov.excelingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces pre-filled-cell immutability for "unprotected join mode" templates WITHOUT trusting the
 * uploaded file's pre-filled cells.
 *
 * <p>Instead of comparing each cell, it reconstructs the immutable part of every existing row from the
 * authoritative baseline (the original generated file, re-read from filestore) and grafts it onto the
 * user's editable inputs, joined by a hidden server-stamped row-id. The file's pre-filled cells are
 * never read for content - so there is nothing to verify and no value-normalization to get wrong.
 *
 * <p>Fail-closed: if the file claims to be a generated template (carries a generationId) but the
 * baseline can't be found, identity doesn't match, a row carries an unknown id, or a baseline row was
 * deleted, processing is aborted with a clear error.
 */
@Service
@Slf4j
public class ImmutableJoinService {

    private final GeneratedFileRepository generatedFileRepository;
    private final FileStoreService fileStoreService;
    private final ExcelUtil excelUtil;
    private final SchemaColumnDefUtil schemaColumnDefUtil;
    private final ObjectMapper objectMapper;
    private final CustomExceptionHandler exceptionHandler;
    private final ExcelIngestionConfig config;

    private static final String MULTISELECT_MARKER = "_MULTISELECT_";

    /** Columns never restored from the baseline even if the schema marks them immutable. */
    private static final Set<String> ALWAYS_EXCLUDED = Set.of(
            ProcessingConstants.USER_USAGE_COLUMN_KEY,   // user active/inactive - intentionally editable
            ProcessingConstants.ROW_ID_COLUMN_NAME,      // the join key itself
            ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY,// computed VLOOKUP formula, not data
            ProcessingConstants.REGISTER_ID_COLUMN_KEY); // computed formula, not data

    public ImmutableJoinService(GeneratedFileRepository generatedFileRepository,
                                FileStoreService fileStoreService,
                                ExcelUtil excelUtil,
                                SchemaColumnDefUtil schemaColumnDefUtil,
                                ObjectMapper objectMapper,
                                CustomExceptionHandler exceptionHandler,
                                ExcelIngestionConfig config) {
        this.generatedFileRepository = generatedFileRepository;
        this.fileStoreService = fileStoreService;
        this.excelUtil = excelUtil;
        this.schemaColumnDefUtil = schemaColumnDefUtil;
        this.objectMapper = objectMapper;
        this.exceptionHandler = exceptionHandler;
        this.config = config;
    }

    /**
     * Reconstructs authoritative pre-filled values onto the uploaded workbook's in-memory data.
     * Mutates the shared cached row maps so all downstream validation/processing/persistence see
     * authoritative data. No-op for legacy/protected files (no embedded generationId).
     *
     * @param sheetNameToSchema visible sheet name -> its MDMS schema map (resolved by the caller)
     * @return per visible sheet, the set of "always-immutable" columns that were reconstructed from the
     *         baseline onto existing rows. Downstream validation uses this to skip re-validating cells it
     *         did not let the user change. Empty map when the feature is inactive / legacy / no-op.
     */
    public Map<String, Set<String>> applyImmutableBaseline(Workbook uploadedWorkbook, ProcessResource resource,
                                       Map<String, Map<String, Object>> sheetNameToSchema) {
        // Scope: only the join-mode template families (unified-console, attendanceRegister,
        // attendanceRegisterAttendee) use join-mode, and only when the master switch is on. Any other
        // type is processed as before, with no baseline reconstruction.
        if (!config.isImmutableEnforce()
                || !ProcessingConstants.isJoinModeType(resource.getType())) {
            return Collections.emptyMap();
        }

        // 1. Read the embedded generationId.
        String generationId = readGenerationId(uploadedWorkbook);
        if (generationId == null || generationId.trim().isEmpty()) {
            // No embedded identity. If the file still carries the hidden row-id column, the metadata
            // sheet was stripped (tampering) -> fail closed. A genuine legacy/protected file (generated
            // before join mode) has neither id nor row-id column -> no-op (graceful migration).
            if (hasRowIdColumn(uploadedWorkbook)) {
                exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_MISSING_GENERATION_ID,
                        ErrorConstants.IMMUTABLE_MISSING_GENERATION_ID_MESSAGE);
            }
            log.debug("No {} generationId and no row-id column; treating as a legacy file, skipping join",
                    GenerationConstants.META_SHEET_NAME);
            return Collections.emptyMap();
        }
        generationId = generationId.trim();
        log.info("Applying immutable-baseline join for generationId {}", generationId);

        // 2. Resolve the baseline generation record by id.
        GenerateResource baselineGen;
        try {
            baselineGen = generatedFileRepository.findByGenerationId(generationId, resource.getTenantId());
        } catch (InvalidTenantIdException e) {
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_BASELINE_NOT_FOUND,
                    ErrorConstants.IMMUTABLE_BASELINE_NOT_FOUND_MESSAGE.replace("{0}", generationId), e);
            return Collections.emptyMap();
        }
        if (baselineGen == null || baselineGen.getFileStoreId() == null
                || baselineGen.getFileStoreId().isEmpty()) {
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_BASELINE_NOT_FOUND,
                    ErrorConstants.IMMUTABLE_BASELINE_NOT_FOUND_MESSAGE.replace("{0}", generationId));
            return Collections.emptyMap();
        }

        // 3. Identity cross-check: the baseline must belong to the SAME reference (campaign) as this
        // upload. We deliberately do NOT compare type: the generation type (e.g. "unified-console") and
        // the processing type (e.g. "unified-console-validation"/"-parse") differ by design, so an
        // equality check would falsely reject every legitimate upload. The generationId (unguessable,
        // looked up by id + tenant) plus the referenceId match are the identity guarantee.
        if (!equalsNullSafe(baselineGen.getReferenceId(), resource.getReferenceId())) {
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_IDENTITY_MISMATCH,
                    ErrorConstants.IMMUTABLE_IDENTITY_MISMATCH_MESSAGE);
            return Collections.emptyMap();
        }

        // 4. Download + parse the baseline, then join per sheet. Collect, per sheet, the always-immutable
        // columns we reconstructed onto existing rows so validation can skip re-checking those cells.
        Map<String, Set<String>> immutableColumnsBySheet = new HashMap<>();
        try (Workbook baselineWorkbook = fileStoreService.downloadExcelFromFileStore(
                baselineGen.getFileStoreId(), resource.getTenantId())) {

            for (Map.Entry<String, Map<String, Object>> entry : sheetNameToSchema.entrySet()) {
                Set<String> restored = joinSheet(entry.getKey(), entry.getValue(), uploadedWorkbook, baselineWorkbook,
                        baselineGen.getFileStoreId(), resource);
                if (!restored.isEmpty()) {
                    immutableColumnsBySheet.put(entry.getKey(), restored);
                }
            }
        } catch (org.egov.tracer.model.CustomException ce) {
            throw ce; // fail-closed business errors propagate as-is
        } catch (Exception e) {
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_BASELINE_READ_ERROR,
                    ErrorConstants.IMMUTABLE_BASELINE_READ_ERROR_MESSAGE, e);
        }
        return immutableColumnsBySheet;
    }

    /**
     * Joins one sheet's immutable columns from the baseline onto the uploaded rows.
     *
     * @return the set of always-immutable column names that were reconstructed onto existing rows of
     *         this sheet (empty if nothing was reconstructed). freezeColumnIfFilled columns are excluded
     *         because they are restored only conditionally (per cell), so they must still be validated.
     */
    private Set<String> joinSheet(String sheetName, Map<String, Object> schemaMap,
                           Workbook uploadedWorkbook, Workbook baselineWorkbook,
                           String baselineFileStoreId, ProcessResource resource) {
        Sheet uploadedSheet = uploadedWorkbook.getSheet(sheetName);
        Sheet baselineSheet = baselineWorkbook.getSheet(sheetName);
        if (uploadedSheet == null || baselineSheet == null) {
            return Collections.emptySet(); // nothing to reconstruct for this sheet
        }

        ImmutableColumns immutable = deriveImmutableColumns(schemaMap);
        if (immutable.isEmpty()) {
            return Collections.emptySet();
        }

        // Uploaded list is the SHARED @Cacheable instance (keyed on the uploaded fileStoreId) reused by
        // validation/processing/persistence - mutating its row maps propagates everywhere. Baseline is
        // cached under its own fileStoreId so the two never collide.
        List<Map<String, Object>> uploadedRows = excelUtil.convertSheetToMapListCached(
                resource.getFileStoreId(), sheetName, uploadedSheet);
        List<Map<String, Object>> baselineRows = excelUtil.convertSheetToMapListCached(
                baselineFileStoreId, sheetName, baselineSheet);

        Map<String, Map<String, Object>> baselineByRowId = new HashMap<>();
        for (Map<String, Object> baseRow : baselineRows) {
            String rid = trimToNull(ExcelUtil.getValueAsString(baseRow.get(ProcessingConstants.ROW_ID_COLUMN_NAME)));
            if (rid != null) {
                baselineByRowId.put(rid, baseRow);
            }
        }
        if (baselineByRowId.isEmpty()) {
            return Collections.emptySet(); // baseline has no stamped rows (headers-only / not a join-mode sheet)
        }

        // Track the always-immutable parent columns we ACTUALLY overwrote from the baseline. This is what
        // validation is allowed to skip - never the full schema-derived alwaysRestore set, because a
        // column the current schema marks immutable but that is absent from the (older) baseline is NOT
        // reconstructed here and must still be validated.
        Set<String> reconstructedColumns = new HashSet<>();

        // Boundary/hierarchy columns (e.g. "NEWTEST00222_COUNTRY") are generated dynamically per
        // hierarchy and are NOT part of the MDMS schema, so deriveImmutableColumns never sees them.
        // Treat any prefilled boundary cell as freezeColumnIfFilled: lock the value the server prefilled
        // at generation, while empty levels stay editable for the user to select.
        String hierarchyPrefix = (resource.getHierarchyType() == null || resource.getHierarchyType().isEmpty())
                ? null : (resource.getHierarchyType().toUpperCase() + "_");

        Set<String> seen = new HashSet<>();
        for (Map<String, Object> upRow : uploadedRows) {
            String rid = trimToNull(ExcelUtil.getValueAsString(upRow.get(ProcessingConstants.ROW_ID_COLUMN_NAME)));
            if (rid == null) {
                continue; // new row -> trust the file entirely
            }
            Map<String, Object> baseRow = baselineByRowId.get(rid);
            if (baseRow == null) {
                // row-id present but unknown to baseline -> a forged/disguised existing row
                exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_UNKNOWN_ROW_ID,
                        ErrorConstants.IMMUTABLE_UNKNOWN_ROW_ID_MESSAGE.replace("{0}", sheetName));
            }
            if (!seen.add(rid)) {
                // Two uploaded rows claim the same baseline identity (a duplicated pre-filled row).
                exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_DUPLICATE_ROW_ID,
                        ErrorConstants.IMMUTABLE_DUPLICATE_ROW_ID_MESSAGE.replace("{0}", sheetName));
            }
            // Reconstruct every immutable column AND its expanded _MULTISELECT_* child columns from the
            // baseline, overwriting whatever the file contains. We iterate the BASELINE row's own keys so
            // multi-select child cells are covered too (the parser keeps the per-child _MULTISELECT_n keys
            // in addition to populating the collapsed parent value).
            // The boundary code / attendance register id are FORMULA cells derived from the boundary
            // selection columns, evaluated at parse time from the UPLOADED selections. We capture their
            // authoritative baseline values and restore them after the loop, but only if the user did not
            // deepen the boundary path (see below) - otherwise we'd clobber a legitimate deeper selection.
            Object baselineBoundaryCode = null;
            Object baselineRegisterId = null;
            boolean userDeepenedBoundary = false;

            for (Map.Entry<String, Object> baseEntry : baseRow.entrySet()) {
                String col = baseEntry.getKey();
                if (ProcessingConstants.ACTUAL_ROW_NUMBER_KEY.equals(col)) {
                    continue; // keep the uploaded row's own row number (used for error reporting)
                }
                String parent = parentColumnOf(col);
                if (ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY.equals(parent)) {
                    baselineBoundaryCode = baseEntry.getValue();
                    continue; // handled after the loop
                }
                if (ProcessingConstants.REGISTER_ID_COLUMN_KEY.equals(parent)) {
                    baselineRegisterId = baseEntry.getValue();
                    continue; // handled after the loop
                }
                boolean baseFilled = trimToNull(ExcelUtil.getValueAsString(baseEntry.getValue())) != null;
                if (immutable.alwaysRestore.contains(parent)) {
                    upRow.put(col, baseEntry.getValue());
                    reconstructedColumns.add(parent);
                } else if (immutable.restoreIfBaselineFilled.contains(parent) && baseFilled) {
                    // freezeColumnIfFilled: immutable only where the baseline actually had a value.
                    upRow.put(col, baseEntry.getValue());
                } else if (hierarchyPrefix != null
                        && parent.toUpperCase().startsWith(hierarchyPrefix)
                        && !isExcluded(parent)) {
                    // Dynamic boundary/hierarchy column. Lock the prefilled level (freezeColumnIfFilled);
                    // an empty baseline level the user filled means the user picked a deeper boundary.
                    if (baseFilled) {
                        upRow.put(col, baseEntry.getValue());
                    } else if (trimToNull(ExcelUtil.getValueAsString(upRow.get(col))) != null) {
                        userDeepenedBoundary = true;
                    }
                }
            }

            // Restore the derived boundary code / register id from the baseline ONLY when the boundary
            // path is unchanged (no deeper level added). Then the baseline value is authoritative - this
            // also corrects a code that the sheet formula mis-evaluated from a tampered locked selection.
            // If the user legitimately deepened an empty level, keep the formula-computed code instead.
            if (!userDeepenedBoundary) {
                if (trimToNull(ExcelUtil.getValueAsString(baselineBoundaryCode)) != null) {
                    upRow.put(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, baselineBoundaryCode);
                }
                if (trimToNull(ExcelUtil.getValueAsString(baselineRegisterId)) != null) {
                    upRow.put(ProcessingConstants.REGISTER_ID_COLUMN_KEY, baselineRegisterId);
                }
            }
        }

        // Orphan detection: a baseline row-id missing from the upload means a pre-filled row was
        // deleted (or its id wiped to disguise an edit as a new row).
        if (seen.size() < baselineByRowId.size()) {
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_ORPHAN_ROWS,
                    ErrorConstants.IMMUTABLE_ORPHAN_ROWS_MESSAGE.replace("{0}", sheetName));
        }

        log.info("Immutable-baseline join applied on sheet '{}': {} existing rows reconstructed from baseline",
                sheetName, seen.size());

        // Report ONLY the columns actually reconstructed from the baseline so validation can skip
        // re-checking exactly those cells on existing rows. (restoreIfBaselineFilled is intentionally
        // excluded - it is applied per-cell; and a current-schema immutable column missing from the
        // baseline is excluded too, so it is still validated.)
        return reconstructedColumns;
    }

    /**
     * Derives the immutable column set from the schema using the SAME source as generation
     * ({@link SchemaColumnDefUtil}), so the two sides always agree on which columns are immutable.
     */
    private ImmutableColumns deriveImmutableColumns(Map<String, Object> schemaMap) {
        ImmutableColumns result = new ImmutableColumns();
        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(schemaMap);
        } catch (Exception e) {
            log.warn("Could not serialize schema for immutable-column derivation: {}", e.getMessage());
            return result; // empty -> sheet skipped
        }
        List<ColumnDef> cols = schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson);
        for (ColumnDef col : cols) {
            String name = col.getName();
            if (name == null || isExcluded(name)) {
                continue;
            }
            if (col.isFreezeColumn() || col.isFreezeTillData()) {
                result.alwaysRestore.add(name);
            } else if (col.isFreezeColumnIfFilled()) {
                result.restoreIfBaselineFilled.add(name);
            }
            // unFreezeColumnTillData / no flag -> editable, never restored
        }
        return result;
    }

    private boolean isExcluded(String name) {
        return ALWAYS_EXCLUDED.contains(name)
                || name.endsWith(ProcessingConstants.HELPER_COLUMN_SUFFIX)
                || (name.startsWith("#") && name.endsWith("#"));
    }

    /**
     * Whether the uploaded workbook still carries the hidden row-id column on any visible sheet. If it
     * does but the generationId is gone, the hidden metadata sheet was stripped (tampering) -> fail
     * closed. If neither is present, the file predates join mode (a genuine legacy/protected template).
     */
    private boolean hasRowIdColumn(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String name = sheet.getSheetName();
            if (name != null && name.startsWith("_h_") && name.endsWith("_h_")) {
                continue;
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                continue;
            }
            for (int c = 0; c < header.getLastCellNum(); c++) {
                Cell cell = header.getCell(c);
                if (cell != null && ProcessingConstants.ROW_ID_COLUMN_NAME.equals(ExcelUtil.getCellValueAsString(cell))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Maps an expanded multi-select child column (parent_MULTISELECT_n) back to its parent name. */
    private static String parentColumnOf(String col) {
        int idx = col.indexOf(MULTISELECT_MARKER);
        return idx > 0 ? col.substring(0, idx) : col;
    }

    private String readGenerationId(Workbook workbook) {
        Sheet meta = workbook.getSheet(GenerationConstants.META_SHEET_NAME);
        if (meta == null) {
            return null;
        }
        Row row = meta.getRow(0);
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(0);
        return cell == null ? null : ExcelUtil.getCellValueAsString(cell);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean equalsNullSafe(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Immutable columns split by how the baseline value is applied. */
    private static class ImmutableColumns {
        final Set<String> alwaysRestore = new HashSet<>();          // freezeColumn / freezeTillData
        final Set<String> restoreIfBaselineFilled = new HashSet<>(); // freezeColumnIfFilled

        boolean isEmpty() {
            return alwaysRestore.isEmpty() && restoreIfBaselineFilled.isEmpty();
        }
    }
}
