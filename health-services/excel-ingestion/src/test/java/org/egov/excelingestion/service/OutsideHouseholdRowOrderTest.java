package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pre-filled Outside Household rows keep the order the planner gave them in an earlier file. */
class OutsideHouseholdRowOrderTest {

    static final String[] KEYS = {"HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID",
            "HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_TYPE", "HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_NAME",
            "HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_STATUS", "HCM_ADMIN_CONSOLE____ROW_ID"};
    static final String OHP = "Outside Household Places", TP = "Transit Posts";

    private static OutsideHouseholdRowOrder.Site site(String id, String name, String type, String boundary) {
        return new OutsideHouseholdRowOrder.Site(id, name, type, boundary);
    }

    @Test
    void sitesTakeTheirPlaceInTheEarlierFileAndTheOthersComeAfter() {
        // As filled: a carried transit post, a typed school (no id yet, no boundary code), a carried market
        List<OutsideHouseholdRowOrder.Site> filled = List.of(site("tp-1", "Bus Stop", TP, "Z"), site(null, "School", OHP, null),
                site("ohp-1", "Market", OHP, "Z"));
        // As listed by the generator: places first, then posts; the typed school now has an id; a new carried site
        List<OutsideHouseholdRowOrder.Site> listed = List.of(site("ohp-1", "Market", OHP, "Z"), site("ohp-9", "New Place", OHP, "Z"),
                site("ohp-2", "School", OHP, "Z"), site("tp-1", "Bus Stop", TP, "Z"));

        assertEquals(List.of(3, 2, 0, 1), OutsideHouseholdRowOrder.order(filled, listed));
    }

    @Test
    void aNameSeveralEarlierRowsHaveIsNotUsedAndATypeLabelInAnotherLanguageStillMatchesByName() {
        List<OutsideHouseholdRowOrder.Site> filled = List.of(site(null, "Clinic", OHP, null), site(null, "Clinic", TP, null),
                site(null, "Ferry", TP, null));
        List<OutsideHouseholdRowOrder.Site> listed = List.of(site("a", "Clinic", "Lieux hors domicile", "Z"), site("b", "Ferry", "Postes de transit", "Z"));

        // "Clinic" is on two earlier rows of other types: no match; "Ferry" is on one: matched by name
        assertEquals(List.of(1, 0), OutsideHouseholdRowOrder.order(filled, listed));
    }

    @Test
    void anOrderAlreadyAsFilledIsTheIdentity() {
        List<OutsideHouseholdRowOrder.Site> filled = List.of(site("a", "A", OHP, "Z"), site("b", "B", TP, "Z"));
        assertEquals(List.of(0, 1), OutsideHouseholdRowOrder.order(filled, filled));
    }

    @Test
    void aFormulaFollowsItsRowAndKeepsItsOtherReferencesAndTexts() {
        String status = "IF($I$11=\"\",\"\",IF(OR($I$11=\"Outside Household Places\",$I$11=\"OUTSIDE_HOUSEHOLD_PLACES\"),\"Permanent\",\"Temporary\"))";
        assertEquals(status.replace("$I$11", "$I$4"), OutsideHouseholdRowOrder.withRow(status, 11, 4));
        // ROW11 is a cell (column ROW); LOG10( is a function
        assertEquals("SUM(A4:B4)+C12+$D$3+ROW4+LOG10(11)", OutsideHouseholdRowOrder.withRow("SUM(A11:B11)+C12+$D$3+ROW11+LOG10(11)", 11, 4));
    }

    @Test
    void rowsMoveWholeWithTheirValuesLocksRowIdsAndOwnRowFormulas() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = CloneSourceDataMergerTest.sheet(wb, "Outside Household", KEYS,
                    new Object[]{"Z", "ohp-1", OHP, "Market", "Permanent", "rid-1"},
                    new Object[]{"Z", "tp-1", TP, "Bus Stop", "Temporary", "rid-2"},
                    new Object[]{"Z", null, OHP, "School", null, "rid-3"});
            CellStyle locked = wb.createCellStyle();
            locked.setLocked(true);
            CellStyle open = wb.createCellStyle();
            open.setLocked(false);
            for (int r = 2; r <= 3; r++) {
                sheet.getRow(r).getCell(3).setCellStyle(locked);
            }
            sheet.getRow(4).createCell(3).setCellValue("School");
            sheet.getRow(4).getCell(3).setCellStyle(open);
            sheet.getRow(4).createCell(4).setCellFormula("IF($C$5=\"\",\"\",\"Permanent\")");
            sheet.createRow(5).createCell(4).setCellFormula("IF($C$6=\"\",\"\",\"Permanent\")");   // an empty template row

            List<OutsideHouseholdRowOrder.Site> filled = List.of(site(null, "School", OHP, null), site("tp-1", "Bus Stop", TP, "Z"),
                    site("ohp-1", "Market", OHP, "Z"));

            assertEquals(2, OutsideHouseholdRowOrder.orderLike(filled, wb), "the market and the school swap; the bus stop stays");

            assertEquals(List.of("School", "Bus Stop", "Market"), names(sheet));
            assertEquals("rid-3", sheet.getRow(2).getCell(5).getStringCellValue());
            assertEquals("rid-1", sheet.getRow(4).getCell(5).getStringCellValue());
            assertFalse(sheet.getRow(2).getCell(3).getCellStyle().getLocked(), "the typed row stays editable");
            assertTrue(sheet.getRow(4).getCell(3).getCellStyle().getLocked(), "a pre-filled row stays locked");
            assertEquals("IF($C$3=\"\",\"\",\"Permanent\")", sheet.getRow(2).getCell(4).getCellFormula());
            assertEquals("ohp-1", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals("IF($C$6=\"\",\"\",\"Permanent\")", sheet.getRow(5).getCell(4).getCellFormula(), "an empty row is left alone");
            assertEquals(0, OutsideHouseholdRowOrder.orderLike(filled, wb), "already in order");
        }
    }

    @Test
    void sitesAreReadFromTheOutsideHouseholdSheetWithoutLoadingTheWorkbook() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            CloneSourceDataMergerTest.sheet(wb, "User List", new String[]{"HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"}, new Object[]{9000000001.0});
            CloneSourceDataMergerTest.sheet(wb, "_h_Helper_h_", KEYS, new Object[]{"X", "x-1", OHP, "Hidden"});
            CloneSourceDataMergerTest.sheet(wb, "Lieux hors domicile", KEYS,
                    new Object[]{"Z", "tp-1", TP, "Bus Stop"}, new Object[]{}, new Object[]{null, null, OHP, "School"});
            List<OutsideHouseholdRowOrder.Site> sites = OutsideHouseholdRowOrder.readSites(bytes(wb));
            assertEquals(List.of(site("tp-1", "Bus Stop", TP, "Z"), site(null, "School", OHP, null)), sites);
        }
        try (Workbook noSheet = new XSSFWorkbook()) {
            CloneSourceDataMergerTest.sheet(noSheet, "User List", new String[]{"HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"}, new Object[]{9000000001.0});
            assertEquals(List.of(), OutsideHouseholdRowOrder.readSites(bytes(noSheet)));
        }
    }

    static List<String> names(Sheet sheet) {
        List<String> names = new ArrayList<>();
        for (int r = 2; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && row.getCell(3) != null && !row.getCell(3).getStringCellValue().isEmpty()) {
                names.add(row.getCell(3).getStringCellValue());
            }
        }
        return names;
    }

    static byte[] bytes(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
