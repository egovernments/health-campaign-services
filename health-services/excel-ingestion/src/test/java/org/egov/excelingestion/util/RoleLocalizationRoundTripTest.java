package org.egov.excelingestion.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * End-to-end check of Role dropdown localization on the user sheet.
 *
 * <p>Role is the multi-select case: MDMS declares ONE column
 * ({@code HCM_ADMIN_CONSOLE_USER_ROLE} with {@code multiSelectDetails}), which the sheet renders as five
 * single-value columns ({@code ..._MULTISELECT_1..5}, shown to the user as "User Role 1..5"). The column
 * also declares {@code "prefix": "ACCESSCONTROL_ROLES_ROLES_"}, the localization namespace already seeded
 * platform-wide.
 *
 * <p>The contract these tests protect: the user picks a localized label, but the canonical MDMS code is
 * what reaches validation, persistence and project-factory - the role codes are enums consumed by the
 * mobile app and dashboards, so they must round-trip byte-for-byte.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleLocalizationRoundTripTest {

    private static final String SHEET_NAME = "User List";
    private static final String ROLE_COL = "HCM_ADMIN_CONSOLE_USER_ROLE";
    private static final String ROLE_PREFIX = "ACCESSCONTROL_ROLES_ROLES_";
    private static final String NAME_COL = "HCM_ADMIN_CONSOLE_USER_NAME";
    private static final int MAX_SELECTIONS = 5;

    /** Mirrors the tenant's MDMS role list; deliberately not a constant the production code knows. */
    private static final List<String> ROLES = List.of(
            "DISTRIBUTOR", "HEALTH_FACILITY_WORKER", "WAREHOUSE_MANAGER", "TEAM_SUPERVISOR", "FIELD_SUPPORT");

    @Mock
    private ExcelIngestionConfig config;
    @Mock
    private ExcelStyleHelper excelStyleHelper;
    @Mock
    private CellProtectionManager cellProtectionManager;
    @Mock
    private ExcelUtil excelUtil;
    @Mock
    private org.egov.excelingestion.exception.CustomExceptionHandler exceptionHandler;

    private ExcelDataPopulator populator;
    private EnumValueNormalizer normalizer;
    private Map<String, String> localizationMap;

    @BeforeEach
    void setUp() {
        when(excelStyleHelper.createCustomHeaderStyle(any(), any(), anyBoolean()))
                .thenAnswer(inv -> ((Workbook) inv.getArgument(0)).createCellStyle());
        when(excelStyleHelper.createDataCellStyle(any(), anyBoolean()))
                .thenAnswer(inv -> ((Workbook) inv.getArgument(0)).createCellStyle());
        when(excelUtil.convertSheetToMapListCached(any(), any(), any())).thenReturn(new ArrayList<>());
        // Real config exposes a positive row limit; the dropdown range is sized from it.
        when(config.getExcelRowLimit()).thenReturn(100);

        populator = new ExcelDataPopulator(config, excelStyleHelper, cellProtectionManager);
        normalizer = new EnumValueNormalizer(
                new SchemaColumnDefUtil(new ColumnDefMaker(), exceptionHandler),
                new ObjectMapper(), excelUtil);

        localizationMap = new HashMap<>();
        localizationMap.put(ROLE_COL, "Fonction de l'utilisateur");
        localizationMap.put(ROLE_PREFIX + "DISTRIBUTOR", "Distributeur");
        localizationMap.put(ROLE_PREFIX + "HEALTH_FACILITY_WORKER", "Agent de sante");
        localizationMap.put(ROLE_PREFIX + "WAREHOUSE_MANAGER", "Gestionnaire d'entrepot");
        localizationMap.put(ROLE_PREFIX + "TEAM_SUPERVISOR", "Superviseur d'equipe");
        // FIELD_SUPPORT intentionally left untranslated - tenants seed locales incrementally.
    }

    /** ColumnDefs as the generator builds them: one multi-select parent plus a plain string column. */
    private List<ColumnDef> userColumns() {
        List<ColumnDef> columns = new ArrayList<>();
        columns.add(ColumnDef.builder().name(NAME_COL).type("string").orderNumber(1).width(30).build());
        columns.add(ColumnDef.builder().name(ROLE_COL).orderNumber(2).width(30)
                .prefix(ROLE_PREFIX)
                .multiSelectDetails(MultiSelectDetails.builder()
                        .enumValues(ROLES)
                        .maxSelections(MAX_SELECTIONS)
                        .minSelections(1)
                        .build())
                .build());
        return columns;
    }

    /** MDMS-shaped user schema, matching the real payload's structure. */
    private Map<String, Object> userSchema() {
        Map<String, Object> roleProp = new HashMap<>();
        roleProp.put("name", ROLE_COL);
        roleProp.put("orderNumber", 2);
        roleProp.put("prefix", ROLE_PREFIX);
        roleProp.put("multiSelectDetails", new HashMap<>(Map.of(
                "enum", ROLES, "maxSelections", MAX_SELECTIONS, "minSelections", 1)));

        Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("name", NAME_COL);
        nameProp.put("type", "string");
        nameProp.put("orderNumber", 1);

        Map<String, Object> schema = new HashMap<>();
        schema.put("stringProperties", List.of(nameProp, roleProp));
        return schema;
    }

    // ---------------------------------------------------------------- generation

    @Test
    @DisplayName("Every one of the five role columns gets a localized dropdown")
    void allExpandedRoleColumnsHaveLocalizedDropdown() {
        Workbook workbook = populator.populateSheetWithData(
                SHEET_NAME, userColumns(), List.of(), localizationMap);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        int localizedDropdowns = 0;
        for (DataValidation dv : sheet.getDataValidations()) {
            String[] values = dv.getValidationConstraint().getExplicitListValues();
            if (values == null) {
                continue;
            }
            List<String> list = List.of(values);
            if (list.contains("Distributeur")) {
                localizedDropdowns++;
                assertTrue(list.contains("Agent de sante"));
                assertTrue(list.contains("Superviseur d'equipe"));
                // Untranslated role still offered, as its canonical MDMS value.
                assertTrue(list.contains("FIELD_SUPPORT"),
                        "an unseeded role must still be selectable as its MDMS value");
                assertFalse(list.contains("DISTRIBUTOR"),
                        "the canonical code should be replaced by its label, not shown alongside");
            }
        }
        assertEquals(MAX_SELECTIONS, localizedDropdowns,
                "each of the " + MAX_SELECTIONS + " expanded role columns needs its own localized dropdown");
    }

    @Test
    @DisplayName("Prefilled roles are localized so they match their own dropdown")
    void prefilledRolesAreLocalized() {
        // Existing users arrive with canonical, comma-joined roles on the parent column.
        List<Map<String, Object>> data = List.of(new HashMap<>(Map.of(
                NAME_COL, "Ana Silva",
                ROLE_COL, "DISTRIBUTOR,TEAM_SUPERVISOR")));

        Workbook workbook = populator.populateSheetWithData(SHEET_NAME, userColumns(), data, localizationMap);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        // Split across the expanded columns AND localized - otherwise Excel flags the cell as invalid
        // because the canonical code is absent from the localized dropdown list.
        assertEquals("Distributeur", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_1"));
        assertEquals("Superviseur d'equipe", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_2"));
    }

    @Test
    @DisplayName("An untranslated role is prefilled as its canonical MDMS value")
    void prefilledUntranslatedRoleFallsBack() {
        List<Map<String, Object>> data = List.of(new HashMap<>(Map.of(
                NAME_COL, "Joao", ROLE_COL, "FIELD_SUPPORT")));

        Workbook workbook = populator.populateSheetWithData(SHEET_NAME, userColumns(), data, localizationMap);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        assertEquals("FIELD_SUPPORT", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_1"));
    }

    @Test
    @DisplayName("The key prefix is never concatenated onto a role cell's value")
    void keyPrefixIsNotPrependedToTheValue() {
        // Data can key an expanded column directly (not only the comma-joined parent), which routes
        // through the generic cell writer where `prefix` is otherwise prepended to the value. For a
        // localizable enum the prefix is the localization NAMESPACE, so prepending it as a literal
        // would emit "ACCESSCONTROL_ROLES_ROLESDistributeur".
        List<Map<String, Object>> data = List.of(new HashMap<>(Map.of(
                NAME_COL, "Ana Silva",
                ROLE_COL + "_MULTISELECT_1", "DISTRIBUTOR")));

        Workbook workbook = populator.populateSheetWithData(SHEET_NAME, userColumns(), data, localizationMap);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        assertEquals("Distributeur", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_1"),
                "the role cell must hold the label alone, with no key-prefix concatenation");
    }

    // ---------------------------------------------------------------- upload

    @Test
    @DisplayName("Uploaded localized roles become canonical MDMS codes in every column")
    void uploadNormalizesRolesToCanonical() {
        Workbook workbook = blankSheetWithRoles(
                "Distributeur", "Agent de sante", "Superviseur d'equipe", null, null);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        normalizer.normalizeToCanonical(workbook, resource(), Map.of(SHEET_NAME, userSchema()), localizationMap);

        assertEquals("DISTRIBUTOR", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_1"));
        assertEquals("HEALTH_FACILITY_WORKER", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_2"));
        assertEquals("TEAM_SUPERVISOR", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_3"));
    }

    @Test
    @DisplayName("The comma-joined parent value is rebuilt from canonical children")
    void cachedParentValueIsRebuiltCanonically() {
        Workbook workbook = blankSheetWithRoles("Distributeur", "Superviseur d'equipe", null, null, null);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        // ExcelUtil.convertSheetToMapListCached joins the children into the parent (via
        // reconstructMultiSelectValues) BEFORE this normalizer runs, so the cached parent still holds
        // localized labels. Validation, persistence and project-factory read this cached map.
        List<Map<String, Object>> cachedRows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put(ROLE_COL + "_MULTISELECT_1", "Distributeur");
        row.put(ROLE_COL + "_MULTISELECT_2", "Superviseur d'equipe");
        row.put(ROLE_COL, "Distributeur,Superviseur d'equipe");
        cachedRows.add(row);
        when(excelUtil.convertSheetToMapListCached(any(), eq(SHEET_NAME), any())).thenReturn(cachedRows);

        normalizer.normalizeToCanonical(workbook, resource(), Map.of(SHEET_NAME, userSchema()), localizationMap);

        assertEquals("DISTRIBUTOR", row.get(ROLE_COL + "_MULTISELECT_1"));
        assertEquals("TEAM_SUPERVISOR", row.get(ROLE_COL + "_MULTISELECT_2"));
        assertEquals("DISTRIBUTOR,TEAM_SUPERVISOR", row.get(ROLE_COL),
                "the parent must be canonical - this is the value persisted and sent downstream");
    }

    @Test
    @DisplayName("A parent with no roles is left alone rather than invented")
    void emptyParentIsNotPopulated() {
        Workbook workbook = blankSheetWithRoles(null, null, null, null, null);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        List<Map<String, Object>> cachedRows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put(NAME_COL, "No Roles");
        cachedRows.add(row);
        when(excelUtil.convertSheetToMapListCached(any(), eq(SHEET_NAME), any())).thenReturn(cachedRows);

        normalizer.normalizeToCanonical(workbook, resource(), Map.of(SHEET_NAME, userSchema()), localizationMap);

        assertFalse(row.containsKey(ROLE_COL), "must not fabricate a parent value for a row with no roles");
    }

    @Test
    @DisplayName("Canonical codes typed or pasted by the user pass through unchanged")
    void canonicalInputSurvivesUpload() {
        Workbook workbook = blankSheetWithRoles("DISTRIBUTOR", "FIELD_SUPPORT", null, null, null);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        normalizer.normalizeToCanonical(workbook, resource(), Map.of(SHEET_NAME, userSchema()), localizationMap);

        assertEquals("DISTRIBUTOR", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_1"));
        assertEquals("FIELD_SUPPORT", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_2"));
    }

    @Test
    @DisplayName("An unknown role is left for the existing validators to reject")
    void unknownRoleIsLeftUntouched() {
        Workbook workbook = blankSheetWithRoles("Not A Real Role", null, null, null, null);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        normalizer.normalizeToCanonical(workbook, resource(), Map.of(SHEET_NAME, userSchema()), localizationMap);

        assertEquals("Not A Real Role", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_1"));
    }

    @Test
    @DisplayName("With no role localization seeded, generation and upload behave exactly as today")
    void withoutLocalizationNothingChanges() {
        Map<String, String> headersOnly = Map.of(ROLE_COL, "User Role", NAME_COL, "Name");
        List<Map<String, Object>> data = List.of(new HashMap<>(Map.of(
                NAME_COL, "Ana", ROLE_COL, "DISTRIBUTOR,TEAM_SUPERVISOR")));

        Workbook workbook = populator.populateSheetWithData(SHEET_NAME, userColumns(), data, headersOnly);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        assertEquals("DISTRIBUTOR", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_1"));
        assertEquals("TEAM_SUPERVISOR", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_2"));

        normalizer.normalizeToCanonical(workbook, resource(), Map.of(SHEET_NAME, userSchema()), headersOnly);

        assertEquals("DISTRIBUTOR", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_1"));
        assertEquals("TEAM_SUPERVISOR", cellValue(sheet, 2, ROLE_COL + "_MULTISELECT_2"));
    }

    @Test
    @DisplayName("Roles added to MDMS need no code change")
    void tenantAddedRoleIsHandled() {
        List<String> grown = new ArrayList<>(ROLES);
        grown.add("PAYMENT_APPROVER");
        localizationMap.put(ROLE_PREFIX + "PAYMENT_APPROVER", "Approbateur de paiement");

        List<ColumnDef> columns = new ArrayList<>();
        columns.add(ColumnDef.builder().name(NAME_COL).type("string").orderNumber(1).width(30).build());
        columns.add(ColumnDef.builder().name(ROLE_COL).orderNumber(2).width(30).prefix(ROLE_PREFIX)
                .multiSelectDetails(MultiSelectDetails.builder()
                        .enumValues(grown).maxSelections(MAX_SELECTIONS).minSelections(1).build())
                .build());

        Workbook workbook = populator.populateSheetWithData(SHEET_NAME, columns, List.of(), localizationMap);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        boolean found = false;
        for (DataValidation dv : sheet.getDataValidations()) {
            String[] values = dv.getValidationConstraint().getExplicitListValues();
            if (values != null && List.of(values).contains("Approbateur de paiement")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "a newly added MDMS role must localize with no code change");
    }

    // ---------- helpers ----------

    /** Builds a generated user sheet, then overwrites row 2's role columns with the given values. */
    private Workbook blankSheetWithRoles(String... roleCells) {
        Workbook workbook = populator.populateSheetWithData(
                SHEET_NAME, userColumns(),
                List.of(new HashMap<>(Map.of(NAME_COL, "Ana Silva"))),
                localizationMap);
        Sheet sheet = workbook.getSheet(SHEET_NAME);
        for (int i = 0; i < roleCells.length; i++) {
            if (roleCells[i] == null) {
                continue;
            }
            int colIdx = columnIndex(sheet, ROLE_COL + "_MULTISELECT_" + (i + 1));
            if (colIdx < 0) {
                continue;
            }
            Row row = sheet.getRow(2);
            Cell cell = row.getCell(colIdx);
            if (cell == null) {
                cell = row.createCell(colIdx);
            }
            cell.setCellValue(roleCells[i]);
        }
        return workbook;
    }

    private ProcessResource resource() {
        return ProcessResource.builder().tenantId("mz").fileStoreId("fs-role-1")
                .type("unified-console").build();
    }

    private int columnIndex(Sheet sheet, String technicalName) {
        Row technicalRow = sheet.getRow(0);
        for (int i = 0; i < technicalRow.getLastCellNum(); i++) {
            Cell cell = technicalRow.getCell(i);
            if (cell != null && technicalName.equals(cell.getStringCellValue())) {
                return i;
            }
        }
        return -1;
    }

    private String cellValue(Sheet sheet, int rowIdx, String technicalName) {
        int colIdx = columnIndex(sheet, technicalName);
        if (colIdx < 0) {
            return null;
        }
        Cell cell = sheet.getRow(rowIdx).getCell(colIdx);
        return cell == null ? null : cell.getStringCellValue();
    }
}
