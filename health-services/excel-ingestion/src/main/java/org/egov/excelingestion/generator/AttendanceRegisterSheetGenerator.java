package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
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
                if (shouldAddBoundaryDropdowns(generateResource)) {
                    List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries =
                            campaignService.getBoundariesFromCampaign(generateResource.getReferenceId(),
                                    generateResource.getTenantId(), requestInfo);

                    if (campaignBoundaries != null && !campaignBoundaries.isEmpty()) {
                        List<Boundary> enrichedBoundaries = boundaryUtil.getEnrichedBoundariesFromCampaign(
                                generateResource.getId(), generateResource.getReferenceId(),
                                generateResource.getTenantId(), generateResource.getHierarchyType(), requestInfo);

                        hierarchicalBoundaryUtil.addHierarchicalBoundaryColumnWithData(
                                workbook, sheetName, localizationMap, enrichedBoundaries,
                                generateResource.getHierarchyType(), generateResource.getTenantId(),
                                requestInfo, null);
                    }
                }

                // Add schema columns (Register ID, etc.) using ExcelDataPopulator
                workbook = (XSSFWorkbook) excelDataPopulator.populateSheetWithData(
                        workbook, sheetName, columns, null, localizationMap);

                // Add Register ID auto-populate formulas
                addRegisterIdFormulas(workbook, sheetName, generateResource.getHierarchyType());
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
     * Add Register ID formulas that auto-populate from the rightmost non-empty boundary column.
     * Formula: IF(lastBoundary<>"", lastBoundary, IF(secondLast<>"", secondLast, ...))
     * Uses unlocked cell style so users can manually override.
     */
    private void addRegisterIdFormulas(XSSFWorkbook workbook, String sheetName, String hierarchyType) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) return;

        Row hiddenRow = sheet.getRow(0);
        if (hiddenRow == null) return;

        // Find boundary code column, visible boundary columns, and Register ID column from hidden row 0
        String hierarchyPrefix = hierarchyType != null ? hierarchyType.toUpperCase() + "_" : "";
        List<Integer> visibleBoundaryColIndices = new ArrayList<>();
        int registerIdColIndex = -1;
        int boundaryCodeColIndex = -1;

        for (int colIdx = 0; colIdx <= hiddenRow.getLastCellNum(); colIdx++) {
            Cell cell = hiddenRow.getCell(colIdx);
            if (cell == null) continue;
            String cellValue = cell.getStringCellValue();
            if (cellValue == null) continue;

            // Check if it's a visible boundary column (has hierarchy prefix, not hidden, not a helper)
            if (!hierarchyPrefix.isEmpty() && cellValue.startsWith(hierarchyPrefix)
                    && !cellValue.endsWith("_HELPER") && !sheet.isColumnHidden(colIdx)) {
                visibleBoundaryColIndices.add(colIdx);
            }

            // Check for Register ID column
            if (ProcessingConstants.REGISTER_ID_COLUMN_KEY.equals(cellValue)) {
                registerIdColIndex = colIdx;
            }

            // Check for boundary code column (hidden column with VLOOKUP result)
            if (ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY.equals(cellValue)) {
                boundaryCodeColIndex = colIdx;
            }
        }

        if (registerIdColIndex == -1) {
            log.info("Register ID column not found, skipping formula generation");
            return;
        }

        if (boundaryCodeColIndex == -1 && visibleBoundaryColIndices.isEmpty()) {
            log.info("Neither boundary code column nor visible boundary columns found, skipping formula generation");
            return;
        }

        // Create unlocked cell style for formula cells (users can override)
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);

        int maxRow = config.getAttendanceRegisterRows();

        // Apply formula to data rows (row 2 onwards, 0-indexed)
        for (int r = 2; r <= maxRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) row = sheet.createRow(r);

            Cell registerIdCell = row.getCell(registerIdColIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);

            String formula;
            if (boundaryCodeColIndex != -1) {
                // Reference the hidden boundary code column directly (contains boundary code via VLOOKUP)
                formula = CellReference.convertNumToColString(boundaryCodeColIndex) + (r + 1);
            } else {
                // Fallback: build nested IF over visible boundary display-name columns
                formula = buildRegisterIdFormula(r + 1, visibleBoundaryColIndices);
            }
            registerIdCell.setCellFormula(formula);
            registerIdCell.setCellStyle(unlocked);
        }

        log.info("Added Register ID formulas for {} rows using {} source",
                maxRow - 1, boundaryCodeColIndex != -1 ? "boundary code column" : "visible boundary columns");
    }

    /**
     * Build nested IF formula: IF(lastCol<>"", lastCol, IF(secondLastCol<>"", secondLastCol, ...))
     * Checks from rightmost to leftmost visible boundary column.
     */
    private String buildRegisterIdFormula(int excelRowNumber, List<Integer> visibleBoundaryColIndices) {
        StringBuilder formula = new StringBuilder();
        int nestCount = 0;

        // Iterate from rightmost to leftmost
        for (int i = visibleBoundaryColIndices.size() - 1; i >= 0; i--) {
            String colRef = CellReference.convertNumToColString(visibleBoundaryColIndices.get(i)) + excelRowNumber;
            formula.append("IF(").append(colRef).append("<>\"\",").append(colRef).append(",");
            nestCount++;
        }

        // Innermost: return empty string when no boundary is filled
        formula.append("\"\"");

        // Close all IF parentheses
        for (int i = 0; i < nestCount; i++) {
            formula.append(")");
        }

        return formula.toString();
    }
}
