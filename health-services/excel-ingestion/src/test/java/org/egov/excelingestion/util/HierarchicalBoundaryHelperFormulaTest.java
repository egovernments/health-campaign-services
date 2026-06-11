package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.request.RequestInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Edge-case tests for the cascading-boundary HELPER column formula.
 *
 * Context: the helper formula was changed from the volatile form
 *   IFERROR(INDEX(..,MATCH(CONCATENATE(INDIRECT("B"&ROW()), ...),..,0)),"")
 * to a non-volatile form that uses literal relative cell references for the current row:
 *   IFERROR(INDEX(..,MATCH(CONCATENATE(B3, "#", C3),..,0)),"")
 *
 * INDIRECT is a volatile function, so the old form forced every helper cell to recalculate on
 * every edit/open/scroll across the whole sheet. These tests pin the new behaviour:
 *  - no helper cell formula contains INDIRECT or ROW() (i.e. it is non-volatile),
 *  - each helper cell references its own Excel row (POI rowIdx + 1),
 *  - multi-level helpers concatenate all ancestor visible columns with the "#" separator,
 *  - the formula is written for every row up to the configured row limit,
 *  - formulas differ row-to-row (proving they are genuinely per-row, not a shared row-agnostic string).
 */
class HierarchicalBoundaryHelperFormulaTest {

    @Mock
    private ExcelIngestionConfig config;
    @Mock
    private BoundaryService boundaryService;
    @Mock
    private BoundaryUtil boundaryUtil;
    @Mock
    private ExcelStyleHelper excelStyleHelper;

    private HierarchicalBoundaryUtil hierarchicalBoundaryUtil;
    private XSSFWorkbook workbook;
    private AutoCloseable closeable;

