package org.egov.excelingestion.generator;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * The attendance register sheet auto-fills Register ID from the boundary the user selects, so the
 * hidden code column carries a per-row lookup and Register ID references it. Formulas are actually
 * EVALUATED here (POI FormulaEvaluator) rather than string-matched, so the test proves the observable
 * behaviour: blank until a boundary is picked, the boundary's code once it is.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceRegisterSheetGeneratorTest {

    @Mock
    private ExcelIngestionConfig config;

    private static final String SHEET = "Attendance Registers";
    private static final int COUNTRY_COL = 0;
    private static final int STATE_COL = 1;
    private static final int CODE_COL = 2;
    private static final int REGISTER_ID_COL = 3;

    // Section-2 mapping rows on the lookup sheet (1-based, as the layout reports them)
    private static final int MAPPING_START = 5;
    private static final int MAPPING_END = 6;

    private static final String COUNTRY_CODE = "HIER_NG";
    private static final String STATE_CODE = "HIER_NG_02_OYO";

    private AttendanceRegisterSheetGenerator newGenerator() {
        return new AttendanceRegisterSheetGenerator(null, null, null, null, null, null, null, config);
    }

    private HierarchicalBoundaryUtil.BoundaryColumnLayout layout() {
        return new HierarchicalBoundaryUtil.BoundaryColumnLayout(
                CODE_COL, Arrays.asList(COUNTRY_COL, STATE_COL), MAPPING_START, MAPPING_END);
    }

    private void invoke(XSSFWorkbook wb, HierarchicalBoundaryUtil.BoundaryColumnLayout layout) throws Exception {
        Method m = AttendanceRegisterSheetGenerator.class.getDeclaredMethod(
                "addBoundaryCodeAndRegisterIdFormulas", XSSFWorkbook.class, String.class,
                HierarchicalBoundaryUtil.BoundaryColumnLayout.class);
        m.setAccessible(true);
        m.invoke(newGenerator(), wb, SHEET, layout);
    }

    /** Register sheet with the hidden key row, plus the lookup sheet's display-path -> code mapping. */
    private XSSFWorkbook workbookWithLayout() {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(SHEET);
        Row hidden = sheet.createRow(0);
        hidden.createCell(COUNTRY_COL).setCellValue("HIER_COUNTRY");
        hidden.createCell(STATE_COL).setCellValue("HIER_STATE");
        hidden.createCell(CODE_COL).setCellValue(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY);
        hidden.createCell(REGISTER_ID_COL).setCellValue(ProcessingConstants.REGISTER_ID_COLUMN_KEY);

        Sheet lookup = wb.createSheet(HierarchicalBoundaryUtil.LOOKUP_SHEET_NAME);
        Row m1 = lookup.createRow(MAPPING_START - 1);
        m1.createCell(HierarchicalBoundaryUtil.CODE_MAPPING_KEY_COLUMN).setCellValue("Nigeria");
        m1.createCell(HierarchicalBoundaryUtil.CODE_MAPPING_CODE_COLUMN).setCellValue(COUNTRY_CODE);
        Row m2 = lookup.createRow(MAPPING_END - 1);
        m2.createCell(HierarchicalBoundaryUtil.CODE_MAPPING_KEY_COLUMN).setCellValue("Nigeria#Oyo");
        m2.createCell(HierarchicalBoundaryUtil.CODE_MAPPING_CODE_COLUMN).setCellValue(STATE_CODE);
        return wb;
    }

    private void stubRowLimit(int rowLimit) {
        lenient().when(config.getExcelRowLimit()).thenReturn(rowLimit);
    }

    private String evaluatedString(FormulaEvaluator evaluator, Cell cell) {
        return evaluator.evaluate(cell).getStringValue();
    }

    @Test
    void registerIdResolvesToTheDeepestSelectedBoundaryCode() throws Exception {
        stubRowLimit(5000);
        try (XSSFWorkbook wb = workbookWithLayout()) {
            invoke(wb, layout());

            Sheet sheet = wb.getSheet(SHEET);
            Row row = sheet.getRow(2); // Excel row 3
            row.createCell(COUNTRY_COL).setCellValue("Nigeria");
            row.createCell(STATE_COL).setCellValue("Oyo");

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            assertEquals(STATE_CODE, evaluatedString(evaluator, row.getCell(CODE_COL)));
            assertEquals(STATE_CODE, evaluatedString(evaluator, row.getCell(REGISTER_ID_COL)));
        }
    }

    @Test
    void registerIdResolvesToTheParentCodeWhenOnlyTheTopLevelIsSelected() throws Exception {
        stubRowLimit(5000);
        try (XSSFWorkbook wb = workbookWithLayout()) {
            invoke(wb, layout());

            Row row = wb.getSheet(SHEET).getRow(2);
            row.createCell(COUNTRY_COL).setCellValue("Nigeria");

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            assertEquals(COUNTRY_CODE, evaluatedString(evaluator, row.getCell(REGISTER_ID_COL)));
        }
    }

    @Test
    void registerIdIsBlankWhenNoBoundaryIsSelected() throws Exception {
        stubRowLimit(5000);
        try (XSSFWorkbook wb = workbookWithLayout()) {
            invoke(wb, layout());

            Row row = wb.getSheet(SHEET).getRow(2);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            // The reported regression was a bare reference rendering as 0 - it must be "" instead.
            assertEquals("", evaluatedString(evaluator, row.getCell(CODE_COL)));
            assertEquals("", evaluatedString(evaluator, row.getCell(REGISTER_ID_COL)));
        }
    }

    @Test
    void anOutOfDropdownSelectionLeavesTheCodeBlankRatherThanErroring() throws Exception {
        stubRowLimit(5000);
        try (XSSFWorkbook wb = workbookWithLayout()) {
            invoke(wb, layout());

            Row row = wb.getSheet(SHEET).getRow(2);
            row.createCell(COUNTRY_COL).setCellValue("Atlantis");

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            assertEquals("", evaluatedString(evaluator, row.getCell(CODE_COL)));
        }
    }

    @Test
    void registerIdCellStaysUnlockedSoTheUserCanOverrideIt() throws Exception {
        stubRowLimit(5000);
        try (XSSFWorkbook wb = workbookWithLayout()) {
            invoke(wb, layout());

            Cell registerId = wb.getSheet(SHEET).getRow(2).getCell(REGISTER_ID_COL);
            assertFalse(registerId.getCellStyle().getLocked(), "Register ID must remain user-editable");
        }
    }

    @Test
    void fillSpansTheConfiguredExcelRowLimit() throws Exception {
        stubRowLimit(10);
        try (XSSFWorkbook wb = workbookWithLayout()) {
            invoke(wb, layout());

            Sheet sheet = wb.getSheet(SHEET);
            assertEquals(CellType.FORMULA, sheet.getRow(2).getCell(REGISTER_ID_COL).getCellType());
            assertEquals(CellType.FORMULA, sheet.getRow(10).getCell(REGISTER_ID_COL).getCellType());
            assertNull(sheet.getRow(11), "fill must stop at excelRowLimit");
        }
    }

    @Test
    void noFormulasAreWrittenWhenThereIsNoBoundaryCodeMapping() throws Exception {
        try (XSSFWorkbook wb = workbookWithLayout()) {
            invoke(wb, new HierarchicalBoundaryUtil.BoundaryColumnLayout(
                    CODE_COL, Arrays.asList(COUNTRY_COL, STATE_COL), 0, 0));

            assertNull(wb.getSheet(SHEET).getRow(2), "an empty mapping must leave the sheet untouched");
        }
    }

    @Test
    void noFormulasAreWrittenWhenTheLayoutIsAbsent() throws Exception {
        try (XSSFWorkbook wb = workbookWithLayout()) {
            invoke(wb, null);
            assertNull(wb.getSheet(SHEET).getRow(2), "a null layout must leave the sheet untouched");
        }
    }

    @Test
    void noFormulasAreWrittenWhenTheRegisterIdColumnIsMissing() throws Exception {
        try (XSSFWorkbook wb = workbookWithLayout()) {
            wb.getSheet(SHEET).getRow(0).getCell(REGISTER_ID_COL).setCellValue("SOMETHING_ELSE");
            invoke(wb, layout());
            assertNull(wb.getSheet(SHEET).getRow(2), "a missing Register ID column must be a no-op");
        }
    }

    @Test
    void noFormulasAreWrittenWhenThereAreNoVisibleBoundaryColumns() throws Exception {
        try (XSSFWorkbook wb = workbookWithLayout()) {
            List<Integer> none = java.util.Collections.emptyList();
            invoke(wb, new HierarchicalBoundaryUtil.BoundaryColumnLayout(
                    CODE_COL, none, MAPPING_START, MAPPING_END));
            assertNull(wb.getSheet(SHEET).getRow(2), "no boundary levels means nothing to resolve from");
        }
    }

    @Test
    void theLayoutDoesNotExposeItsCallersMutableList() {
        List<Integer> mutable = new java.util.ArrayList<>(Arrays.asList(COUNTRY_COL, STATE_COL));
        HierarchicalBoundaryUtil.BoundaryColumnLayout layout = new HierarchicalBoundaryUtil.BoundaryColumnLayout(
                CODE_COL, mutable, MAPPING_START, MAPPING_END);
        mutable.add(99);
        assertEquals(2, layout.getVisibleBoundaryColIndices().size(), "layout must not track later mutation");
    }

    @Test
    void theVlookupResultIndexIsDerivedFromTheMappingColumnsNotHardcoded() throws Exception {
        stubRowLimit(5000);
        try (XSSFWorkbook wb = workbookWithLayout()) {
            invoke(wb, layout());
            String formula = wb.getSheet(SHEET).getRow(2).getCell(CODE_COL).getCellFormula();
            int expectedOffset = HierarchicalBoundaryUtil.CODE_MAPPING_CODE_COLUMN
                    - HierarchicalBoundaryUtil.CODE_MAPPING_KEY_COLUMN + 1;
            assertTrue(formula.contains("," + expectedOffset + ",0)"),
                    "VLOOKUP index must follow the mapping columns, was: " + formula);
        }
    }

    @Test
    void theSharedBoundaryUtilStillAdvertisesTheMappingColumnsThisFormulaDependsOn() {
        // Guards the coupling: the formula reads Section 2 via these two constants.
        assertEquals(3, HierarchicalBoundaryUtil.CODE_MAPPING_KEY_COLUMN);
        assertEquals(4, HierarchicalBoundaryUtil.CODE_MAPPING_CODE_COLUMN);
        assertTrue(HierarchicalBoundaryUtil.LOOKUP_SHEET_NAME.startsWith("_h_"));
    }
}
