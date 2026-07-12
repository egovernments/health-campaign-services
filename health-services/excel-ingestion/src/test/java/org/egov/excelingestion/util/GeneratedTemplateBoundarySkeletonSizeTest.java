package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.generator.IExcelPopulatorSheetGenerator;
import org.egov.excelingestion.generator.ISheetGenerator;
import org.egov.excelingestion.generator.SchemaBasedSheetGenerator;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.ConfigBasedGenerationService;
import org.egov.excelingestion.service.GenerationConfigValidationService;
import org.egov.excelingestion.web.models.Boundary;
import org.egov.excelingestion.web.models.BoundaryHierarchy;
import org.egov.excelingestion.web.models.BoundaryHierarchyChild;
import org.egov.excelingestion.web.models.BoundaryHierarchyResponse;
import org.egov.excelingestion.web.models.BoundarySearchResponse;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationResult;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Proof for the boundary-column half of the template-size fix, driven through the REAL
 * {@link ConfigBasedGenerationService#generateExcelWithConfig} path with the real
 * {@link ExcelDataPopulator}, {@link CellProtectionManager} and {@link HierarchicalBoundaryUtil}
 * (only the remote-data services are mocked).
 *
 * <p>Generates a representative TWO-sheet template — a plain schema data sheet plus a data sheet
 * with cascading boundary dropdown columns (the FacilitySheetGenerator composition) — and asserts:
 * <ul>
 *   <li>the plain data sheet materializes ONLY header + data rows;</li>
 *   <li>the boundary sheet's skeleton rows carry ONLY the kept per-row formula cells
 *       (helper-hash + hidden boundary-code); the previously materialized empty unlocked cells of
 *       the visible dropdown columns are gone, replaced by unlocked DEFAULT COLUMN styles;</li>
 *   <li>the kept formulas are unchanged (helper INDEX/MATCH, boundary-code VLOOKUP);</li>
 *   <li>dropdown validations still span the full paste cap (excelRowLimit);</li>
 *   <li>measured cells + serialized bytes shrink vs the old materialize-every-visible-cell
 *       behavior, which is reproduced on a throwaway copy for the BEFORE numbers.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GeneratedTemplateBoundarySkeletonSizeTest {

    private static final int EXCEL_ROW_LIMIT = 2000;
    private static final int DATA_ROWS = 3;
    private static final int LEVELS = 3;
    private static final String PLAIN_SHEET = "PlainSheet";
    private static final String BOUNDARY_SHEET = "BoundarySheet";
    // Boundary block layout on a fresh sheet: L1 visible(0), L2 helper(1)+visible(2),
    // L3 helper(3)+visible(4), hidden boundary code(5). Schema columns follow from 6.
    private static final List<Integer> VISIBLE_BOUNDARY_COLS = Arrays.asList(0, 2, 4);
    private static final List<Integer> HELPER_COLS = Arrays.asList(1, 3);
    private static final int BOUNDARY_CODE_COL = 5;

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ExcelIngestionConfig config;
    @Mock
    private CustomExceptionHandler exceptionHandler;
    @Mock
    private GenerationConfigValidationService validationService;
    @Mock
    private BoundaryService boundaryService;
    @Mock
    private BoundaryUtil boundaryUtil;

    private ExcelStyleHelper styleHelper;
    private CellProtectionManager protectionManager;
    private ExcelDataPopulator populator;
    private HierarchicalBoundaryUtil hierarchicalBoundaryUtil;
    private ConfigBasedGenerationService service;

    @BeforeEach
    void setUp() {
        lenient().when(config.getExcelRowLimit()).thenReturn(EXCEL_ROW_LIMIT);
        lenient().when(config.getExcelSheetPassword()).thenReturn("pw");
        lenient().when(config.getValidationErrorColor()).thenReturn("#ff0000");
        lenient().when(config.getDefaultHeaderColor()).thenReturn("#93C47D");
        lenient().when(config.getSheetNameMaxLength()).thenReturn(31);
        lenient().when(config.getExcelSheetZoom()).thenReturn(100);

        styleHelper = new ExcelStyleHelper();
        protectionManager = new CellProtectionManager(config, styleHelper);
        populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        hierarchicalBoundaryUtil = new HierarchicalBoundaryUtil(config, boundaryService, boundaryUtil, styleHelper);
        service = new ConfigBasedGenerationService(applicationContext, populator,
                new BoundaryColumnUtil(config, boundaryService, boundaryUtil, styleHelper),
                hierarchicalBoundaryUtil, protectionManager, config, exceptionHandler, validationService);

        stubBoundaryData();

        // Route the service's reflective generator lookups to the two fake generators below.
        IExcelPopulatorSheetGenerator plainGenerator =
                (sheetConfig, generateResource, requestInfo, loc) -> SheetGenerationResult.builder()
                        .columnDefs(sampleColumns()).data(sampleData()).build();
        BoundaryDirectGenerator boundaryGenerator =
                new BoundaryDirectGenerator(hierarchicalBoundaryUtil, populator, sampleColumns(), sampleData());
        lenient().when(applicationContext.getBean(any(Class.class))).thenAnswer(invocation -> {
            Class<?> requested = invocation.getArgument(0);
            return requested == SchemaBasedSheetGenerator.class ? plainGenerator : boundaryGenerator;
        });
    }

    @Test
    void boundarySkeletonKeepsOnlyFormulaCellsAndValidatesFullPasteRange() throws IOException {
        byte[] afterBytes = service.generateExcelWithConfig(
                processorConfig(), generateResource(), new RequestInfo(), new HashMap<>());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(afterBytes))) {
            // --- Plain data sheet: ONLY header + data rows are materialized ---
            Sheet plain = wb.getSheet(PLAIN_SHEET);
            assertNotNull(plain, "Plain data sheet must exist");
            assertEquals(2 + DATA_ROWS, plain.getPhysicalNumberOfRows(),
                    "Plain data sheet must materialize only header + data rows");
            CellRangeAddress plainEnumRange = findValidationRangeForColumn(plain, indexOfColumn("status"));
            assertNotNull(plainEnumRange, "Plain sheet enum dropdown validation must exist");
            assertTrue(plainEnumRange.getLastRow() >= EXCEL_ROW_LIMIT,
                    "Plain sheet dropdown must span the paste cap, got " + plainEnumRange.getLastRow());

            // --- Boundary sheet: skeleton rows keep ONLY the load-bearing formula cells ---
            Sheet boundary = wb.getSheet(BOUNDARY_SHEET);
            assertNotNull(boundary, "Boundary data sheet must exist");
            int[] sampleRows = {2 + DATA_ROWS, EXCEL_ROW_LIMIT / 2, EXCEL_ROW_LIMIT};
            for (int r : sampleRows) {
                Row row = boundary.getRow(r);
                assertNotNull(row, "Skeleton row " + r + " must exist (kept per-row formulas)");
                assertEquals(HELPER_COLS.size() + 1, row.getPhysicalNumberOfCells(),
                        "Skeleton row " + r + " must carry only helper + boundary-code formula cells");
                for (int visibleCol : VISIBLE_BOUNDARY_COLS) {
                    assertNull(row.getCell(visibleCol),
                            "Visible dropdown cell must NOT be materialized at row " + r + " col " + visibleCol);
                }
                for (int helperCol : HELPER_COLS) {
                    Cell helper = row.getCell(helperCol);
                    assertNotNull(helper, "Helper formula cell must be kept at row " + r);
                    assertEquals(CellType.FORMULA, helper.getCellType());
                    String f = helper.getCellFormula();
                    assertTrue(f.contains("INDEX(") && f.contains("MATCH("),
                            "Helper formula must be the unchanged INDEX/MATCH lookup, got: " + f);
                }
                Cell code = row.getCell(BOUNDARY_CODE_COL);
                assertNotNull(code, "Boundary-code formula cell must be kept at row " + r);
                assertEquals(CellType.FORMULA, code.getCellType());
                assertTrue(code.getCellFormula().contains("VLOOKUP("),
                        "Boundary-code formula must be the unchanged VLOOKUP, got: " + code.getCellFormula());
            }

            // Editability of the de-materialized paste area now rides on unlocked column defaults.
            for (int visibleCol : VISIBLE_BOUNDARY_COLS) {
                CellStyle colDefault = boundary.getColumnStyle(visibleCol);
                assertNotNull(colDefault, "Visible dropdown column must carry a default column style");
                assertTrue(!colDefault.getLocked(),
                        "Visible dropdown column " + visibleCol + " default style must be UNLOCKED");
            }

            // Dropdown validations still span the full paste cap on every visible boundary column.
            for (int visibleCol : VISIBLE_BOUNDARY_COLS) {
                CellRangeAddress range = findValidationRangeForColumn(boundary, visibleCol);
                assertNotNull(range, "Boundary dropdown validation must exist on col " + visibleCol);
                assertTrue(range.getFirstRow() <= 2 && range.getLastRow() >= EXCEL_ROW_LIMIT,
                        "Boundary validation on col " + visibleCol + " must span rows 2.."
                                + EXCEL_ROW_LIMIT + ", got " + range.getFirstRow() + ".." + range.getLastRow());
            }

            // --- Measure: reproduce the OLD materialize-every-visible-cell behavior for BEFORE ---
            long cellsAfter = countCells(wb.getSheet(BOUNDARY_SHEET)) + countCells(plain);
            long bytesAfter = afterBytes.length;
            long[] before = measureOldMaterializedBehavior(afterBytes);
            long cellsBefore = before[0];
            long bytesBefore = before[1];

            System.out.println("=== BOUNDARY TEMPLATE SKELETON SIZE (MEASURED) ===");
            System.out.println("excelRowLimit=" + EXCEL_ROW_LIMIT + ", dataRows=" + DATA_ROWS
                    + ", boundaryLevels=" + LEVELS);
            System.out.println("BEFORE (materialized visible dropdown cells): cells=" + cellsBefore
                    + ", bytes=" + bytesBefore + " (" + (bytesBefore / 1024) + " KB)");
            System.out.println("AFTER  (unlocked column defaults):            cells=" + cellsAfter
                    + ", bytes=" + bytesAfter + " (" + (bytesAfter / 1024) + " KB)");
            System.out.println("Kept per-row formula cells (helpers + boundary code): "
                    + ((long) (HELPER_COLS.size() + 1) * (EXCEL_ROW_LIMIT - 1)));
            System.out.println("==================================================");

            // Every skeleton row sheds its visible dropdown cells (LEVELS per row).
            assertTrue(cellsBefore - cellsAfter >= (long) (EXCEL_ROW_LIMIT - 1 - DATA_ROWS) * LEVELS,
                    "Cell reduction must cover the de-materialized visible dropdown cells");
            assertTrue(bytesAfter < bytesBefore, "AFTER bytes must be smaller than BEFORE");
        }
    }

    /**
     * Reproduces the OLD behavior on a copy of the generated workbook: one unlocked-styled cell per
     * VISIBLE dropdown column per skeleton row. Used only to MEASURE the before-state.
     */
    private long[] measureOldMaterializedBehavior(byte[] generatedBytes) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(generatedBytes))) {
            Sheet boundary = wb.getSheet(BOUNDARY_SHEET);
            CellStyle unlocked = wb.createCellStyle();
            unlocked.setLocked(false);
            for (int r = 2; r <= EXCEL_ROW_LIMIT; r++) {
                Row row = boundary.getRow(r);
                if (row == null) row = boundary.createRow(r);
                for (int colIdx : VISIBLE_BOUNDARY_COLS) {
                    row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellStyle(unlocked);
                }
            }
            long cells = countCells(boundary) + countCells(wb.getSheet(PLAIN_SHEET));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new long[]{cells, bos.size()};
        }
    }

    /** Direct generator mirroring FacilitySheetGenerator: boundary columns first, then schema data. */
    static class BoundaryDirectGenerator implements ISheetGenerator {
        private final HierarchicalBoundaryUtil hierarchicalBoundaryUtil;
        private final ExcelDataPopulator populator;
        private final List<ColumnDef> columns;
        private final List<Map<String, Object>> data;

        BoundaryDirectGenerator(HierarchicalBoundaryUtil hierarchicalBoundaryUtil, ExcelDataPopulator populator,
                                List<ColumnDef> columns, List<Map<String, Object>> data) {
            this.hierarchicalBoundaryUtil = hierarchicalBoundaryUtil;
            this.populator = populator;
            this.columns = columns;
            this.data = data;
        }

        @Override
        public XSSFWorkbook generateSheet(XSSFWorkbook workbook, String sheetName, SheetGenerationConfig config,
                                          GenerateResource generateResource, RequestInfo requestInfo,
                                          Map<String, String> localizationMap) {
            workbook.createSheet(sheetName);
            hierarchicalBoundaryUtil.addHierarchicalBoundaryColumnWithData(workbook, sheetName, localizationMap,
                    Collections.singletonList(Boundary.builder().code("ROOT").name("Root").type("Level1").build()),
                    generateResource.getHierarchyType(), generateResource.getTenantId(), requestInfo, null);
            return (XSSFWorkbook) populator.populateSheetWithData(workbook, sheetName, columns, data,
                    localizationMap, generateResource.isUnprotectedJoinMode());
        }
    }

    private void stubBoundaryData() {
        lenient().when(boundaryService.fetchBoundaryRelationship(any(), any(), any()))
                .thenReturn(new BoundarySearchResponse());
        BoundaryHierarchy hierarchy = new BoundaryHierarchy();
        List<BoundaryHierarchyChild> children = new ArrayList<>();
        for (int i = 1; i <= LEVELS; i++) {
            BoundaryHierarchyChild child = new BoundaryHierarchyChild();
            child.setBoundaryType("Level" + i);
            children.add(child);
        }
        hierarchy.setBoundaryHierarchy(children);
        BoundaryHierarchyResponse hierarchyResponse = new BoundaryHierarchyResponse();
        hierarchyResponse.setBoundaryHierarchy(Collections.singletonList(hierarchy));
        lenient().when(boundaryService.fetchBoundaryHierarchy(any(), any(), any())).thenReturn(hierarchyResponse);
        lenient().when(boundaryUtil.buildCodeToBoundaryMap(any())).thenReturn(new HashMap<>());
        BoundaryUtil.BoundaryRowData rowData = new BoundaryUtil.BoundaryRowData(
                Arrays.asList("ROOT", "B1", "B2"), "B2");
        lenient().when(boundaryUtil.processBoundariesWithEnrichment(any(), any(), any()))
                .thenReturn(Collections.singletonList(rowData));
    }

    private ProcessorGenerationConfig processorConfig() {
        return ProcessorGenerationConfig.builder()
                .applyWorkbookProtection(false)
                .sheets(new ArrayList<>(Arrays.asList(
                        SheetGenerationConfig.builder()
                                .sheetName(PLAIN_SHEET)
                                .schemaName("test-schema")
                                .order(1)
                                .visible(true)
                                .build(),
                        SheetGenerationConfig.builder()
                                .sheetName(BOUNDARY_SHEET)
                                .generationClass(BoundaryDirectGenerator.class.getName())
                                .isGenerationClassViaExcelPopulator(false)
                                .order(2)
                                .visible(true)
                                .build())))
                .build();
    }

    private GenerateResource generateResource() {
        return GenerateResource.builder()
                .id("gen-1")
                .tenantId("mz")
                .type("facility")
                .hierarchyType("HIERARCHY1")
                .referenceId("ref-1")
                .build();
    }

    private static long countCells(Sheet sheet) {
        long count = 0;
        for (Row row : sheet) {
            count += row.getPhysicalNumberOfCells();
        }
        return count;
    }

    private static CellRangeAddress findValidationRangeForColumn(Sheet sheet, int colIndex) {
        for (DataValidation dv : sheet.getDataValidations()) {
            for (CellRangeAddress addr : dv.getRegions().getCellRangeAddresses()) {
                if (addr.getFirstColumn() <= colIndex && colIndex <= addr.getLastColumn()) {
                    return addr;
                }
            }
        }
        return null;
    }

    private static int indexOfColumn(String name) {
        List<ColumnDef> columns = sampleColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (name.equals(columns.get(i).getName())) {
                // Plain sheet has no boundary columns, so schema columns start at 0.
                return i;
            }
        }
        return -1;
    }

    private static List<ColumnDef> sampleColumns() {
        return Arrays.asList(
                ColumnDef.builder().name("name").type("string").width(30).build(),
                ColumnDef.builder().name("status").type("string").width(20)
                        .enumValues(Arrays.asList("ACTIVE", "INACTIVE")).build());
    }

    private static List<Map<String, Object>> sampleData() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < DATA_ROWS; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("name", "Row " + i);
            r.put("status", "ACTIVE");
            rows.add(r);
        }
        return rows;
    }
}
