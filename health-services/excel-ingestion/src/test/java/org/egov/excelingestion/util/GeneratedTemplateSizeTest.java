package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * Focused proof for the generation-source OOM/size fix.
 *
 * <p>Runs the REAL sheet-generation stack ({@link ExcelDataPopulator} + {@link CellProtectionManager}
 * + {@link ExcelStyleHelper}) with a small number of data rows and a protected sheet, then asserts the
 * produced workbook is small (KB, not MB) and does NOT pre-materialize ~excelRowLimit empty styled
 * rows, while still applying dropdown validation over the FULL intended paste range so pasted data is
 * validated, and keeping header + used-area styling.
 *
 * <p>The BEFORE numbers are computed by reproducing the OLD materialize-every-row behavior on a
 * throwaway workbook so the size reduction is measured, not merely estimated.
 */
@ExtendWith(MockitoExtension.class)
class GeneratedTemplateSizeTest {

    private static final int EXCEL_ROW_LIMIT = 5000;
    private static final int DATA_ROWS = 5;
    private static final String SHEET_NAME = "SizeSheet";
    private static final String ARTIFACT_DIR = "/tmp/hcm-excel";
    private static final String ARTIFACT_PATH = ARTIFACT_DIR + "/sample_generated.xlsx";

    @Mock
    private ExcelIngestionConfig config;

    private ExcelStyleHelper styleHelper;
    private CellProtectionManager protectionManager;
    private ExcelDataPopulator populator;

    @BeforeEach
    void setUp() {
        lenient().when(config.getExcelRowLimit()).thenReturn(EXCEL_ROW_LIMIT);
        // Non-empty password => the sheet is actually protected. This proves the paste area stays
        // editable via default column styles (not via materialized unlocked rows).
        lenient().when(config.getExcelSheetPassword()).thenReturn("pw");
        lenient().when(config.getValidationErrorColor()).thenReturn("#ff0000");

        styleHelper = new ExcelStyleHelper();
        protectionManager = new CellProtectionManager(config, styleHelper);
        populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
    }

    @Test
    void generatedTemplateStaysSmallAndValidatesFullPasteRange() throws IOException {
        List<ColumnDef> columns = sampleColumns();
        List<Map<String, Object>> data = sampleData();
        Map<String, String> loc = new HashMap<>();

        // --- AFTER: the real, patched generation path ---
        Workbook after = populator.populateSheetWithData(SHEET_NAME, columns, data, loc);
        Sheet afterSheet = after.getSheet(SHEET_NAME);

        // 1) Physical rows ~ header(2) + data(DATA_ROWS), NOT ~excelRowLimit.
        int physicalRowsAfter = afterSheet.getPhysicalNumberOfRows();
        assertTrue(physicalRowsAfter <= 2 + DATA_ROWS + 1,
                "AFTER should materialize only header+data rows, got " + physicalRowsAfter);
        assertTrue(physicalRowsAfter < 100,
                "AFTER must NOT pre-materialize thousands of rows, got " + physicalRowsAfter);

        // 2) Cell count over the whole sheet.
        long cellsAfter = countCells(afterSheet);

        // 3) Dropdown validation exists and its sqref spans the FULL intended paste range
        //    (row 3 .. excelRowLimit+1) so pasted data IS validated.
        int enumColIndex = indexOfColumn(columns, "status");
        CellRangeAddress dropdownRange = findValidationRangeForColumn(afterSheet, enumColIndex);
        assertNotNull(dropdownRange,
                "A dropdown/validation must exist over the 'status' column");
        assertTrue(dropdownRange.getFirstRow() <= 2,
                "Validation must start at the first data row (row index 2), got " + dropdownRange.getFirstRow());
        assertTrue(dropdownRange.getLastRow() >= EXCEL_ROW_LIMIT,
                "Validation must extend down to the high cap so the whole paste area is validated; "
                        + "lastRow=" + dropdownRange.getLastRow() + " expected >= " + EXCEL_ROW_LIMIT);

        // 4) Serialize and measure size; assert small.
        byte[] afterBytes = toBytes(after);
        long bytesAfter = afterBytes.length;
        writeArtifact(afterBytes);
        assertTrue(bytesAfter < 200 * 1024,
                "AFTER serialized workbook must be < 200KB, got " + bytesAfter + " bytes");

        // 5) Header styling present + used-area (row 2) data styling present.
        Row header = afterSheet.getRow(1);
        assertNotNull(header, "Visible header row must exist");
        CellStyle headerStyle = header.getCell(0).getCellStyle();
        assertNotNull(headerStyle, "Header cell must carry a style");
        assertTrue(headerStyle.getIndex() > 0, "Header cell must use a non-default style");

        Row firstData = afterSheet.getRow(2);
        assertNotNull(firstData, "First data row must exist");
        assertNotNull(firstData.getCell(0).getCellStyle(), "Used-area data cell must carry a style");

        // 6) Empty paste-area protection is carried by default column styles (zero materialized rows),
        //    so an editable (unlocked) column default exists and the sheet is protected.
        assertTrue(afterSheet.getProtect(), "Sheet must be protected (password configured)");
        CellStyle nameColDefault = afterSheet.getColumnStyle(indexOfColumn(columns, "name"));
        assertNotNull(nameColDefault, "Editable data column must have a default column style for empty rows");
        assertTrue(!nameColDefault.getLocked(),
                "Editable 'name' column's empty paste area must be UNLOCKED via default column style");

        // --- BEFORE: reproduce the old materialize-every-row-to-limit behavior for measurement ---
        long[] before = measureOldMaterializedBehavior(columns, data);
        long cellsBefore = before[0];
        long bytesBefore = before[1];

        // Report measured numbers.
        System.out.println("=== GENERATED TEMPLATE SIZE (MEASURED) ===");
        System.out.println("excelRowLimit=" + EXCEL_ROW_LIMIT + ", dataRows=" + DATA_ROWS
                + ", dataColumns=" + columns.size());
        System.out.println("BEFORE (materialize every row): physicalRows~" + (EXCEL_ROW_LIMIT + 1)
                + ", cells=" + cellsBefore + ", bytes=" + bytesBefore
                + " (" + (bytesBefore / 1024) + " KB)");
        System.out.println("AFTER  (default column styles): physicalRows=" + physicalRowsAfter
                + ", cells=" + cellsAfter + ", bytes=" + bytesAfter
                + " (" + (bytesAfter / 1024) + " KB)");
        System.out.println("Cell reduction: " + cellsBefore + " -> " + cellsAfter
                + " (" + pct(cellsBefore, cellsAfter) + "% fewer)");
        System.out.println("Byte reduction: " + bytesBefore + " -> " + bytesAfter
                + " (" + pct(bytesBefore, bytesAfter) + "% smaller)");
        System.out.println("Artifact written: " + ARTIFACT_PATH);
        System.out.println("==========================================");

        assertTrue(cellsAfter < cellsBefore / 10,
                "AFTER cell count must be an order of magnitude smaller than BEFORE");
        assertTrue(bytesAfter < bytesBefore,
                "AFTER bytes must be smaller than BEFORE");

        after.close();
    }