    private static final String HELPER_SUFFIX = "_HELPER";

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        hierarchicalBoundaryUtil = new HierarchicalBoundaryUtil(config, boundaryService, boundaryUtil, excelStyleHelper);
        workbook = new XSSFWorkbook();
        when(config.getDefaultHeaderColor()).thenReturn("BLUE");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (workbook != null) workbook.close();
        if (closeable != null) closeable.close();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Runs addHierarchicalBoundaryColumn for a hierarchy with the given number of levels and the
     * given configured row limit, then returns the populated sheet. One schema column ("FIELD1")
     * is placed at column 0 so boundary columns start at column 1.
     */
    private Sheet generateBoundarySheet(int numLevels, int rowLimit) {
        when(config.getExcelRowLimit()).thenReturn(rowLimit);

        Sheet sheet = workbook.createSheet("TestSheet");
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);
        hiddenRow.createCell(0).setCellValue("FIELD1");
        visibleRow.createCell(0).setCellValue("Field 1");

        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "Boundary Code");

        // Build hierarchy levels: Level1, Level2, ...
        BoundaryHierarchy hierarchy = new BoundaryHierarchy();
        List<BoundaryHierarchyChild> children = new ArrayList<>();
        for (int i = 1; i <= numLevels; i++) {
            BoundaryHierarchyChild child = new BoundaryHierarchyChild();
            child.setBoundaryType("Level" + i);
            children.add(child);
        }
        hierarchy.setBoundaryHierarchy(children);
        BoundaryHierarchyResponse hierarchyResponse = new BoundaryHierarchyResponse();
        hierarchyResponse.setBoundaryHierarchy(Collections.singletonList(hierarchy));

        when(boundaryService.fetchBoundaryRelationship(any(), any(), any())).thenReturn(new BoundarySearchResponse());
        when(boundaryService.fetchBoundaryHierarchy(any(), any(), any())).thenReturn(hierarchyResponse);
        when(boundaryUtil.buildCodeToBoundaryMap(any())).thenReturn(new HashMap<>());

        // One boundary row whose path covers every level (ROOT -> B1 -> B2 -> ...)
        List<String> path = new ArrayList<>();
        for (int i = 0; i < numLevels; i++) {
            path.add(i == 0 ? "ROOT" : "B" + i);
        }
        BoundaryUtil.BoundaryRowData rowData =
                new BoundaryUtil.BoundaryRowData(path, path.get(path.size() - 1));
        when(boundaryUtil.processBoundariesWithEnrichment(any(), any(), any()))
                .thenReturn(Collections.singletonList(rowData));

        List<Boundary> boundaries = Collections.singletonList(
                Boundary.builder().code("B1").name("ROOT").type("Level1").build());

        hierarchicalBoundaryUtil.addHierarchicalBoundaryColumn(
                workbook, "TestSheet", localizationMap, boundaries, "hierarchy1", "tenant1", new RequestInfo());

        return sheet;
    }

    /** Column indices whose technical header (row 0) ends with "_HELPER", in left-to-right order. */
    private List<Integer> helperColumnIndices(Sheet sheet) {
        List<Integer> helperCols = new ArrayList<>();
        Row hiddenRow = sheet.getRow(0);
        short lastCell = hiddenRow.getLastCellNum();
        for (int c = 0; c < lastCell; c++) {
            Cell cell = hiddenRow.getCell(c);
            if (cell != null && cell.getCellType() == CellType.STRING
                    && cell.getStringCellValue().endsWith(HELPER_SUFFIX)) {
                helperCols.add(c);
            }
        }
        return helperCols;
    }

    private String helperFormulaAt(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        assertNotNull(row, "Expected a row at index " + rowIdx);
        Cell cell = row.getCell(colIdx);
        assertNotNull(cell, "Expected a helper cell at row " + rowIdx + ", col " + colIdx);
        assertEquals(CellType.FORMULA, cell.getCellType(),
                "Helper cell at row " + rowIdx + ", col " + colIdx + " should hold a formula");
        return cell.getCellFormula();
    }

    // ---------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    void helperFormula_isNonVolatile_noIndirectNoRowFunction() {
        Sheet sheet = generateBoundarySheet(3, 20);
        List<Integer> helperCols = helperColumnIndices(sheet);
        assertEquals(2, helperCols.size(), "A 3-level hierarchy should produce 2 helper columns (levels 2 and 3)");

        for (int colIdx : helperCols) {
            for (int r = 2; r <= 20; r++) {
                String formula = helperFormulaAt(sheet, r, colIdx);
                assertFalse(formula.contains("INDIRECT"),
                        "Helper formula must not use volatile INDIRECT: " + formula);
                assertFalse(formula.contains("ROW()"),
                        "Helper formula must not use ROW(): " + formula);
            }
        }
    }

    @Test
    void helperFormula_usesRelativeReferenceToOwnRow() {
        Sheet sheet = generateBoundarySheet(3, 10);
        List<Integer> helperCols = helperColumnIndices(sheet);

        // First helper column corresponds to Level2, whose only parent is the Level1 visible column.
        // With FIELD1 at column 0, the Level1 visible column is B; helper col itself is at index 2.
        int firstHelperCol = helperCols.get(0);

        for (int r = 2; r <= 10; r++) {
            int excelRow = r + 1; // POI 0-based -> Excel 1-based
            String formula = helperFormulaAt(sheet, r, firstHelperCol);
            assertTrue(formula.contains("CONCATENATE(B" + excelRow + ")"),
                    "Row " + r + " helper should reference B" + excelRow + " but was: " + formula);
            // Must not reference a neighbouring row's cell.
            assertFalse(formula.contains("B" + (excelRow + 1)),
                    "Row " + r + " helper should not reference B" + (excelRow + 1) + ": " + formula);
            assertFalse(formula.contains("B" + (excelRow - 1)),
                    "Row " + r + " helper should not reference B" + (excelRow - 1) + ": " + formula);
        }
    }

    @Test
    void helperFormula_multiLevelConcatenatesAllAncestorsWithSeparator() {
        Sheet sheet = generateBoundarySheet(4, 8); // 4 levels -> 3 helper columns
        List<Integer> helperCols = helperColumnIndices(sheet);
        assertEquals(3, helperCols.size(), "A 4-level hierarchy should produce 3 helper columns");

        // Deepest helper (Level4) concatenates the three ancestor visible columns: B, D, F.
        int deepestHelperCol = helperCols.get(helperCols.size() - 1);
        int rowIdx = 5;
        int excelRow = rowIdx + 1;
        String formula = helperFormulaAt(sheet, rowIdx, deepestHelperCol);

        assertTrue(formula.contains("B" + excelRow), "Deepest helper should reference B" + excelRow + ": " + formula);
        assertTrue(formula.contains("D" + excelRow), "Deepest helper should reference D" + excelRow + ": " + formula);
        assertTrue(formula.contains("F" + excelRow), "Deepest helper should reference F" + excelRow + ": " + formula);
        // Two "#" separators join three ancestor references.
        int separators = formula.split("\"#\"", -1).length - 1;
        assertEquals(2, separators, "Three ancestors must be joined by two '#' separators: " + formula);
    }

    @Test
    void helperFormula_singleCascadeLevel_hasNoSeparator() {
        Sheet sheet = generateBoundarySheet(2, 6); // 2 levels -> exactly 1 helper column
        List<Integer> helperCols = helperColumnIndices(sheet);
        assertEquals(1, helperCols.size(), "A 2-level hierarchy should produce exactly 1 helper column");

        String formula = helperFormulaAt(sheet, 2, helperCols.get(0));
        assertTrue(formula.contains("CONCATENATE(B3)"), "Single-level helper should be CONCATENATE(B3): " + formula);
        assertFalse(formula.contains("\"#\""), "Single-parent helper must not contain a separator: " + formula);
        assertFalse(formula.contains("INDIRECT"), formula);
    }

    @Test
    void helperFormula_writtenForEveryRowUpToConfiguredLimit() {
        int rowLimit = 5;
        Sheet sheet = generateBoundarySheet(3, rowLimit);
        List<Integer> helperCols = helperColumnIndices(sheet);

        for (int colIdx : helperCols) {
            for (int r = 2; r <= rowLimit; r++) {
                String formula = helperFormulaAt(sheet, r, colIdx); // asserts a FORMULA cell exists
                assertTrue(formula.startsWith("IFERROR(INDEX("),
                        "Helper formula shape changed unexpectedly: " + formula);
            }
        }
    }

    @Test
    void helperFormula_differsAcrossRows_provingPerRowReferences() {
        Sheet sheet = generateBoundarySheet(3, 10);
        int firstHelperCol = helperColumnIndices(sheet).get(0);

        String row2 = helperFormulaAt(sheet, 2, firstHelperCol);
        String row3 = helperFormulaAt(sheet, 3, firstHelperCol);
        String row9 = helperFormulaAt(sheet, 9, firstHelperCol);

        assertNotEquals(row2, row3, "Adjacent rows must have distinct relative references");
        assertNotEquals(row3, row9, "Distant rows must have distinct relative references");
        assertTrue(row2.contains("B3"));
        assertTrue(row3.contains("B4"));
        assertTrue(row9.contains("B10"));
    }
}
