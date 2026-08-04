package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.ProcessResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BoundaryCodeResolver}: on upload, blank boundary-code cells of user-entered rows
 * are resolved from the workbook's own lookup-sheet mapping (display path -> code), mirroring the
 * semantics of the old per-row VLOOKUP formula. Non-blank codes (prefilled rows, legacy files) are
 * never touched; unresolvable paths stay blank for the boundary-selection validation to flag.
 */
class BoundaryCodeResolverTest {

    private static final String SHEET = "Facilities List";
    private static final String CODE = ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY;
    private static final String L1 = "HIER_COUNTRY";
    private static final String L2 = "HIER_PROVINCE";

    private BoundaryCodeResolver resolver;
    private XSSFWorkbook workbook;
    private ProcessResource resource;

    @BeforeEach
    void setUp() {
        resolver = new BoundaryCodeResolver(new ExcelUtil());
        workbook = new XSSFWorkbook();

        // Lookup sheet Section 2: display-path key (col D) -> code (col E)
        Sheet lookup = workbook.createSheet(HierarchicalBoundaryUtil.LOOKUP_SHEET_NAME);
        addMapping(lookup, 0, "Mozambique", "MZ");
        addMapping(lookup, 1, "Mozambique#Maryland", "MZ_MD");

        // Data sheet: hidden header keys (row 0), label row (row 1), data from row 2
        Sheet sheet = workbook.createSheet(SHEET);
        Row hidden = sheet.createRow(0);
        hidden.createCell(0).setCellValue(L1);
        hidden.createCell(1).setCellValue(L2);
        hidden.createCell(2).setCellValue(CODE);
        hidden.createCell(3).setCellValue("HCM_ADMIN_CONSOLE_FACILITY_NAME");
        sheet.createRow(1);

        addDataRow(sheet, 2, "Mozambique", "Maryland", null);   // new row -> resolve to MZ_MD
        addDataRow(sheet, 3, "Mozambique", null, null);         // partial path -> resolve to MZ
        addDataRow(sheet, 4, "Mozambique", "Bogus", null);      // out-of-list -> stays blank
        addDataRow(sheet, 5, null, null, null);                 // no selection -> untouched
        addDataRow(sheet, 6, "Mozambique", "Maryland", "PRE");  // prefilled code -> untouched

        resource = ProcessResource.builder()
                .tenantId("dev").type("unified-console-validation").hierarchyType("hier")
                .fileStoreId("fs-1").referenceId("ref-1").build();
    }

    @AfterEach
    void tearDown() throws Exception {
        workbook.close();
    }

    private void addMapping(Sheet lookup, int rowIdx, String key, String code) {
        Row row = lookup.getRow(rowIdx) != null ? lookup.getRow(rowIdx) : lookup.createRow(rowIdx);
        row.createCell(HierarchicalBoundaryUtil.CODE_MAPPING_KEY_COLUMN).setCellValue(key);
        row.createCell(HierarchicalBoundaryUtil.CODE_MAPPING_CODE_COLUMN).setCellValue(code);
    }

    private void addDataRow(Sheet sheet, int rowIdx, String l1, String l2, String code) {
        Row row = sheet.createRow(rowIdx);
        if (l1 != null) row.createCell(0).setCellValue(l1);
        if (l2 != null) row.createCell(1).setCellValue(l2);
        if (code != null) row.createCell(2).setCellValue(code);
        row.createCell(3).setCellValue("Facility " + rowIdx); // some data so the parser keeps the row
    }

    private List<Map<String, Object>> parsedRows() {
        return new ExcelUtil().convertSheetToMapListCached("fs-1", SHEET, workbook.getSheet(SHEET));
    }

    @Test
    void resolvesBlankCodes_fromWorkbookLookupMapping() {
        resolver.resolveBlankBoundaryCodes(workbook, resource);

        Sheet sheet = workbook.getSheet(SHEET);
        assertEquals("MZ_MD", sheet.getRow(2).getCell(2).getStringCellValue(),
                "full path must resolve to the deepest code");
        assertEquals("MZ", sheet.getRow(3).getCell(2).getStringCellValue(),
                "partial path must resolve to the deepest selected level's code");
        assertNull(sheet.getRow(4).getCell(2),
                "out-of-dropdown path must stay blank (validators flag it)");
        assertNull(sheet.getRow(5).getCell(2), "row without any selection must stay untouched");
        assertEquals("PRE", sheet.getRow(6).getCell(2).getStringCellValue(),
                "existing (prefilled/legacy) code must never be overwritten");
    }

    @Test
    void mutatesTheSharedParsedRowMaps() {
        resolver.resolveBlankBoundaryCodes(workbook, resource);
        List<Map<String, Object>> rows = parsedRows();
        // rows are keyed by the hidden header names; first data row is Excel row 3 (index 2)
        assertEquals("MZ_MD", ExcelUtil.getValueAsString(rows.get(0).get(CODE)));
        assertEquals("MZ", ExcelUtil.getValueAsString(rows.get(1).get(CODE)));
        assertNull(rows.get(2).get(CODE), "unresolvable path stays blank in the row map too");
    }

    @Test
    void noOp_whenNotJoinModeType_orNoLookupSheet_orNoHierarchy() {
        ProcessResource nonJoin = ProcessResource.builder()
                .tenantId("dev").type("boundary").hierarchyType("hier").fileStoreId("fs-2").build();
        resolver.resolveBlankBoundaryCodes(workbook, nonJoin);
        assertNull(workbook.getSheet(SHEET).getRow(2).getCell(2), "non-join-mode types are skipped");

        ProcessResource noHier = ProcessResource.builder()
                .tenantId("dev").type("unified-console-validation").fileStoreId("fs-3").build();
        resolver.resolveBlankBoundaryCodes(workbook, noHier);
        assertNull(workbook.getSheet(SHEET).getRow(2).getCell(2), "missing hierarchyType is skipped");

        try (XSSFWorkbook noLookup = new XSSFWorkbook()) {
            noLookup.createSheet(SHEET).createRow(0).createCell(0).setCellValue(L1);
            assertDoesNotThrow(() -> resolver.resolveBlankBoundaryCodes(noLookup, resource),
                    "files without the lookup sheet (legacy/non-boundary) must be a clean no-op");
        } catch (Exception e) {
            fail(e);
        }
    }
}
