package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generator for attendance register sheet - uses ISheetGenerator (direct workbook) approach
 * to support cascading boundary dropdowns via HierarchicalBoundaryUtil.
 */
@Component
@Slf4j
public class AttendanceRegisterSheetGenerator implements ISheetGenerator {

    /** Positional format specifier standing in for the Excel row number in a formula template. */
    private static final String ROW_PLACEHOLDER = "%1$d";

    private final BoundaryUtil boundaryUtil;
    private final MDMSService mdmsService;
    private final CampaignService campaignService;
    private final CustomExceptionHandler exceptionHandler;
    private final SchemaColumnDefUtil schemaColumnDefUtil;
    private final ExcelDataPopulator excelDataPopulator;
    private final HierarchicalBoundaryUtil hierarchicalBoundaryUtil;
    private final ExcelIngestionConfig config;

    public AttendanceRegisterSheetGenerator(BoundaryUtil boundaryUtil,
                                           MDMSService mdmsService, CampaignService campaignService,
                                           CustomExceptionHandler exceptionHandler,
                                           SchemaColumnDefUtil schemaColumnDefUtil,
                                           ExcelDataPopulator excelDataPopulator,
                                           HierarchicalBoundaryUtil hierarchicalBoundaryUtil,
                                           ExcelIngestionConfig config) {
        this.boundaryUtil = boundaryUtil;
        this.mdmsService = mdmsService;
        this.campaignService = campaignService;
        this.exceptionHandler = exceptionHandler;
        this.schemaColumnDefUtil = schemaColumnDefUtil;
        this.excelDataPopulator = excelDataPopulator;
        this.hierarchicalBoundaryUtil = hierarchicalBoundaryUtil;
        this.config = config;
    }

