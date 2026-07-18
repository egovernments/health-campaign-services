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
    // Boundary block layout on a fresh sheet (scaffold-less design, no helper columns):
    // L1 visible(0), L2 visible(1), L3 visible(2), hidden boundary code(3). Schema columns follow.
    private static final List<Integer> VISIBLE_BOUNDARY_COLS = Arrays.asList(0, 1, 2);
    private static final int BOUNDARY_CODE_COL = 3;

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

            // --- Boundary sheet: NO skeleton rows at all (scaffold-less design) ---
            Sheet boundary = wb.getSheet(BOUNDARY_SHEET);
            assertNotNull(boundary, "Boundary data sheet must exist");
            // No helper columns in the technical header row
            Row techHeader = boundary.getRow(0);
            for (int c = 0; c < techHeader.getLastCellNum(); c++) {
                Cell hc = techHeader.getCell(c);
                if (hc != null && hc.getCellType() == CellType.STRING) {
                    assertTrue(!hc.getStringCellValue().endsWith("_HELPER"),
                            "No helper columns must exist, found: " + hc.getStringCellValue());
                }
            }
            int[] sampleRows = {2 + DATA_ROWS, EXCEL_ROW_LIMIT / 2, EXCEL_ROW_LIMIT};
            for (int r : sampleRows) {
                assertNull(boundary.getRow(r),
                        "Row " + r + " must NOT be materialized (no per-row formula scaffold)");
            }
            // The cascade rides on ONE data-validation formula per level (evaluated on dropdown open)
            for (int level = 1; level < LEVELS; level++) {
                CellRangeAddress range = findValidationRangeForColumn(boundary, VISIBLE_BOUNDARY_COLS.get(level));
                assertNotNull(range, "Cascade validation must exist on level " + (level + 1));
            }
            String level2Formula = findValidationFormulaForColumn(boundary, VISIBLE_BOUNDARY_COLS.get(1));
            assertNotNull(level2Formula, "Level-2 cascade validation formula must exist");
            assertTrue(level2Formula.contains("INDIRECT(") && level2Formula.contains("MATCH(")
                            && level2Formula.contains("\"_hL\"") && level2Formula.contains("A3"),
                    "Cascade formula must MATCH the parent path and INDIRECT the positional child list, got: "
                            + level2Formula);

            // Editability of the de-materialized paste area now rides on unlocked column defaults.
            for (int visibleCol : VISIBLE_BOUNDARY_COLS) {
                CellStyle colDefault = boundary.getColumnStyle(visibleCol);
                assertNotNull(colDefault, "Visible dropdown column must carry a default column style");
                assertTrue(!colDefault.getLocked(),
                        "Visible dropdown column " + visibleCol + " default style must be UNLOCKED");
            }

            // Dropdown validations must cover the FULL paste cap on every visible boundary column.
            // Data starts at row index 2, so covering excelRowLimit rows requires lastRow >= 2 + excelRowLimit - 1
            // (= excelRowLimit + 1). The old code used lastRow = excelRowLimit, leaving the final row of a
            // maximum-size paste unvalidated (off-by-one); this asserts that coverage extends to the last row.
            int requiredLastRow = 2 + EXCEL_ROW_LIMIT - 1;
            for (int visibleCol : VISIBLE_BOUNDARY_COLS) {
                CellRangeAddress range = findValidationRangeForColumn(boundary, visibleCol);
                assertNotNull(range, "Boundary dropdown validation must exist on col " + visibleCol);
                assertTrue(range.getFirstRow() <= 2 && range.getLastRow() >= requiredLastRow,
                        "Boundary validation on col " + visibleCol + " must cover all " + EXCEL_ROW_LIMIT
                                + " paste rows (rows 2.." + requiredLastRow + "), got "
                                + range.getFirstRow() + ".." + range.getLastRow());
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
            System.out.println("Kept per-row formula cells: 0 (scaffold-less cascade design)");
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

    private static String findValidationFormulaForColumn(Sheet sheet, int colIndex) {
        for (DataValidation dv : sheet.getDataValidations()) {
            for (CellRangeAddress addr : dv.getRegions().getCellRangeAddresses()) {
                if (addr.getFirstColumn() <= colIndex && colIndex <= addr.getLastColumn()) {
                    return dv.getValidationConstraint().getFormula1();
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
