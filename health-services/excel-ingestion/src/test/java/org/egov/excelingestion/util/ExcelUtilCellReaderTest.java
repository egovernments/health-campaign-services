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
}
