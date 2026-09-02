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
    private final CampaignService campaignService;

    private static final String MULTISELECT_MARKER = "_MULTISELECT_";

    /** Columns never restored from the baseline even if the schema marks them immutable. */
    private static final Set<String> ALWAYS_EXCLUDED = Set.of(
            ProcessingConstants.USER_USAGE_COLUMN_KEY,     // user active/inactive - intentionally editable
            ProcessingConstants.FACILITY_USAGE_COLUMN_KEY, // facility active/inactive - intentionally editable
            ProcessingConstants.ROW_ID_COLUMN_NAME,        // the join key itself
            ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY,  // computed VLOOKUP formula, not data
            ProcessingConstants.REGISTER_ID_COLUMN_KEY);   // computed formula, not data

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
                    ErrorConstants.IMMUTABLE_MISSING_GENERATION_ID_MESSAGE);
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
        } else if (clonedCampaignId != null && !clonedCampaignId.trim().isEmpty()
                && equalsNullSafe(baselineGen.getReferenceId(), clonedCampaignId.trim())) {
            log.info("Upload sheet is belong to the cloned campaign ");
        } else {
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
                        baselineGen.getFileStoreId(), resource, warningsOut, localizationMap);
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
     * <p>Hierarchy/boundary columns are handled specially: ANY level whose uploaded value differs from
     * the baseline is a permitted area change and is left exactly as uploaded - deepening, a same-level
     * move and clearing a level are all allowed. Levels the operator did NOT touch are still restored
     * from the baseline when the baseline had a value, so untouched cells keep their
     * freezeColumnIfFilled protection.
     *
     * @return the set of always-immutable column names that were reconstructed onto existing rows of
     *         this sheet (empty if nothing was reconstructed). freezeColumnIfFilled columns are excluded
     *         because they are restored only conditionally (per cell), so they must still be validated.
     */
    private Set<String> joinSheet(String sheetName, Map<String, Object> schemaMap,
                           Workbook uploadedWorkbook, Workbook baselineWorkbook,
                           String baselineFileStoreId, ProcessResource resource,
                           List<ValidationError> warningsOut, Map<String, String> localizationMap) {
        Sheet uploadedSheet = uploadedWorkbook.getSheet(sheetName);
        Sheet baselineSheet = baselineWorkbook.getSheet(sheetName);
        if (uploadedSheet == null || baselineSheet == null) {
            return Collections.emptySet(); // nothing to reconstruct for this sheet
        }

        ImmutableColumns immutable = deriveImmutableColumns(schemaMap);

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
        SheetJoin sj = new SheetJoin(uploadedSheet, uploadedColIndex, sheetName, warningsOut, localizationMap);

        // Area levels are editable only on the free-entry (user-typed) sheets - the User List and
        // Facilities List. Generation leaves exactly those UNPROTECTED in join mode and protects every
        // other join-mode sheet, so the BASELINE sheet's protection flag is the server's own record of
        // which is which. Read from the BASELINE (our generated file), never from the upload, so an
        // operator cannot unlock a protected sheet by unprotecting it in Excel.
        //
        // An unprotected sheet only MEANS "free-entry" when protection was actually applied somewhere in
        // this workbook. ExcelDataPopulator.applyProtection skips protection entirely when
        // egov.excel.sheet.password is blank, which would leave EVERY sheet unprotected - and then a
        // Boundary List would read as area-editable and its hierarchy would become writable. Requiring at
        // least one protected sheet makes the signal self-validating: no protection anywhere means the
        // flag carries no information, so fall back to the pre-relaxation behaviour and restore from
        // baseline. Fail closed, never open.
        boolean protectionIsMeaningful = anySheetProtected(baselineWorkbook);
        boolean areaEditableSheet = protectionIsMeaningful && !baselineSheet.getProtect();
        if (!protectionIsMeaningful) {
            log.warn("Baseline generation {} has no protected sheet at all (egov.excel.sheet.password unset?)"
                    + " - treating every sheet as area-immutable so boundary columns stay server-authoritative",
                    baselineFileStoreId);
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
            // change the boundary path (see below) - otherwise we'd clobber a legitimate area change.
            int poiRowIdx = rowIndexOf(upRow);
            Object baselineBoundaryCode = null;
            Object baselineRegisterId = null;
            boolean userChangedBoundaryPath = false;

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
                    // Dynamic boundary/hierarchy column.
                    if (areaEditableSheet) {
                        // Free-entry sheet (User/Facilities List): ANY level the operator changed is a
                        // permitted area change and is left exactly as uploaded - deepening, a same-level
                        // move and clearing a level are all allowed. writeBack is deliberately NOT called
                        // for a changed level, because its revert path fails open (no error, no warning)
                        // whenever poiRowIdx is -1. Untouched levels are still restored when the baseline
                        // had a value, so they keep their freezeColumnIfFilled protection.
                        if (!sameTrimmed(upRow.get(col), baseEntry.getValue())) {
                            userChangedBoundaryPath = true;
                        } else if (baseFilled) {
                            writeBack(sj, upRow, poiRowIdx, col, baseEntry.getValue(), true);
                        }
                    } else {
                        // Protected sheet (e.g. Boundary List, where a boundary cell IS the row's
                        // identity): unchanged legacy behaviour - a prefilled level stays locked and a
                        // change is rejected; only filling an EMPTY level is treated as a deepen.
                        if (baseFilled) {
                            writeBack(sj, upRow, poiRowIdx, col, baseEntry.getValue(), true);
                        } else if (trimToNull(ExcelUtil.getValueAsString(upRow.get(col))) != null) {
                            userChangedBoundaryPath = true;
                        }
                    }
                }
            }

            // Restore the derived boundary code / register id from the baseline ONLY when the boundary
            // path is unchanged. Then the baseline value is authoritative - this also corrects a code
            // that the sheet formula mis-evaluated from a tampered locked selection. If the user
            // legitimately changed the boundary path (deepened, moved sideways, or cleared a leaf),
            // keep the formula-computed code instead.
            if (!userChangedBoundaryPath) {
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

    /**
     * Whether ANY sheet in the generated baseline carries sheet protection. Used to decide whether an
     * unprotected sheet is a deliberate free-entry sheet or simply a workbook that was generated with no
     * protection at all (blank {@code egov.excel.sheet.password}), in which case the flag means nothing.
     */
    private static boolean anySheetProtected(Workbook baselineWorkbook) {
        for (int i = 0; i < baselineWorkbook.getNumberOfSheets(); i++) {
            Sheet sheet = baselineWorkbook.getSheetAt(i);
            if (sheet != null && sheet.getProtect()) {
                return true;
            }
        }
        return false;
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

        // Fail-closed (reject-on-change): a pre-filled server-managed DATA cell was changed. Reject the whole
        // upload instead of silently reverting it - even if the user shifted the cell, the join matches on the
        // row-id so a moved-but-changed value is still caught. Derived formula cells (boundary code / register
        // id) pass immutableData=false and are still restored silently. Multi-select child cells are excluded
        // from the reject (their per-child order can differ harmlessly) and are restored silently instead.
        // Toggle: egov.excel.immutable-reject-on-change.
        boolean multiSelectChild = col.contains(MULTISELECT_MARKER);
        if (immutableData && !multiSelectChild && config.isImmutableRejectOnChange()
                && poiRowIdx >= 0 && !sameTrimmed(oldVal, value)) {
            log.info("Immutable-join REJECTED upload: sheet '{}' row {} column '{}' changed from baseline",
                    sj.sheetName, poiRowIdx + 1, col);
            exceptionHandler.throwCustomException(ErrorConstants.IMMUTABLE_CELL_TAMPERED,
                    ErrorConstants.IMMUTABLE_CELL_TAMPERED_MESSAGE
                            .replace("{0}", sj.sheetName)
                            .replace("{1}", String.valueOf(poiRowIdx + 1))
                            .replace("{2}", col));
        }

        upRow.put(col, value);

        // Legacy behaviour (reject disabled, or a derived formula cell): if the user's uploaded value differed
        // from the authoritative baseline, this locked (server-managed) cell was just reverted -> surface a
        // NON-FAILING warning (status=valid) so the user knows their edit did not take. Editable columns are
        // never reconstructed, so they never reach here.
        if (sj.warnings != null && poiRowIdx >= 0 && !sameTrimmed(oldVal, value)) {
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

    /** Per-sheet context: where to graft baseline values back + where to collect revert warnings. */
    private static final class SheetJoin {
        final Sheet sheet;
        final Map<String, Integer> colIndex;
        final String sheetName;
        final List<ValidationError> warnings;
        final Map<String, String> localizationMap;

        SheetJoin(Sheet sheet, Map<String, Integer> colIndex, String sheetName,
                  List<ValidationError> warnings, Map<String, String> localizationMap) {
            this.sheet = sheet;
            this.colIndex = colIndex;
            this.sheetName = sheetName;
            this.warnings = warnings;
            this.localizationMap = localizationMap;
        }
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
