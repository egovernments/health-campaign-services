package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.web.models.Boundary;
import org.egov.excelingestion.web.models.BoundaryHierarchy;
import org.egov.excelingestion.web.models.BoundaryHierarchyChild;
import org.egov.excelingestion.web.models.BoundaryHierarchyResponse;
import org.egov.excelingestion.web.models.BoundarySearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Every pre-filled row gets its boundary names, also past excelRowLimit (carry-over can pre-fill more rows than that,
 * e.g. 10,000 sites). Rows past the first 4,998 used to keep only the hidden boundary code.
 */
@ExtendWith(MockitoExtension.class)
class HierarchicalBoundaryPrefillAllRowsTest {

    private static final int EXCEL_ROW_LIMIT = 5000;
    private static final int PREFILLED_ROWS = 10000;
    // Fresh sheet: Level1, Level2, Level3 visible (0-2), hidden boundary code (3)
    private static final List<Integer> VISIBLE_BOUNDARY_COLS = Arrays.asList(0, 1, 2);
    private static final int BOUNDARY_CODE_COL = 3;

    @Mock private ExcelIngestionConfig config;
    @Mock private BoundaryService boundaryService;
    @Mock private BoundaryUtil boundaryUtil;

    private HierarchicalBoundaryUtil hierarchicalBoundaryUtil;

    @BeforeEach
    void setUp() {
        lenient().when(config.getExcelRowLimit()).thenReturn(EXCEL_ROW_LIMIT);
        lenient().when(config.getDefaultHeaderColor()).thenReturn("#93C47D");
        hierarchicalBoundaryUtil = new HierarchicalBoundaryUtil(config, boundaryService, boundaryUtil, new ExcelStyleHelper());

        lenient().when(boundaryService.fetchBoundaryRelationship(any(), any(), any())).thenReturn(new BoundarySearchResponse());
        List<BoundaryHierarchyChild> levels = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            BoundaryHierarchyChild child = new BoundaryHierarchyChild();
            child.setBoundaryType("Level" + i);
            levels.add(child);
        }
        BoundaryHierarchy hierarchy = new BoundaryHierarchy();
        hierarchy.setBoundaryHierarchy(levels);
        BoundaryHierarchyResponse hierarchyResponse = new BoundaryHierarchyResponse();
        hierarchyResponse.setBoundaryHierarchy(Collections.singletonList(hierarchy));
        lenient().when(boundaryService.fetchBoundaryHierarchy(any(), any(), any())).thenReturn(hierarchyResponse);
        lenient().when(boundaryUtil.buildCodeToBoundaryMap(any())).thenReturn(new HashMap<>());
        lenient().when(boundaryUtil.processBoundariesWithEnrichment(any(), any(), any()))
                .thenReturn(Collections.singletonList(new BoundaryUtil.BoundaryRowData(Arrays.asList("ROOT", "B1", "B2"), "B2")));
    }

    @Test
    void writesBoundaryNamesOnEveryPrefilledRowPastTheRowLimit() throws Exception {
        List<Map<String, Object>> prefilled = new ArrayList<>();
        for (int i = 0; i < PREFILLED_ROWS; i++) {
            prefilled.add(Map.of("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "B2"));
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Outside Household");
            hierarchicalBoundaryUtil.addHierarchicalBoundaryColumnWithData(workbook, "Outside Household", new HashMap<>(),
                    Collections.singletonList(Boundary.builder().code("ROOT").name("Root").type("Level1").build()),
                    "HIERARCHY1", "mz", new RequestInfo(), prefilled);

            Sheet sheet = workbook.getSheet("Outside Household");
            Row first = sheet.getRow(2);
            for (int rowIndex : new int[]{2 + EXCEL_ROW_LIMIT - 2, 2 + EXCEL_ROW_LIMIT - 1, 2 + PREFILLED_ROWS - 1}) {
                Row row = sheet.getRow(rowIndex);
                assertNotNull(row, "row " + rowIndex + " got no boundary columns");
                for (int col : VISIBLE_BOUNDARY_COLS) {
                    String name = row.getCell(col).getStringCellValue();
                    assertFalse(name.isBlank(), "row " + rowIndex + " column " + col + " has no boundary name");
                    assertEquals(first.getCell(col).getStringCellValue(), name);
                }
                assertEquals("B2", row.getCell(BOUNDARY_CODE_COL).getStringCellValue());
            }
        }
    }

    /** One pre-filled row with boundary code B2, generated against the given boundaries; returns its sheet row. */
    private Row prefillOneRow(XSSFWorkbook workbook, List<BoundaryUtil.BoundaryRowData> boundaries) {
        lenient().when(boundaryUtil.processBoundariesWithEnrichment(any(), any(), any())).thenReturn(boundaries);
        workbook.createSheet("Outside Household");
        hierarchicalBoundaryUtil.addHierarchicalBoundaryColumnWithData(workbook, "Outside Household", new HashMap<>(),
                Collections.singletonList(Boundary.builder().code("ROOT").name("Root").type("Level1").build()),
                "HIERARCHY1", "mz", new RequestInfo(), List.of(Map.of("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "B2")));
        return workbook.getSheet("Outside Household").getRow(2);
    }

    @Test
    void usesTheFirstBoundaryWithTheRowsCode() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Row row = prefillOneRow(workbook, Arrays.asList(
                    new BoundaryUtil.BoundaryRowData(Arrays.asList("ROOT", "OTHER", "B9"), "B9"),
                    new BoundaryUtil.BoundaryRowData(Arrays.asList("ROOT", "FIRST", "B2"), "B2"),
                    new BoundaryUtil.BoundaryRowData(Arrays.asList("ROOT", "SECOND", "B2"), "B2")));

            assertEquals("FIRST", row.getCell(1).getStringCellValue());
            assertEquals("B2", row.getCell(BOUNDARY_CODE_COL).getStringCellValue());
        }
    }

    @Test
    void keepsTheFirstMatchEvenWhenItsPathIsNull() {
        Map<String, List<String>> pathByCode = HierarchicalBoundaryUtil.pathByLastLevelCode(Arrays.asList(
                new BoundaryUtil.BoundaryRowData(null, "B2"),
                new BoundaryUtil.BoundaryRowData(Arrays.asList("ROOT", "SECOND", "B2"), "B2"),
                new BoundaryUtil.BoundaryRowData(Arrays.asList("ROOT", "B9"), null)));

        assertTrue(pathByCode.containsKey("B2"));
        assertNull(pathByCode.get("B2"));
        assertEquals(1, pathByCode.size(), "a boundary without a last-level code is left out");
    }

    @Test
    void leavesARowWithAnUnknownCodeBlank() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Row row = prefillOneRow(workbook, Collections.singletonList(
                    new BoundaryUtil.BoundaryRowData(Arrays.asList("ROOT", "B1", "B9"), "B9")));

            assertEquals("", row.getCell(1).getStringCellValue());
            assertEquals("", row.getCell(BOUNDARY_CODE_COL).getStringCellValue());
        }
    }
}
