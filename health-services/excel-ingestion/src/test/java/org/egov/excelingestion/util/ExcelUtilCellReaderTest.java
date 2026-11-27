package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the safe performance refactors in ExcelUtil:
 *  - reusing a single FormulaEvaluator for formula cells (output must be unchanged)
 *  - lazily allocating the multi-select reconstruction map (output must be unchanged)
 */
class ExcelUtilCellReaderTest {

    private final ExcelUtil excelUtil = new ExcelUtil();

    /** Formula cells must still evaluate to their result value when read via the shared evaluator. */
    @Test
    void convertSheetToMapList_evaluatesFormulaCells() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");

            // Row 0 = technical headers (used for keys), row 1 = localized header (skipped), data from row 2.
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("numCol");
            header.createCell(1).setCellValue("strCol");
            sheet.createRow(1); // second header row, ignored by the parser

            Row data = sheet.createRow(2);
            data.createCell(0).setCellFormula("5+3");           // numeric formula -> 8
            data.createCell(1).setCellFormula("\"ab\"&\"cd\""); // string formula  -> "abcd"
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll(); // a saved xlsx always carries cached values

            List<Map<String, Object>> rows = excelUtil.convertSheetToMapListCached("fs-1", "Data", sheet);

            assertEquals(1, rows.size());
            assertEquals(8L, rows.get(0).get("numCol"), "numeric formula should evaluate to 8");
            assertEquals("abcd", rows.get(0).get("strCol"), "string formula should evaluate to abcd");
        }
    }

    /** A reused evaluator must produce the same value as a freshly created (null) one. */
    @Test
    void getCellValue_withSharedEvaluator_matchesLazyEvaluator() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("S");
            Cell formulaCell = sheet.createRow(0).createCell(0);
            formulaCell.setCellFormula("10*2");

            FormulaEvaluator shared = wb.getCreationHelper().createFormulaEvaluator();

            assertEquals(ExcelUtil.getCellValue(formulaCell), ExcelUtil.getCellValue(formulaCell, shared));
            assertEquals(ExcelUtil.getCellValueAsString(formulaCell),
                    ExcelUtil.getCellValueAsString(formulaCell, shared));
            assertEquals(20L, ExcelUtil.getCellValue(formulaCell, shared));
        }
    }

    /** Multi-select columns reconstruct into the comma-joined parent value. */
    @Test
    void reconstructMultiSelect_buildsParentValue() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("skills_MULTISELECT_1", "Java");
        row.put("skills_MULTISELECT_2", "Python");

        List<Map<String, Object>> data = new ArrayList<>();
        data.add(row);

        ExcelUtil.reconstructMultiSelectValues(data);

        assertEquals("Java,Python", data.get(0).get("skills"));
    }

    /** Rows with no _MULTISELECT_ columns must be left untouched (lazy path = no-op). */
    @Test
    void reconstructMultiSelect_noMultiselect_leavesRowUnchanged() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "John");
        row.put("age", 25L);

        List<Map<String, Object>> data = new ArrayList<>();
        data.add(row);

        ExcelUtil.reconstructMultiSelectValues(data);

        assertEquals(2, data.get(0).size(), "no synthetic keys should be added");
        assertEquals("John", data.get(0).get("name"));
        assertEquals(25L, data.get(0).get("age"));
    }

    /**
     * Last-row detection must ignore a trailing tail of formula rows whose cached result is empty
     * (the template pre-fills thousands of such lookup-formula rows). It must return the last
     * PLAIN data row, not the formula tail.
     */
    @Test
    void findActualLastRow_ignoresTrailingEmptyFormulaRows() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("name"); // technical header
            sheet.createRow(1).createCell(0).setCellValue("Name"); // localized header

            sheet.createRow(2).createCell(0).setCellValue("Alice"); // real data
            sheet.createRow(3).createCell(0).setCellValue("Bob");   // last real data row

            // Trailing template rows: empty-string formula in the boundary-code style.
            for (int r = 4; r <= 50; r++) {
                sheet.createRow(r).createCell(0).setCellFormula("\"\"");
            }
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll(); // populate cached "" values

            assertEquals(3, ExcelUtil.findActualLastRowWithData(sheet),
                    "should stop at last plain-data row, not the empty-formula tail");
        }
    }

    /** A row whose only populated cell is a formula with a non-empty cached result counts as data. */
    @Test
    void findActualLastRow_countsCachedNonEmptyFormula() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("code");
            sheet.createRow(1).createCell(0).setCellValue("Code");

            sheet.createRow(2).createCell(0).setCellValue("plain"); // row 2 data
            sheet.createRow(3).createCell(0).setCellFormula("\"BNDRY-1\""); // row 3: formula -> non-empty
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();

            assertEquals(3, ExcelUtil.findActualLastRowWithData(sheet),
                    "a formula cell with a non-empty cached value should be detected as data");
        }
    }

    /** Detection finds the LAST populated row even with an empty gap in the middle. */
    @Test
    void findActualLastRow_handlesGapBetweenDataRows() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("name");
            sheet.createRow(1).createCell(0).setCellValue("Name");

            sheet.createRow(2).createCell(0).setCellValue("Alice");
            sheet.createRow(3); // empty gap row
            sheet.createRow(4).createCell(0).setCellValue("Carol"); // last data after gap

            assertEquals(4, ExcelUtil.findActualLastRowWithData(sheet),
                    "last data row must be found even with an empty row in between");
        }
    }

    /**
     * Regression for role-not-empty validation: a row whose only populated cells are the plain
     * _MULTISELECT_ role children must still be detected and retained, so downstream
     * min-selection (role required) validation can run on it.
     */
    @Test
    void roleOnlyRow_isDetectedAndRetained() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("User List");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1");
            header.createCell(1).setCellValue("HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_2");
            sheet.createRow(1); // localized header

            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("DISTRIBUTOR"); // plain dropdown role child
            // second child left empty

            assertEquals(2, ExcelUtil.findActualLastRowWithData(sheet),
                    "a row with only role children selected must be detected as data");

            List<Map<String, Object>> rows = excelUtil.convertSheetToMapListCached("fs-role", "User List", sheet);
            assertEquals(1, rows.size(), "the role-only row must be retained for validation");
            assertEquals("DISTRIBUTOR", rows.get(0).get("HCM_ADMIN_CONSOLE_USER_ROLE"),
                    "reconstructed parent role value must be present");
        }
    }
}