    @Override
    public XSSFWorkbook generateSheet(XSSFWorkbook workbook,
                                      String sheetName,
                                      SheetGenerationConfig sheetConfig,
                                      GenerateResource generateResource,
                                      RequestInfo requestInfo,
                                      Map<String, String> localizationMap) {

        log.info("Generating attendance register sheet: {} for schema: {}", sheetName, sheetConfig.getSchemaName());

        try {
            // Fetch schema from MDMS
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", sheetConfig.getSchemaName());

            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, generateResource.getTenantId(), ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);

            String schemaJson = extractSchemaFromMDMSResponse(mdmsList, sheetConfig.getSchemaName());

            if (schemaJson != null && !schemaJson.isEmpty()) {
                List<ColumnDef> columns = schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson);

                // Create or recreate the sheet
                if (workbook.getSheetIndex(sheetName) >= 0) {
                    workbook.removeSheetAt(workbook.getSheetIndex(sheetName));
                }
                workbook.createSheet(sheetName);

                // Add boundary dropdowns using HierarchicalBoundaryUtil (same pattern as UserSheetGenerator)
                HierarchicalBoundaryUtil.BoundaryColumnLayout boundaryLayout = null;
                if (shouldAddBoundaryDropdowns(generateResource)) {
                    List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries =
                            campaignService.getBoundariesFromCampaign(generateResource.getReferenceId(),
                                    generateResource.getTenantId(), requestInfo);

                    if (campaignBoundaries != null && !campaignBoundaries.isEmpty()) {
                        List<Boundary> enrichedBoundaries = boundaryUtil.getEnrichedBoundariesFromCampaign(
                                generateResource.getId(), generateResource.getReferenceId(),
                                generateResource.getTenantId(), generateResource.getHierarchyType(), requestInfo);

                        boundaryLayout = hierarchicalBoundaryUtil.addHierarchicalBoundaryColumnWithData(
                                workbook, sheetName, localizationMap, enrichedBoundaries,
                                generateResource.getHierarchyType(), generateResource.getTenantId(),
                                requestInfo, null);
                    }
                }

                // Add schema columns (Register ID, etc.) using ExcelDataPopulator
                workbook = (XSSFWorkbook) excelDataPopulator.populateSheetWithData(
                        workbook, sheetName, columns, null, localizationMap);

                // Must run AFTER populateSheetWithData: the Register ID column only exists by then.
                addBoundaryCodeAndRegisterIdFormulas(workbook, sheetName, boundaryLayout);
            }

        } catch (Exception e) {
            log.error("Error generating attendance register sheet {}: {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate attendance register sheet: " + sheetName, e);
        }

        return workbook;
    }

    private boolean shouldAddBoundaryDropdowns(GenerateResource generateResource) {
        return generateResource.getReferenceId() != null && !generateResource.getReferenceId().isEmpty()
                && generateResource.getHierarchyType() != null && !generateResource.getHierarchyType().isEmpty();
    }

    private String extractSchemaFromMDMSResponse(List<Map<String, Object>> mdmsList, String title) {
        try {
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                if (properties != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    log.info("Successfully extracted MDMS schema for: {}", title);
                    return mapper.writeValueAsString(properties);
                }
            }
            log.warn("No MDMS data found for schema: {}", title);
        } catch (Exception e) {
            log.error("Error extracting MDMS schema {}: {}", title, e.getMessage(), e);
        }
        return null;
    }

    /**
     * Attendance-register only: fills the hidden boundary-code column with a per-row lookup so the
     * Register ID (a plain reference to it) auto-populates in Excel the moment a boundary is picked.
     *
     * The shared boundary util deliberately writes no per-row formulas — templates that do not need
     * this live auto-fill (facility / user / unified-console) stay scaffold-less. The helper columns
     * the pre-scaffold-removal design used are not recreated either: the cascade now resolves inside
     * each validation formula, so only this one lookup column is needed.
     *
     * Blank until a boundary is selected, and left unlocked so a user can type their own id over it.
     *
     * The fill must span the same rows as the dropdown validations (index 2..excelRowLimit + 1) — a
     * dropdown row without the formula yields a blank code, and project-factory rejects that row with
     * "Boundary code is missing". There is no server-side recovery for this template: attendance
     * uploads are parsed by project-factory itself, so BoundaryCodeResolver never runs on them.
     */
    private void addBoundaryCodeAndRegisterIdFormulas(XSSFWorkbook workbook, String sheetName,
                                                      HierarchicalBoundaryUtil.BoundaryColumnLayout layout) {
        if (layout == null || layout.getCodeMappingStartRow() < 1) {
            log.info("No boundary code mapping available, skipping attendance register formulas");
            return;
        }

        Sheet sheet = workbook.getSheet(sheetName);
        Row hiddenRow = sheet != null ? sheet.getRow(0) : null;
        if (hiddenRow == null) {
            log.warn("Hidden header row missing on sheet {}, skipping attendance register formulas", sheetName);
            return;
        }

        int registerIdColIndex = -1;
        // getLastCellNum() is already lastIndex + 1; a non-string header would throw on getStringCellValue.
        for (int colIdx = 0; colIdx < hiddenRow.getLastCellNum(); colIdx++) {
            Cell cell = hiddenRow.getCell(colIdx);
            if (cell != null && cell.getCellType() == CellType.STRING
                    && ProcessingConstants.REGISTER_ID_COLUMN_KEY.equals(cell.getStringCellValue())) {
                registerIdColIndex = colIdx;
                break;
            }
        }
        if (registerIdColIndex == -1) {
            log.info("Register ID column not found, skipping attendance register formulas");
            return;
        }

        List<Integer> levelCols = layout.getVisibleBoundaryColIndices();
        if (levelCols.isEmpty()) {
            log.info("No visible boundary columns found, skipping attendance register formulas");
            return;
        }

        int codeColIndex = layout.getBoundaryCodeColIndex();
        String codeColLetter = CellReference.convertNumToColString(codeColIndex);
        String mappingRange = String.format("%s!$%s$%d:$%s$%d",
                HierarchicalBoundaryUtil.LOOKUP_SHEET_NAME,
                CellReference.convertNumToColString(HierarchicalBoundaryUtil.CODE_MAPPING_KEY_COLUMN),
                layout.getCodeMappingStartRow(),
                CellReference.convertNumToColString(HierarchicalBoundaryUtil.CODE_MAPPING_CODE_COLUMN),
                layout.getCodeMappingEndRow());

        // Matches the cascade validation range (HierarchicalBoundaryUtil applies dropdowns to
        // 2..excelRowLimit + 1), so every row that offers a dropdown can resolve its code.
        int lastRow = config.getExcelRowLimit() + 1;

        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);

        // Only the row number varies per row, so build the nested formula once and stamp the row in.
        String codeFormulaTemplate = buildBoundaryCodeFormulaTemplate(levelCols, mappingRange);

        for (int r = 2; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) row = sheet.createRow(r);

            // Only ever fill blanks: a prefilled row's code/id is server-authoritative.
            Cell codeCell = row.getCell(codeColIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            if (codeCell.getCellType() == CellType.BLANK) {
                codeCell.setCellFormula(String.format(codeFormulaTemplate, r + 1));
            }

            Cell registerIdCell = row.getCell(registerIdColIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            if (registerIdCell.getCellType() == CellType.BLANK) {
                // Guarded rather than a bare reference: =<code><row> on a blank code cell renders 0,
                // which was the reported symptom.
                String codeRef = codeColLetter + (r + 1);
                registerIdCell.setCellFormula("IF(" + codeRef + "=\"\",\"\"," + codeRef + ")");
                registerIdCell.setCellStyle(unlocked);
            }
        }

        log.info("Added attendance register boundary-code + Register ID formulas for rows 3-{} (mapping {})",
                lastRow + 1, mappingRange);
    }

    /**
     * Resolves the deepest selected boundary level to its code:
     * IF(deepest<>"", VLOOKUP(path-to-deepest, mapping, codeOffset, 0), IF(nextUp<>"", ... , "")).
     * Empty when no level is selected, and IFERROR keeps an out-of-dropdown value blank rather than #N/A.
     *
     * Returned as a format template with {@value #ROW_PLACEHOLDER} wherever the row number goes; every
     * other fragment is an internal constant or a column letter, so no stray format specifiers appear.
     */
    private String buildBoundaryCodeFormulaTemplate(List<Integer> levelCols, String mappingRange) {
        // VLOOKUP's result index is the code column's 1-based offset WITHIN the mapping range, so it
        // must be derived from the two mapping constants rather than assuming they are adjacent.
        int codeColumnOffset = HierarchicalBoundaryUtil.CODE_MAPPING_CODE_COLUMN
                - HierarchicalBoundaryUtil.CODE_MAPPING_KEY_COLUMN + 1;

        StringBuilder formula = new StringBuilder();
        int nestCount = 0;

        for (int i = levelCols.size() - 1; i >= 0; i--) {
            String levelRef = CellReference.convertNumToColString(levelCols.get(i)) + ROW_PLACEHOLDER;
            StringBuilder path = new StringBuilder();
            for (int j = 0; j <= i; j++) {
                if (j > 0) path.append(",\"").append(HierarchicalBoundaryUtil.BOUNDARY_SEPARATOR).append("\",");
                path.append(CellReference.convertNumToColString(levelCols.get(j))).append(ROW_PLACEHOLDER);
            }
            formula.append("IF(").append(levelRef).append("<>\"\",IFERROR(VLOOKUP(CONCATENATE(")
                    .append(path).append("),").append(mappingRange).append(",")
                    .append(codeColumnOffset).append(",0),\"\"),");
            nestCount++;
        }

        formula.append("\"\"");
        for (int i = 0; i < nestCount; i++) {
            formula.append(")");
        }
        return formula.toString();
    }
}
