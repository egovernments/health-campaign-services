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

import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for the scaffold-less cascading dropdown design in {@link HierarchicalBoundaryUtil}:
 * no per-row helper columns or boundary-code formulas exist; each cascade level is enforced by a
 * single data-validation formula
 *   INDIRECT(IFERROR("_hL"&MATCH(&lt;parent path&gt;, lookup!$A$1:$A$N, 0), "_h_EmptyList"))
 * over the whole column range, resolved against positional per-parent named ranges. This keeps the
 * generated file's size and row count INDEPENDENT of excelRowLimit - asserted here so a per-row
 * scaffold can never silently come back.
 */
class HierarchicalBoundaryCascadeValidationTest {

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
    // Fixture: one schema column ("FIELD1") at column 0, boundary columns from column 1 (B, C, ...).
    // One boundary path ROOT -> B1 -> B2 ... covering every level; no localization, so display
    // names equal codes and the lookup keys are "ROOT", "ROOT#B1", ...
    // ---------------------------------------------------------------------------------------------

    private Sheet generateBoundarySheet(XSSFWorkbook wb, int numLevels, int rowLimit) {
        when(config.getExcelRowLimit()).thenReturn(rowLimit);

        Sheet sheet = wb.createSheet("TestSheet");
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);
        hiddenRow.createCell(0).setCellValue("FIELD1");
        visibleRow.createCell(0).setCellValue("Field 1");

        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "Boundary Code");

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
                wb, "TestSheet", localizationMap, boundaries, "hierarchy1", "tenant1", new RequestInfo());

        return sheet;
    }

    private String validationFormulaForColumn(Sheet sheet, int colIndex) {
        for (DataValidation dv : sheet.getDataValidations()) {
            for (org.apache.poi.ss.util.CellRangeAddress addr : dv.getRegions().getCellRangeAddresses()) {
                if (addr.getFirstColumn() <= colIndex && colIndex <= addr.getLastColumn()) {
                    return dv.getValidationConstraint().getFormula1();
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    void noPerRowScaffold_noHelperColumns_noFormulaCells() {
        Sheet sheet = generateBoundarySheet(workbook, 3, 5000);

        // No helper columns in the technical header
        Row hiddenRow = sheet.getRow(0);
        for (int c = 0; c < hiddenRow.getLastCellNum(); c++) {
            Cell cell = hiddenRow.getCell(c);
            if (cell != null && cell.getCellType() == CellType.STRING) {
                assertFalse(cell.getStringCellValue().endsWith("_HELPER"),
                        "No helper columns must exist, found: " + cell.getStringCellValue());
            }
        }
        // Only the two header rows are materialized - no data-area rows at all
        assertEquals(2, sheet.getPhysicalNumberOfRows(),
                "Only header rows may be materialized; the paste area must stay empty");
        assertNull(sheet.getRow(2), "First data row must not be materialized");
        assertNull(sheet.getRow(2500), "Mid paste-area row must not be materialized");
        assertNull(sheet.getRow(5000), "Last paste-area row must not be materialized");
    }

    @Test
    void cascadeValidationFormula_matchesParentPath_andIndirectsPositionalName() {
        Sheet sheet = generateBoundarySheet(workbook, 3, 100);
        // Layout: FIELD1(A), L1(B), L2(C), L3(D), code(E). Lookup Section 1 has 2 parent rows
        // ("ROOT", "ROOT#B1"), so the MATCH range is $A$1:$A$2.
        String level2 = validationFormulaForColumn(sheet, 2);
        assertEquals(
                "INDIRECT(IFERROR(\"_hL\"&MATCH(B3,_h_SimpleLookup_h_!$A$1:$A$2,0),\"_h_EmptyList\"))",
                level2, "Level-2 cascade formula must MATCH the level-1 cell against the lookup keys");

        String level3 = validationFormulaForColumn(sheet, 3);
        assertEquals(
                "INDIRECT(IFERROR(\"_hL\"&MATCH(B3&\"#\"&C3,_h_SimpleLookup_h_!$A$1:$A$2,0),\"_h_EmptyList\"))",
                level3, "Level-3 cascade formula must MATCH the level-1#level-2 path");
    }

    @Test
    void cascadeValidationFormula_staysUnderExcel255CharLimit_forDeepHierarchies() {
        Sheet sheet = generateBoundarySheet(workbook, 6, 100);
        for (int col = 2; col <= 6; col++) {
            String formula = validationFormulaForColumn(sheet, col);
            assertNotNull(formula, "Cascade validation must exist on column " + col);
            assertTrue(formula.length() <= 255,
                    "Validation formula must fit Excel's 255-char limit, was " + formula.length() + ": " + formula);
        }
    }

    @Test
    void lookupSheet_usesDisplayPathKeys_andPositionalChildNames() {
        generateBoundarySheet(workbook, 3, 100);
        Sheet lookup = workbook.getSheet(HierarchicalBoundaryUtil.LOOKUP_SHEET_NAME);
        assertNotNull(lookup, "Lookup sheet must exist");

        // Section 1: column A holds plain display-path keys (NOT hashes)
        assertEquals("ROOT", lookup.getRow(0).getCell(0).getStringCellValue());
        assertEquals("ROOT#B1", lookup.getRow(1).getCell(0).getStringCellValue());
        // Children next to the keys
        assertEquals("B1", lookup.getRow(0).getCell(1).getStringCellValue());
        assertEquals("B2", lookup.getRow(1).getCell(1).getStringCellValue());

        // Positional named ranges span exactly the children cells of their row
        Name hl1 = workbook.getName("_hL1");
        assertNotNull(hl1, "Positional child-list name _hL1 must exist");
        assertEquals("_h_SimpleLookup_h_!$B$1:$B$1", hl1.getRefersToFormula());
        Name hl2 = workbook.getName("_hL2");
        assertNotNull(hl2, "Positional child-list name _hL2 must exist");
        assertEquals("_h_SimpleLookup_h_!$B$2:$B$2", hl2.getRefersToFormula());

        // The empty-list fallback exists
        assertNotNull(workbook.getName(HierarchicalBoundaryUtil.EMPTY_LIST_NAME));
    }

    @Test
    void cascadeChain_evaluatesEndToEnd_viaPoiFormulaEngine() {
        // Functional lock-in of the exact expression the data validation uses: resolve it through
        // POI's evaluator (MATCH display key -> positional name -> INDIRECT -> COUNTA of children).
        Sheet sheet = generateBoundarySheet(workbook, 3, 100);
        Row row3 = sheet.createRow(2);
        row3.createCell(1).setCellValue("ROOT"); // level-1 selection in B3

        Cell probe = row3.createCell(9);
        probe.setCellFormula(
                "COUNTA(INDIRECT(IFERROR(\"_hL\"&MATCH(B3,_h_SimpleLookup_h_!$A$1:$A$2,0),\"_h_EmptyList\")))");
        Cell probeEmpty = sheet.createRow(3).createCell(9);
        probeEmpty.setCellFormula(
                "COUNTA(INDIRECT(IFERROR(\"_hL\"&MATCH(B4,_h_SimpleLookup_h_!$A$1:$A$2,0),\"_h_EmptyList\")))");

        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        assertEquals(1.0, evaluator.evaluate(probe).getNumberValue(),
                "Selected parent must resolve to its children list (1 child of ROOT)");
        assertEquals(0.0, evaluator.evaluate(probeEmpty).getNumberValue(),
                "Unselected parent must degrade to the empty list");
    }

    @Test
    void generatedSize_isIndependentOfRowLimit() throws Exception {
        // The property the redesign exists for: the template no longer grows with excelRowLimit.
        byte[] small;
        byte[] large;
        try (XSSFWorkbook wb1 = new XSSFWorkbook()) {
            // fresh mocks state applies per generate call (config row limit stubbed inside)
            generateBoundarySheet(wb1, 3, 100);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb1.write(bos);
            small = bos.toByteArray();
        }
        try (XSSFWorkbook wb2 = new XSSFWorkbook()) {
            generateBoundarySheet(wb2, 3, 20000);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb2.write(bos);
            large = bos.toByteArray();
        }
        assertTrue(Math.abs(large.length - small.length) < 1024,
                "File size must be row-limit-independent: rowLimit=100 -> " + small.length
                        + " bytes vs rowLimit=20000 -> " + large.length + " bytes");
    }
}