    /**
     * Reproduces the OLD behavior: materialize every row from 2..excelRowLimit with a per-cell style,
     * on a fresh workbook seeded with the same header+data. Used only to MEASURE the before-state; it
     * does not touch production code.
     */
    private long[] measureOldMaterializedBehavior(List<ColumnDef> columns,
                                                  List<Map<String, Object>> data) throws IOException {
        // Build the same header+data sheet via the real populator, then force-materialize the empty
        // paste area exactly as the old CellProtectionManager loop did.
        Workbook wb = populator.populateSheetWithData(SHEET_NAME, columns, data, new HashMap<>());
        Sheet sheet = wb.getSheet(SHEET_NAME);
        CellStyle unlocked = styleHelper.createUnlockedCellStyle(wb);
        Row visibleRow = sheet.getRow(1);
        int totalCols = visibleRow != null ? visibleRow.getLastCellNum() : columns.size();
        int startCol = Math.max(0, totalCols - columns.size());
        for (int rowIdx = 2; rowIdx <= EXCEL_ROW_LIMIT; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                row = sheet.createRow(rowIdx);
            }
            for (int i = 0; i < columns.size(); i++) {
                int colIdx = startCol + i;
                Cell cell = row.getCell(colIdx);
                if (cell == null) {
                    cell = row.createCell(colIdx);
                }
                cell.setCellStyle(unlocked);
            }
        }
        long cells = countCells(sheet);
        byte[] bytes = toBytes(wb);
        wb.close();
        return new long[]{cells, bytes.length};
    }

    private static long pct(long before, long after) {
        if (before == 0) return 0;
        return Math.round((before - after) * 100.0 / before);
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

    private static int indexOfColumn(List<ColumnDef> columns, String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (name.equals(columns.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }

    private byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        return bos.toByteArray();
    }

    private void writeArtifact(byte[] bytes) throws IOException {
        File dir = new File(ARTIFACT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(ARTIFACT_PATH)) {
            fos.write(bytes);
        }
    }

    private List<ColumnDef> sampleColumns() {
        // A representative mix: an editable text column, a frozen (immutable) column, an
        // enum dropdown column, and a number column.
        return Arrays.asList(
                ColumnDef.builder().name("name").type("string").width(30).build(),
                ColumnDef.builder().name("code").type("string").width(20).freezeColumn(true).build(),
                ColumnDef.builder().name("status").type("string").width(20)
                        .enumValues(Arrays.asList("ACTIVE", "INACTIVE", "PENDING")).build(),
                ColumnDef.builder().name("count").type("number").width(15).build());
    }

    private List<Map<String, Object>> sampleData() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < DATA_ROWS; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("name", "Facility " + i);
            r.put("code", "C-" + i);
            r.put("status", "ACTIVE");
            r.put("count", i);
            rows.add(r);
        }
        return rows;
    }
}
