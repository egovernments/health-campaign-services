package org.egov.excelingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.util.EnumLocalizationUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.util.LocalizationUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ValidationError;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final CampaignService campaignService;

    private static final String MULTISELECT_MARKER = "_MULTISELECT_";
    // How many campaigns up a clone chain an uploaded file's template may come from
    private static final int MAX_CLONE_CHAIN = 10;

    /** Columns never restored from the baseline even if the schema marks them immutable. */
    private static final Set<String> ALWAYS_EXCLUDED = Set.of(
            ProcessingConstants.USER_USAGE_COLUMN_KEY,     // user active/inactive - intentionally editable
            ProcessingConstants.FACILITY_USAGE_COLUMN_KEY, // facility active/inactive - intentionally editable
            ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY,     // site include/exclude/remove - intentionally editable
            ProcessingConstants.ROW_ID_COLUMN_NAME,        // the join key itself
            ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY,  // computed VLOOKUP formula, not data
            ProcessingConstants.REGISTER_ID_COLUMN_KEY);   // computed formula, not data

    /**
     * The columns that tie a pre-filled row to its existing record, per sheet, keyed by the hidden id column that marks
     * the sheet. On such a sheet a pre-filled row the planner copied is taken as a new row, whether the whole row was
     * copied (its hidden row id too) or only its cells up to the last visible column (the hidden ids among them come along,
     * the row id after them does not): these columns are cleared on the copy, so it looks like a row the planner typed. Other sheets
     * (e.g. the attendance sheets, where UserName is the planner's input) keep failing on a copied row as before.
     */
    private static final Map<String, List<String>> RECORD_COLUMNS_BY_SHEET_ID_COLUMN = Map.of(
            ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY,   // Outside Household: the site
            List.of(ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY),
            ProcessingConstants.FACILITY_CODE_COLUMN_KEY,                          // Facilities List: the facility
            List.of(ProcessingConstants.FACILITY_CODE_COLUMN_KEY),
            ProcessingConstants.USER_SERVICE_UUIDS_COLUMN_KEY,                     // User List: the user and its login
            List.of(ProcessingConstants.USER_SERVICE_UUIDS_COLUMN_KEY, ProcessingConstants.USERNAME_COLUMN_KEY,
                    ProcessingConstants.USER_PASSWORD_COLUMN_KEY, ProcessingConstants.WORKER_ID_COLUMN_KEY));

    public ImmutableJoinService(GeneratedFileRepository generatedFileRepository,
                                FileStoreService fileStoreService,
                                ExcelUtil excelUtil,
                                SchemaColumnDefUtil schemaColumnDefUtil,
                                ObjectMapper objectMapper,
                                CustomExceptionHandler exceptionHandler,
                                ExcelIngestionConfig config,
                                CampaignService campaignService) {
        this.generatedFileRepository = generatedFileRepository;
        this.fileStoreService = fileStoreService;
        this.excelUtil = excelUtil;
        this.schemaColumnDefUtil = schemaColumnDefUtil;
        this.objectMapper = objectMapper;
        this.exceptionHandler = exceptionHandler;
        this.config = config;
        this.campaignService = campaignService;
    }

    /**
     * Reconstructs authoritative pre-filled values onto the uploaded workbook's in-memory data.
     * Mutates the shared cached row maps so all downstream validation/processing/persistence see
     * authoritative data. No-op for legacy/protected files (no embedded generationId).
     *
     * @param sheetNameToSchema visible sheet name -> its MDMS schema map (resolved by the caller)
     * @param requestInfo the caller's RequestInfo, used to authenticate the campaign search
     * @return per visible sheet, the set of "always-immutable" columns that were reconstructed from the
     *         baseline onto existing rows. Downstream validation uses this to skip re-validating cells it
     *         did not let the user change. Empty map when the feature is inactive / legacy / no-op.
     */
    public Map<String, Set<String>> applyImmutableBaseline(Workbook uploadedWorkbook, ProcessResource resource,
                                       Map<String, Map<String, Object>> sheetNameToSchema,
                                       RequestInfo requestInfo) {
        return applyImmutableBaseline(uploadedWorkbook, resource, sheetNameToSchema, requestInfo,
                new ArrayList<>(), null);
    }

    /**
     * As {@link #applyImmutableBaseline(Workbook, ProcessResource, Map, RequestInfo)} but also appends a
     * non-failing WARNING to {@code warningsOut} for every locked cell whose uploaded value differed from
     * the baseline (a user edit to a server-managed cell that was reverted), localized via
     * {@code localizationMap}.
     */
    public Map<String, Set<String>> applyImmutableBaseline(Workbook uploadedWorkbook, ProcessResource resource,
                                       Map<String, Map<String, Object>> sheetNameToSchema,
                                       RequestInfo requestInfo,
                                       List<ValidationError> warningsOut, Map<String, String> localizationMap) {
        return applyImmutableBaseline(uploadedWorkbook, resource, sheetNameToSchema, requestInfo,
                warningsOut, localizationMap, localizationMap);
    }

    public Map<String, Set<String>> applyImmutableBaseline(Workbook uploadedWorkbook, ProcessResource resource,
                                       Map<String, Map<String, Object>> sheetNameToSchema,
                                       RequestInfo requestInfo,
                                       List<ValidationError> warningsOut, Map<String, String> localizationMap,
                                       Map<String, String> errorLocalizationMap) {
        // Scope: only the join-mode template families (unified-console, attendanceRegister,
        // attendanceRegisterAttendee) use join-mode. Any other type is processed as before, with no
        // baseline reconstruction.
        if (!ProcessingConstants.isJoinModeType(resource.getType())) {
            return Collections.emptyMap();
        }

        // 1. Read the embedded generationId.
        String generationId = readGenerationId(uploadedWorkbook);
        if (generationId == null || generationId.trim().isEmpty()) {
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_MISSING_GENERATION_ID,
                    localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_MISSING_GENERATION_ID,
                            ErrorConstants.IMMUTABLE_MISSING_GENERATION_ID_MESSAGE));
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
                    localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_BASELINE_NOT_FOUND,
                            ErrorConstants.IMMUTABLE_BASELINE_NOT_FOUND_MESSAGE,
                            generationId), e);
            return Collections.emptyMap();
        }
        if (baselineGen == null || baselineGen.getFileStoreId() == null
                || baselineGen.getFileStoreId().isEmpty()) {
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_BASELINE_NOT_FOUND,
                    localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_BASELINE_NOT_FOUND,
                            ErrorConstants.IMMUTABLE_BASELINE_NOT_FOUND_MESSAGE,
                            generationId));
            return Collections.emptyMap();
        }

        // 3. Identity cross-check: the baseline must belong to the SAME reference (campaign) as this
        // upload. We deliberately do NOT compare type: the generation type (e.g. "unified-console") and
        // the processing type (e.g. "unified-console-validation"/"-parse") differ by design, so an
        // equality check would falsely reject every legitimate upload. The generationId (unguessable,
        // looked up by id + tenant) plus the referenceId match are the identity guarantee.

        // A register reference is not a campaign id, so clone resolution is skipped for it.
        String clonedCampaignId = null;
        if (ProcessingConstants.REFERENCE_TYPE_ATTENDANCE_REGISTER.equals(resource.getReferenceType())) {
            log.info("Immutable-baseline join for generationId {}: register reference {}, no clone resolution",
                    generationId, resource.getReferenceId());
        } else {
            CampaignSearchResponse.CampaignDetail campaign = campaignService.searchCampaignById(
                    resource.getReferenceId(), resource.getTenantId(), requestInfo);
            clonedCampaignId = campaign.getAdditionalDetails() == null ? null
                    : campaign.getAdditionalDetails().getClonedCampaignId();
            log.info("Immutable-baseline join for generationId {}: campaign {} clonedCampaignId {}",
                    generationId, resource.getReferenceId(), clonedCampaignId);
        }



        if (equalsNullSafe(baselineGen.getReferenceId(), resource.getReferenceId())) {
            log.info("Upload sheet is belong to the campaign itself");
        } else if (isCloneAncestor(baselineGen.getReferenceId(), clonedCampaignId, resource.getTenantId(), requestInfo)) {
            // A clone gets its source's file as its template, and a clone of a clone gets that same file, so the file can
            // be the template of the source's source (or earlier)
            log.info("Upload sheet is belong to campaign {}, an earlier campaign of this clone's chain", baselineGen.getReferenceId());
        } else {
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_IDENTITY_MISMATCH,
                    localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_IDENTITY_MISMATCH,
                            ErrorConstants.IMMUTABLE_IDENTITY_MISMATCH_MESSAGE));
            return Collections.emptyMap();
        }

        // 4. Download + parse the baseline, then join per sheet. Collect, per sheet, the always-immutable
        // columns we reconstructed onto existing rows so validation can skip re-checking those cells.
        Map<String, Set<String>> immutableColumnsBySheet = new HashMap<>();
        // Changed pre-filled cells from every sheet; the upload is rejected once, naming all of them, so the
        // user does not have to fix and re-upload one cell at a time.
        List<TamperedCell> tampered = new ArrayList<>();
        try (Workbook baselineWorkbook = fileStoreService.downloadExcelFromFileStore(
                baselineGen.getFileStoreId(), resource.getTenantId())) {

            for (Map.Entry<String, Map<String, Object>> entry : sheetNameToSchema.entrySet()) {
                Set<String> restored = joinSheet(entry.getKey(), entry.getValue(), uploadedWorkbook, baselineWorkbook,
                        baselineGen.getFileStoreId(), resource, warningsOut, localizationMap, errorLocalizationMap,
                        tampered);
                if (!restored.isEmpty()) {
                    immutableColumnsBySheet.put(entry.getKey(), restored);
                }
            }
            if (!tampered.isEmpty()) {
                rejectTampered(tampered, errorLocalizationMap);
            }
        } catch (org.egov.tracer.model.CustomException ce) {
            throw ce; // fail-closed business errors propagate as-is
        } catch (Exception e) {
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_BASELINE_READ_ERROR,
                    localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_BASELINE_READ_ERROR,
                            ErrorConstants.IMMUTABLE_BASELINE_READ_ERROR_MESSAGE), e);
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
                           String baselineFileStoreId, ProcessResource resource,
                           List<ValidationError> warningsOut, Map<String, String> localizationMap,
                           Map<String, String> errorLocalizationMap, List<TamperedCell> tampered) {
        Sheet uploadedSheet = uploadedWorkbook.getSheet(sheetName);
        Sheet baselineSheet = baselineWorkbook.getSheet(sheetName);
        if (uploadedSheet == null || baselineSheet == null) {
            return Collections.emptySet(); // nothing to reconstruct for this sheet
        }

        ImmutableColumns immutable = deriveImmutableColumns(schemaMap, localizationMap);

        String hierarchyPrefix = (resource.getHierarchyType() == null || resource.getHierarchyType().isEmpty())
                ? null : (resource.getHierarchyType().toUpperCase() + "_");
        if (immutable.isEmpty() && hierarchyPrefix == null) {
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

        // Header name -> physical column index, so reconstructed values are also written back onto the
        // workbook cells (not just the parsed row-map), keeping the processed output file consistent with
        // the server-authoritative data. Without this, a tampered locked cell stays visible and unflagged
        // in the processed file even though validation/persistence used the correct baseline value.
        Map<String, Integer> uploadedColIndex = headerIndex(uploadedSheet);
        SheetJoin sj = new SheetJoin(uploadedSheet, uploadedColIndex, sheetName, warningsOut, localizationMap,
                errorLocalizationMap, immutable.enumReverseMaps, immutable.multiSelectReverseMaps, tampered,
                uploadedWorkbook.getSheetIndex(uploadedSheet));

        // Only when a row id repeats, which was always rejected: the copies become new rows
        if (!immutable.recordIdColumns.isEmpty()) {
            takeCopiedRowsAsNewRows(sj, immutable, hierarchyPrefix, uploadedRows, baselineByRowId);
        }

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
                        localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_UNKNOWN_ROW_ID,
                            ErrorConstants.IMMUTABLE_UNKNOWN_ROW_ID_MESSAGE,
                                localizedName(errorLocalizationMap, localizationMap, sheetName)));
            }
            if (!seen.add(rid)) {
                // Two uploaded rows claim the same baseline identity (a duplicated pre-filled row).
                exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_DUPLICATE_ROW_ID,
                        localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_DUPLICATE_ROW_ID,
                            ErrorConstants.IMMUTABLE_DUPLICATE_ROW_ID_MESSAGE,
                                localizedName(errorLocalizationMap, localizationMap, sheetName)));
            }
            // Reconstruct every immutable column AND its expanded _MULTISELECT_* child columns from the
            // baseline, overwriting whatever the file contains. We iterate the BASELINE row's own keys so
            // multi-select child cells are covered too (the parser keeps the per-child _MULTISELECT_n keys
            // in addition to populating the collapsed parent value).
            // The boundary code / attendance register id are FORMULA cells derived from the boundary
            // selection columns, evaluated at parse time from the UPLOADED selections. We capture their
            // authoritative baseline values and restore them after the loop, but only if the user did not
            // deepen the boundary path (see below) - otherwise we'd clobber a legitimate deeper selection.
            int poiRowIdx = rowIndexOf(upRow);
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
                    writeBack(sj, upRow, poiRowIdx, col, baseEntry.getValue(), true);
                    reconstructedColumns.add(parent);
                } else if (immutable.restoreIfBaselineFilled.contains(parent) && baseFilled) {
                    // freezeColumnIfFilled: immutable only where the baseline actually had a value.
                    writeBack(sj, upRow, poiRowIdx, col, baseEntry.getValue(), true);
                } else if (hierarchyPrefix != null
                        && parent.toUpperCase().startsWith(hierarchyPrefix)
                        && !isExcluded(parent)) {
                    // Dynamic boundary/hierarchy column. Lock the prefilled level (freezeColumnIfFilled);
                    // an empty baseline level the user filled means the user picked a deeper boundary.
                    if (baseFilled) {
                        writeBack(sj, upRow, poiRowIdx, col, baseEntry.getValue(), true);
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
                    writeBack(sj, upRow, poiRowIdx,
                            ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, baselineBoundaryCode, false);
                }
                if (trimToNull(ExcelUtil.getValueAsString(baselineRegisterId)) != null) {
                    writeBack(sj, upRow, poiRowIdx,
                            ProcessingConstants.REGISTER_ID_COLUMN_KEY, baselineRegisterId, false);
                }
            }
        }

        // Orphan detection: a baseline row-id missing from the upload means a pre-filled row was
        // deleted (or its id wiped to disguise an edit as a new row).
        if (seen.size() < baselineByRowId.size()) {
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_ORPHAN_ROWS,
                    localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_ORPHAN_ROWS,
                            ErrorConstants.IMMUTABLE_ORPHAN_ROWS_MESSAGE,
                            localizedName(errorLocalizationMap, localizationMap, sheetName)));
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
     * A pre-filled row that appears more than once was copied by the planner: Excel copies its hidden row id and record id
     * too. One of the rows stays the existing row - the first whose locked cells match the baseline, else the first - and
     * the others get their row id, record columns and boundary code cleared (row map and workbook cell), so they are validated
     * and created like rows the planner typed: the boundary code is resolved again from the copy's own boundary selection
     * (the planner may have changed it), and an unchanged copy is caught by the sheet's duplicate checks. A row id unknown
     * to the baseline is left alone, so it still fails closed. A row without a row id that carries an id of a pre-filled
     * row (its site id, facility code, user id, username or worker id - not the password, which users share) is that row
     * copied without its row id (only its cells up to the last visible column, where the row id is not): it is taken as a
     * new row the same way, so it never keeps the existing record's ids.
     */
    private void takeCopiedRowsAsNewRows(SheetJoin sj, ImmutableColumns immutable, String hierarchyPrefix,
                                         List<Map<String, Object>> uploadedRows,
                                         Map<String, Map<String, Object>> baselineByRowId) {
        Map<String, List<Map<String, Object>>> rowsById = new LinkedHashMap<>();
        for (Map<String, Object> upRow : uploadedRows) {
            String rid = trimToNull(ExcelUtil.getValueAsString(upRow.get(ProcessingConstants.ROW_ID_COLUMN_NAME)));
            if (rid != null && baselineByRowId.containsKey(rid)) {
                rowsById.computeIfAbsent(rid, k -> new ArrayList<>()).add(upRow);
            }
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : rowsById.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            if (rows.size() < 2) {
                continue;
            }
            Map<String, Object> baseRow = baselineByRowId.get(entry.getKey());
            Map<String, Object> existing = rows.stream()
                    .filter(row -> matchesBaseline(sj, immutable, hierarchyPrefix, row, baseRow))
                    .findFirst().orElse(rows.get(0));
            for (Map<String, Object> row : rows) {
                if (row == existing) {
                    continue;
                }
                clearCell(sj, row, ProcessingConstants.ROW_ID_COLUMN_NAME);
                for (String col : immutable.recordIdColumns) {
                    clearCell(sj, row, col);
                }
                clearCell(sj, row, ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY);
                log.info("Sheet '{}' row {} is a copy of pre-filled row {}; taken as a new row",
                        sj.sheetName, rowIndexOf(row) + 1, rowIndexOf(existing) + 1);
            }
        }
        Map<String, Set<String>> preFilledIds = new HashMap<>();
        for (String col : immutable.recordIdColumns) {
            if (ProcessingConstants.USER_PASSWORD_COLUMN_KEY.equals(col)) {
                continue;
            }
            Set<String> values = new HashSet<>();
            for (Map<String, Object> baseRow : baselineByRowId.values()) {
                String value = trimToNull(ExcelUtil.getValueAsString(baseRow.get(col)));
                if (value != null) {
                    values.add(value);
                }
            }
            preFilledIds.put(col, values);
        }
        for (Map<String, Object> row : uploadedRows) {
            if (trimToNull(ExcelUtil.getValueAsString(row.get(ProcessingConstants.ROW_ID_COLUMN_NAME))) != null
                    || preFilledIds.entrySet().stream().noneMatch(ids ->
                            ids.getValue().contains(trimToNull(ExcelUtil.getValueAsString(row.get(ids.getKey())))))) {
                continue;
            }
            for (String col : immutable.recordIdColumns) {
                clearCell(sj, row, col);
            }
            clearCell(sj, row, ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY);
            log.info("Sheet '{}' row {} carries a pre-filled row's id without its row id (a copy of its visible cells); taken as a new row",
                    sj.sheetName, rowIndexOf(row) + 1);
        }
    }

    /** True when every cell the join locks on this row (the cells a change of which is rejected) equals the baseline. */
    private boolean matchesBaseline(SheetJoin sj, ImmutableColumns immutable, String hierarchyPrefix,
                                    Map<String, Object> upRow, Map<String, Object> baseRow) {
        for (Map.Entry<String, Object> baseEntry : baseRow.entrySet()) {
            String col = baseEntry.getKey();
            if (ProcessingConstants.ACTUAL_ROW_NUMBER_KEY.equals(col) || col.contains(MULTISELECT_MARKER)) {
                continue;
            }
            String parent = parentColumnOf(col);
            if (ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY.equals(parent) || ProcessingConstants.REGISTER_ID_COLUMN_KEY.equals(parent)) {
                continue;
            }
            boolean baseFilled = trimToNull(ExcelUtil.getValueAsString(baseEntry.getValue())) != null;
            boolean locked = immutable.alwaysRestore.contains(parent)
                    || (immutable.restoreIfBaselineFilled.contains(parent) && baseFilled)
                    || (hierarchyPrefix != null && parent.toUpperCase().startsWith(hierarchyPrefix) && !isExcluded(parent) && baseFilled);
            if (locked && !sameValue(sj, col, upRow.get(col), baseEntry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** Blanks a column of an uploaded row in the row map and in its workbook cell; a column the row lacks is left out. */
    private static void clearCell(SheetJoin sj, Map<String, Object> upRow, String col) {
        if (!upRow.containsKey(col)) {
            return;
        }
        upRow.put(col, null);
        Integer ci = sj.colIndex.get(col);
        int poiRowIdx = rowIndexOf(upRow);
        Row row = ci == null || poiRowIdx < 0 ? null : sj.sheet.getRow(poiRowIdx);
        Cell cell = row == null ? null : row.getCell(ci);
        if (cell != null) {
            cell.setBlank();
        }
    }

    /**
     * Derives the immutable column set from the schema using the SAME source as generation
     * ({@link SchemaColumnDefUtil}), so the two sides always agree on which columns are immutable.
     */
    private ImmutableColumns deriveImmutableColumns(Map<String, Object> schemaMap,
                                                    Map<String, String> localizationMap) {
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
            if (RECORD_COLUMNS_BY_SHEET_ID_COLUMN.containsKey(name)) {
                result.recordIdColumns.addAll(RECORD_COLUMNS_BY_SHEET_ID_COLUMN.get(name));
            }
            if (EnumLocalizationUtil.isEnumColumn(col)) {
                Map<String, String> reverseMap = EnumLocalizationUtil.buildReverseMap(
                        name, col.getEnumValues(), localizationMap, col.getPrefix());
                if (!reverseMap.isEmpty()) {
                    result.enumReverseMaps.put(name, reverseMap);
                }
            } else if (col.getMultiSelectDetails() != null && col.getMultiSelectDetails().getEnumValues() != null) {
                // The multi-select parent holds the comma-joined selections (e.g. the user roles); an earlier
                // request on the same file may already have rebuilt it from codes, while the baseline has labels.
                Map<String, String> reverseMap = EnumLocalizationUtil.buildReverseMap(
                        name, col.getMultiSelectDetails().getEnumValues(), localizationMap, col.getPrefix());
                if (!reverseMap.isEmpty()) {
                    result.multiSelectReverseMaps.put(name, reverseMap);
                }
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

    /** Maps an expanded multi-select child column (parent_MULTISELECT_n) back to its parent name. */
    private static String parentColumnOf(String col) {
        int idx = col.indexOf(MULTISELECT_MARKER);
        return idx > 0 ? col.substring(0, idx) : col;
    }

    /** Header (row 0) name -> physical column index, for writing reconstructed values back to cells. */
    private static Map<String, Integer> headerIndex(Sheet sheet) {
        Map<String, Integer> idx = new HashMap<>();
        Row header = sheet.getRow(0);
        if (header == null) {
            return idx;
        }
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            String name = cell == null ? null : ExcelUtil.getCellValueAsString(cell);
            if (name != null && !name.isEmpty()) {
                idx.putIfAbsent(name, c);
            }
        }
        return idx;
    }

    /** The uploaded row's own POI row index, recovered from the parser-stamped 1-based row number. */
    private static int rowIndexOf(Map<String, Object> row) {
        Object n = row.get(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY);
        return (n instanceof Number) ? ((Number) n).intValue() - 1 : -1;
    }

    /**
     * Grafts an authoritative baseline value onto BOTH the parsed row-map (used by validation/persistence)
     * AND the underlying workbook cell (so the processed output file shows the server value, not the user's
     * edited one). Writing only the map would leave a tampered locked value visible and unflagged in the
     * processed file. Map-only fallback when the column has no physical cell (synthetic/multi-select key)
     * or the row index is unknown.
     */
    private void writeBack(SheetJoin sj, Map<String, Object> upRow, int poiRowIdx, String col, Object value,
                           boolean immutableData) {
        Object oldVal = upRow.get(col);

        // Fail-closed (reject-on-change): a pre-filled server-managed DATA cell was changed. The whole upload
        // is rejected instead of silently reverting it - even if the user shifted the cell, the join matches on
        // the row-id so a moved-but-changed value is still caught. The cell is only noted here, and left as the
        // user wrote it; applyImmutableBaseline rejects once after every sheet is joined, naming every changed
        // cell. Derived formula cells (boundary code / register id) pass immutableData=false and are still
        // restored silently. Multi-select child cells are excluded from the reject (their per-child order can
        // differ harmlessly) and are restored silently instead. Toggle: egov.excel.immutable-reject-on-change.
        boolean multiSelectChild = col.contains(MULTISELECT_MARKER);
        if (immutableData && !multiSelectChild && config.isImmutableRejectOnChange()
                && poiRowIdx >= 0 && !sameValue(sj, col, oldVal, value)) {
            log.info("Immutable-join REJECTED upload: sheet '{}' row {} column '{}' changed from baseline",
                    sj.sheetName, poiRowIdx + 1, col);
            Integer ci = sj.colIndex.get(col);
            sj.tampered.add(new TamperedCell(sj.sheetIndex, poiRowIdx + 1, ci == null ? Integer.MAX_VALUE : ci,
                    localizedName(sj.errorLocalizationMap, sj.localizationMap, sj.sheetName),
                    localizedName(sj.errorLocalizationMap, sj.localizationMap, col)));
            return;
        }

        upRow.put(col, value);

        // Legacy behaviour (reject disabled, or a derived formula cell): if the user's uploaded value differed
        // from the authoritative baseline, this locked (server-managed) cell was just reverted -> surface a
        // NON-FAILING warning (status=valid) so the user knows their edit did not take. Editable columns are
        // never reconstructed, so they never reach here.
        if (sj.warnings != null && poiRowIdx >= 0 && !sameValue(sj, col, oldVal, value)) {
            String msg = ValidationConstants.DEFAULT_IMMUTABLE_CELL_REVERTED;
            if (sj.localizationMap != null) {
                msg = LocalizationUtil.getLocalizedMessage(sj.localizationMap,
                        ValidationConstants.HCM_IMMUTABLE_CELL_REVERTED, msg);
            }
            sj.warnings.add(ValidationError.builder()
                    .rowNumber(poiRowIdx + 1)
                    .sheetName(sj.sheetName)
                    .status(ValidationConstants.STATUS_VALID)
                    .errorDetails(msg)
                    .columnName(col)
                    .build());
            log.info("Immutable-join reverted a user edit on sheet '{}' row {} column '{}' (server-managed)",
                    sj.sheetName, poiRowIdx + 1, col);
        }

        Integer ci = sj.colIndex.get(col);
        if (ci == null || poiRowIdx < 0) {
            return;
        }
        Row row = sj.sheet.getRow(poiRowIdx);
        if (row == null) {
            return;
        }
        Cell cell = row.getCell(ci, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        if (cell.getCellType() == CellType.FORMULA) {
            cell.setBlank(); // drop the now-stale VLOOKUP formula; the authoritative literal replaces it
        }
        if (value == null || value.toString().isEmpty()) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Equal as trimmed strings, or - for a localized enum column - the same canonical value. The uploaded
     * row map is shared across requests on the same file, so an earlier request may already have
     * normalized it to codes (EnumValueNormalizer) while the baseline still holds the localized label.
     */
    private static boolean sameValue(SheetJoin sj, String col, Object uploaded, Object baseline) {
        if (sameTrimmed(uploaded, baseline)) {
            return true;
        }
        Map<String, String> multiSelectMap = sj.multiSelectReverseMaps.get(col);
        if (multiSelectMap != null) {
            List<String> canonical = canonicalSelections(uploaded, multiSelectMap);
            return canonical != null && canonical.equals(canonicalSelections(baseline, multiSelectMap));
        }
        Map<String, String> reverseMap = sj.enumReverseMaps.get(col);
        String canonical = EnumLocalizationUtil.toCanonical(norm(uploaded), reverseMap);
        return canonical != null && canonical.equals(EnumLocalizationUtil.toCanonical(norm(baseline), reverseMap));
    }

    /** The comma-joined selections as sorted codes, or null when one of them is not a known value. */
    private static List<String> canonicalSelections(Object value, Map<String, String> reverseMap) {
        List<String> out = new ArrayList<>();
        for (String part : norm(value).split(",")) {
            if (part.trim().isEmpty()) {
                continue;
            }
            String canonical = EnumLocalizationUtil.toCanonical(part, reverseMap);
            if (canonical == null) {
                return null;
            }
            out.add(canonical);
        }
        Collections.sort(out);
        return out;
    }

    /** True when two cell values are equal as trimmed strings (null/empty treated as equal). */
    private static boolean sameTrimmed(Object a, Object b) {
        return norm(a).equals(norm(b));
    }

    private static String norm(Object o) {
        if (o == null) {
            return "";
        }
        String s = ExcelUtil.getValueAsString(o);
        return s == null ? "" : s.trim();
    }

    /**
     * Whether referenceId is the campaign this one was cloned from, or one that campaign was cloned from, and so on (at
     * most MAX_CLONE_CHAIN campaigns up, stopping at a loop or a campaign that cannot be read).
     */
    private boolean isCloneAncestor(String referenceId, String clonedCampaignId, String tenantId, RequestInfo requestInfo) {
        String ancestor = trimToNull(clonedCampaignId);
        Set<String> seen = new HashSet<>();
        for (int depth = 0; ancestor != null && depth < MAX_CLONE_CHAIN && seen.add(ancestor); depth++) {
            if (equalsNullSafe(referenceId, ancestor)) {
                return true;
            }
            try {
                CampaignSearchResponse.CampaignDetail campaign = campaignService.searchCampaignById(ancestor, tenantId, requestInfo);
                ancestor = campaign == null || campaign.getAdditionalDetails() == null ? null
                        : trimToNull(campaign.getAdditionalDetails().getClonedCampaignId());
            } catch (Exception e) {
                log.warn("Could not read campaign {} of the clone chain: {}", ancestor, e.getMessage());
                return false;
            }
        }
        return false;
    }

    /** Per-sheet context: where to graft baseline values back + where to collect revert warnings. */
    private static final class SheetJoin {
        final Sheet sheet;
        final Map<String, Integer> colIndex;
        final String sheetName;
        final List<ValidationError> warnings;
        final Map<String, String> localizationMap;
        final Map<String, String> errorLocalizationMap;
        final Map<String, Map<String, String>> enumReverseMaps;
        final Map<String, Map<String, String>> multiSelectReverseMaps;
        final List<TamperedCell> tampered;
        final int sheetIndex;

        SheetJoin(Sheet sheet, Map<String, Integer> colIndex, String sheetName,
                  List<ValidationError> warnings, Map<String, String> localizationMap,
                  Map<String, String> errorLocalizationMap,
                  Map<String, Map<String, String>> enumReverseMaps,
                  Map<String, Map<String, String>> multiSelectReverseMaps,
                  List<TamperedCell> tampered, int sheetIndex) {
            this.sheet = sheet;
            this.colIndex = colIndex;
            this.sheetName = sheetName;
            this.warnings = warnings;
            this.localizationMap = localizationMap;
            this.errorLocalizationMap = errorLocalizationMap;
            this.enumReverseMaps = enumReverseMaps;
            this.multiSelectReverseMaps = multiSelectReverseMaps;
            this.tampered = tampered;
            this.sheetIndex = sheetIndex;
        }
    }

    /** A changed pre-filled cell: workbook position (for ordering) and the display names used in the error. */
    record TamperedCell(int sheetIndex, int row, int column, String sheetName, String columnName) {
    }

    /**
     * Rejects the upload naming every changed pre-filled cell, in workbook order (sheet, row, column). One cell
     * keeps the single-cell message; more list up to egov.excel.immutable-reject-max-listed cells, with the total.
     */
    private void rejectTampered(List<TamperedCell> tampered, Map<String, String> errorLocalizationMap) {
        tampered.sort(Comparator.comparingInt(TamperedCell::sheetIndex).thenComparingInt(TamperedCell::row)
                .thenComparingInt(TamperedCell::column));
        if (tampered.size() == 1) {
            TamperedCell t = tampered.get(0);
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_CELL_TAMPERED,
                    localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_CELL_TAMPERED,
                            ErrorConstants.IMMUTABLE_CELL_TAMPERED_MESSAGE,
                            t.sheetName(), String.valueOf(t.row()), t.columnName()));
            return;
        }
        int maxListed = Math.max(1, config.getImmutableRejectMaxListed());
        StringBuilder cells = new StringBuilder();
        for (TamperedCell t : tampered.subList(0, Math.min(maxListed, tampered.size()))) {
            if (cells.length() > 0) {
                cells.append("; ");
            }
            cells.append(localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_CELLS_TAMPERED_ITEM,
                    ErrorConstants.IMMUTABLE_CELLS_TAMPERED_ITEM_MESSAGE,
                    t.sheetName(), String.valueOf(t.row()), t.columnName()));
        }
        if (tampered.size() > maxListed) {
            cells.append("; ...");
        }
        exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_CELL_TAMPERED,
                localizedError(errorLocalizationMap, ErrorConstants.IMMUTABLE_CELLS_TAMPERED,
                        ErrorConstants.IMMUTABLE_CELLS_TAMPERED_MESSAGE,
                        String.valueOf(tampered.size()), cells.toString()));
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

    /**
     * Resolves an upload-error message from the request-locale localization map, falling back to the
     * English text in {@link ErrorConstants} when the localization service has no entry for the code.
     *
     * <p>Placeholders are substituted on BOTH branches on purpose:
     * {@code LocalizationUtil.getLocalizedMessage} only substitutes when the key was found, which
     * would leave raw {@code {0}} markers in the fallback text.
     */
    /**
     * Display name for a column header inside a user-facing message.
     *
     * <p>Sheet headers are stored as technical keys (row 0), so inserting one raw into an error leaves
     * the user reading {@code HCM_ADMIN_CONSOLE_USER_ROLE} instead of "User Role". This resolves any
     * column generically - there is no per-column special-casing - using the same
     * lookup-then-fall-back-to-the-key convention the generator uses when it writes the localized
     * header row.
     *
     * <p>Deliberately uses the WORKBOOK's localization map, not the request-locale one: the name has to
     * match the header the user is actually looking at in the file they uploaded, which was written in
     * the generation locale. The surrounding sentence stays in the request locale.
     *
     * <p>Expanded multi-select children ({@code PARENT_MULTISELECT_2}) have no key of their own, so the
     * parent is localized and the child suffix preserved.
     *
     * <p>NOTE: this is for DISPLAY text only. {@code ValidationError.columnName} must keep the technical
     * key - it is looked up against a row-0-derived index to highlight the offending cell
     * (UserValidationProcessor), and a localized value would never match.
     */
    private static String localizedName(Map<String, String> requestLocale,
                                        Map<String, String> workbookLocale, String col) {
        if (col == null || col.isEmpty()) {
            return col;
        }
        // Prefer the READER's locale so the whole sentence is in one language; fall back to the
        // workbook's locale (which at least matches the header text in the file they uploaded).
        String resolved = lookupName(requestLocale, col);
        return resolved != null ? resolved : (lookupName(workbookLocale, col) != null
                ? lookupName(workbookLocale, col) : col);
    }

    /** Single-map resolution: exact key, multi-select parent, then Excel-truncated sheet name. */
    private static String lookupName(Map<String, String> messages, String col) {
        String direct = LocalizationUtil.getLocalizedMessage(messages, col, null);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        String parent = parentColumnOf(col);
        if (!parent.equals(col)) {
            String localizedParent = LocalizationUtil.getLocalizedMessage(messages, parent, null);
            if (localizedParent != null && !localizedParent.isEmpty()) {
                // Match the convention the catalogue itself uses for these
                // ("..._USER_ROLE_MULTISELECT_2" -> "User Role 2") so ANY multi-select column reads
                // correctly without needing a per-child entry seeded for it.
                String index = col.substring(parent.length() + MULTISELECT_MARKER.length());
                return index.isEmpty() ? localizedParent : localizedParent + " " + index;
            }
        }
        return resolveTruncatedSheetName(messages, col);
    }

    /** Excel's hard cap on a sheet name; longer names are silently truncated when the file is written. */
    private static final int EXCEL_SHEET_NAME_LIMIT = 31;

    /**
     * Recovers the display name for a sheet whose key was truncated when the workbook was written.
     *
     * <p>Excel caps sheet names at {@value #EXCEL_SHEET_NAME_LIMIT} characters, so a configured sheet
     * like {@code HCM_ADMIN_CONSOLE_FACILITIES_LIST} (33) reaches us as
     * {@code HCM_ADMIN_CONSOLE_FACILITIES_LI} (31). A catalogue entry authored against the full name
     * can then never match an exact lookup - the name we hold is not the name anyone would seed.
     *
     * <p>Only engages for names sitting exactly on the limit, and only when exactly one key extends it,
     * so an ambiguous prefix falls through to the raw key rather than guessing wrong.
     */
    private static String resolveTruncatedSheetName(Map<String, String> messages, String name) {
        if (messages == null || name.length() != EXCEL_SHEET_NAME_LIMIT) {
            return null;
        }
        String match = null;
        for (Map.Entry<String, String> e : messages.entrySet()) {
            String key = e.getKey();
            if (key != null && key.length() > EXCEL_SHEET_NAME_LIMIT && key.startsWith(name)) {
                if (match != null) {
                    return null; // ambiguous - two keys share this truncation, do not guess
                }
                match = e.getValue();
            }
        }
        return (match == null || match.isEmpty()) ? null : match;
    }

    private static String localizedError(Map<String, String> messages, String code, String defaultMessage,
                                         String... params) {
        String message = LocalizationUtil.getLocalizedMessage(messages, code, defaultMessage);
        for (int i = 0; i < params.length; i++) {
            if (params[i] != null) {
                message = message.replace("{" + i + "}", params[i]);
            }
        }
        return message;
    }

    private static boolean equalsNullSafe(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Immutable columns split by how the baseline value is applied. */
    private static class ImmutableColumns {
        final Set<String> alwaysRestore = new HashSet<>();          // freezeColumn / freezeTillData
        final Set<String> restoreIfBaselineFilled = new HashSet<>(); // freezeColumnIfFilled
        final Map<String, Map<String, String>> enumReverseMaps = new HashMap<>(); // localized label -> code
        final Map<String, Map<String, String>> multiSelectReverseMaps = new HashMap<>(); // multi-select parent: label -> code
        final List<String> recordIdColumns = new ArrayList<>();    // columns tying a row to its record (RECORD_COLUMNS_BY_SHEET_ID_COLUMN)

        boolean isEmpty() {
            return alwaysRestore.isEmpty() && restoreIfBaselineFilled.isEmpty();
        }
    }
}
