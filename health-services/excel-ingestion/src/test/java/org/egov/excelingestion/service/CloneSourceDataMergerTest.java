package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A clone that adds or drops Outside Household gets its source's rows in its newly generated template. */
class CloneSourceDataMergerTest {

    private static final String ROW_ID = CloneSourceDataMerger.ROW_ID;

    @Test
    void sourceUsersAreAddedWithoutTheirRowIdsOrPasswords() {
        Workbook source = new XSSFWorkbook(), template = new XSSFWorkbook();
        sheet(source, "User List", new String[]{"HCM_ADMIN_CONSOLE_USER_NAME", "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", "Password", ROW_ID},
                new Object[]{"Asha", 9677111138.0, "secret", "src-row-1"});
        sheet(template, "User List", new String[]{"HCM_ADMIN_CONSOLE_USER_NAME", "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", "Password"});

        assertEquals(1, CloneSourceDataMerger.copyRows(source, template));

        Row user = template.getSheet("User List").getRow(2);
        assertEquals("Asha", user.getCell(0).getStringCellValue());
        assertEquals(9677111138.0, user.getCell(1).getNumericCellValue());
        assertNull(user.getCell(2));
    }

    @Test
    void targetsGoIntoTheTemplatesBoundaryRowsWhichKeepTheirOwnRowIds() {
        Workbook source = new XSSFWorkbook(), template = new XSSFWorkbook();
        String[] cols = {"HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_TARGET_POLIO_AGE", ROW_ID};
        sheet(source, "Boundary List", cols, new Object[]{"V1", 500.0, "src-row"}, new Object[]{"V2", 300.0, "src-row-2"});
        sheet(template, "Boundary List", cols, new Object[]{"V2", null, "tpl-row-2"}, new Object[]{"V1", null, "tpl-row-1"});

        CloneSourceDataMerger.copyRows(source, template);

        Sheet boundaries = template.getSheet("Boundary List");
        assertEquals(300.0, boundaries.getRow(2).getCell(1).getNumericCellValue());
        assertEquals("tpl-row-2", boundaries.getRow(2).getCell(2).getStringCellValue());
        assertEquals(500.0, boundaries.getRow(3).getCell(1).getNumericCellValue());
        assertEquals("tpl-row-1", boundaries.getRow(3).getCell(2).getStringCellValue());
        assertNull(boundaries.getRow(4));
    }

    @Test
    void facilitiesAreMatchedByCodeOrNameAndNewOnesAreAdded() {
        Workbook source = new XSSFWorkbook(), template = new XSSFWorkbook();
        String[] cols = {"HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_FACILITY_CODE", "HCM_ADMIN_CONSOLE_FACILITY_NAME",
                "HCM_ADMIN_CONSOLE_FACILITY_USAGE", ROW_ID};
        sheet(source, "Facilities List", cols, new Object[]{"V1", "F-1", "Warehouse A", "Active", "s1"},
                new Object[]{"V1", null, "New Store", "Active", null});
        sheet(template, "Facilities List", cols, new Object[]{"V1", "F-1", "Warehouse A", "Inactive", "t1"},
                new Object[]{"V2", "F-2", "Warehouse B", "Inactive", "t2"});

        assertEquals(2, CloneSourceDataMerger.copyRows(source, template));

        Sheet facilities = template.getSheet("Facilities List");
        assertEquals("Active", facilities.getRow(2).getCell(3).getStringCellValue());
        assertEquals("t1", facilities.getRow(2).getCell(4).getStringCellValue());
        assertEquals("Inactive", facilities.getRow(3).getCell(3).getStringCellValue());
        assertEquals("New Store", facilities.getRow(4).getCell(2).getStringCellValue());
        assertEquals("Active", facilities.getRow(4).getCell(3).getStringCellValue());
    }

    @Test
    void theOutsideHouseholdSheetStaysTheClonesOwnAndABlankSourceCellKeepsTheTemplatesValue() {
        Workbook source = new XSSFWorkbook(), template = new XSSFWorkbook();
        String[] oh = {"HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_NAME"};
        sheet(source, "Outside Household", oh, new Object[]{"V1", "Source School"});
        sheet(template, "Outside Household", oh, new Object[]{"V1", "Carried Market"});
        String[] cols = {"HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_TARGET_POLIO_AGE"};
        sheet(source, "Boundary List", cols, new Object[]{"V1", null});
        sheet(template, "Boundary List", cols, new Object[]{"V1", 42.0});

        CloneSourceDataMerger.copyRows(source, template);

        assertEquals("Carried Market", template.getSheet("Outside Household").getRow(2).getCell(1).getStringCellValue());
        assertNull(template.getSheet("Outside Household").getRow(3));
        assertEquals(42.0, template.getSheet("Boundary List").getRow(2).getCell(1).getNumericCellValue());
    }

    /** A sheet with column keys in row 0, a label row 1 and data rows from row 2 (null = no cell). */
    static Sheet sheet(Workbook wb, String name, String[] keys, Object[]... rows) {
        Sheet sheet = wb.createSheet(name);
        Row header = sheet.createRow(0), labels = sheet.createRow(1);
        for (int c = 0; c < keys.length; c++) {
            header.createCell(c).setCellValue(keys[c]);
            labels.createCell(c).setCellValue(keys[c].toLowerCase());
        }
        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r + 2);
            for (int c = 0; c < rows[r].length; c++) {
                Object v = rows[r][c];
                if (v instanceof String s) {
                    row.createCell(c).setCellValue(s);
                } else if (v instanceof Double d) {
                    row.createCell(c).setCellValue(d);
                }
            }
        }
        return sheet;
    }
}
