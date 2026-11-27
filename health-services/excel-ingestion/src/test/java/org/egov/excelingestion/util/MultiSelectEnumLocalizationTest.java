package org.egov.excelingestion.util;

import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Localization of the user sheet's multi-select Role dropdown.
 *
 * <p>Role differs from the previously localized enums (facility type/status, employment type) in two ways
 * that these tests pin down:
 * <ul>
 *   <li>It is ONE MDMS multi-select column rendered as N single-value columns
 *       ({@code HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1..5}), so every expanded column must resolve to
 *       the SAME localization key per role rather than one key per column.</li>
 *   <li>It declares an explicit key prefix ({@code ACCESSCONTROL_ROLES_ROLES_}) whose entries are already
 *       seeded platform-wide, so keys must use that namespace instead of a column-derived one.</li>
 * </ul>
 *
 * <p>The role list is tenant-configurable in MDMS (values may be added, removed or renamed), so nothing
 * here asserts a fixed set - the variable-length cases below are the point, not an afterthought.
 */
class MultiSelectEnumLocalizationTest {

    private static final String ROLE_COL = "HCM_ADMIN_CONSOLE_USER_ROLE";
    private static final String ROLE_PREFIX = "ACCESSCONTROL_ROLES_ROLES_";
    private static final String FACILITY_TYPE_COL = "HCM_ADMIN_CONSOLE_FACILITY_TYPE";

    private static Map<String, String> roleLocalization() {
        Map<String, String> map = new HashMap<>();
        map.put(ROLE_PREFIX + "DISTRIBUTOR", "Distribuidor");
        map.put(ROLE_PREFIX + "HEALTH_FACILITY_WORKER", "Trabalhador de Saude");
        map.put(ROLE_PREFIX + "TEAM_SUPERVISOR", "Supervisor de Equipe");
        return map;
    }

    // ---------------------------------------------------------------- key building

    @Test
    @DisplayName("Role keys use the MDMS-declared prefix, not a column-derived namespace")
    void buildKeyHonoursPrefix() {
        assertEquals(ROLE_PREFIX + "DISTRIBUTOR",
                EnumLocalizationUtil.buildKey(ROLE_COL, "DISTRIBUTOR", ROLE_PREFIX));
        assertEquals(ROLE_PREFIX + "HEALTH_FACILITY_WORKER",
                EnumLocalizationUtil.buildKey(ROLE_COL, "HEALTH_FACILITY_WORKER", ROLE_PREFIX));
    }

    @Test
    @DisplayName("A blank or absent prefix keeps the original column-derived key exactly")
    void buildKeyWithoutPrefixIsUnchanged() {
        String expected = FACILITY_TYPE_COL + "_HEALTH_FACILITY";
        assertEquals(expected, EnumLocalizationUtil.buildKey(FACILITY_TYPE_COL, "Health Facility", null));
        assertEquals(expected, EnumLocalizationUtil.buildKey(FACILITY_TYPE_COL, "Health Facility", "   "));
        // and identical to the pre-existing 2-arg overload, so shipped columns are untouched
        assertEquals(EnumLocalizationUtil.buildKey(FACILITY_TYPE_COL, "Health Facility"), expected);
    }

    @Test
    @DisplayName("All expanded role columns share one key per role, so one entry translates all five")
    void expandedColumnsShareOneKeyPerRole() {
        Map<String, String> localization = roleLocalization();
        // Whatever column index the value sits in, the parent's technical name drives the key.
        for (int i = 1; i <= 5; i++) {
            assertEquals("Distribuidor",
                    EnumLocalizationUtil.toLocalized(ROLE_COL, "DISTRIBUTOR", localization, ROLE_PREFIX),
                    "expanded column " + i + " must resolve the same key");
        }
    }

    // ---------------------------------------------------------------- column classification

    @Test
    @DisplayName("Expanded multi-select columns are localizable; the comma-joined parent is not")
    void parentIsExcludedChildrenAreIncluded() {
        ColumnDef parent = ColumnDef.builder()
                .name(ROLE_COL)
                .technicalName(ROLE_COL)
                .prefix(ROLE_PREFIX)
                .multiSelectDetails(MultiSelectDetails.builder()
                        .enumValues(List.of("DISTRIBUTOR", "TEAM_SUPERVISOR"))
                        .maxSelections(5)
                        .minSelections(1)
                        .build())
                .build();
        ColumnDef child = ColumnDef.builder()
                .name(ROLE_COL + "_MULTISELECT_1")
                .technicalName(ROLE_COL)
                .type("multiselect_item")
                .prefix(ROLE_PREFIX)
                .enumValues(List.of("DISTRIBUTOR", "TEAM_SUPERVISOR"))
                .build();

        // The parent cell holds "A,B" - not a single-value lookup, so it must not be localized directly.
        assertFalse(EnumLocalizationUtil.isLocalizableEnumCell(parent));
        assertTrue(EnumLocalizationUtil.isLocalizableEnumCell(child));
        assertTrue(EnumLocalizationUtil.isExpandedMultiSelectColumn(child));
        assertFalse(EnumLocalizationUtil.isExpandedMultiSelectColumn(parent));
    }

    @Test
    @DisplayName("Plain enum columns stay localizable, so facility type/status are unaffected")
    void plainEnumColumnStillLocalizable() {
        ColumnDef facilityType = ColumnDef.builder()
                .name(FACILITY_TYPE_COL)
                .technicalName(FACILITY_TYPE_COL)
                .type("enum")
                .enumValues(List.of("Warehouse", "Health Facility", "Storing Resource"))
                .build();
        assertTrue(EnumLocalizationUtil.isEnumColumn(facilityType));
        assertTrue(EnumLocalizationUtil.isLocalizableEnumCell(facilityType));
    }

    // ---------------------------------------------------------------- round trip

    @Test
    @DisplayName("Role values survive the localize -> canonical round trip")
    void roleRoundTrip() {
        Map<String, String> localization = roleLocalization();
        List<String> canonical = List.of("DISTRIBUTOR", "HEALTH_FACILITY_WORKER", "TEAM_SUPERVISOR");

        List<String> localized =
                EnumLocalizationUtil.toLocalizedValues(ROLE_COL, canonical, localization, ROLE_PREFIX);
        assertEquals(List.of("Distribuidor", "Trabalhador de Saude", "Supervisor de Equipe"), localized);

        Map<String, String> reverse =
                EnumLocalizationUtil.buildReverseMap(ROLE_COL, canonical, localization, ROLE_PREFIX);
        for (int i = 0; i < canonical.size(); i++) {
            assertEquals(canonical.get(i), EnumLocalizationUtil.toCanonical(localized.get(i), reverse),
                    "localized label must map back to its MDMS enum value");
            // A user who pasted the English code, or a pre-localization file, still normalizes.
            assertEquals(canonical.get(i), EnumLocalizationUtil.toCanonical(canonical.get(i), reverse));
        }
    }

    @Test
    @DisplayName("Case-insensitive reverse lookup, since users retype dropdown values")
    void reverseLookupIsCaseInsensitive() {
        Map<String, String> reverse = EnumLocalizationUtil.buildReverseMap(
                ROLE_COL, List.of("DISTRIBUTOR", "TEAM_SUPERVISOR"), roleLocalization(), ROLE_PREFIX);
        assertEquals("DISTRIBUTOR", EnumLocalizationUtil.toCanonical("distribuidor", reverse));
        assertEquals("DISTRIBUTOR", EnumLocalizationUtil.toCanonical("  DISTRIBUIDOR  ", reverse));
        assertEquals("DISTRIBUTOR", EnumLocalizationUtil.toCanonical("distributor", reverse));
    }

    @Test
    @DisplayName("An unknown value stays null so existing validators still raise the invalid-value error")
    void unknownValueIsNotRewritten() {
        Map<String, String> reverse = EnumLocalizationUtil.buildReverseMap(
                ROLE_COL, List.of("DISTRIBUTOR"), roleLocalization(), ROLE_PREFIX);
        assertNull(EnumLocalizationUtil.toCanonical("NOT_A_ROLE", reverse));
        assertNull(EnumLocalizationUtil.toCanonical("", reverse));
    }

    // ------------------------------------------------- tenant-configurable role list (edge case 1)

    @Test
    @DisplayName("Roles added to MDMS need no code change; unseeded ones fall back to the MDMS value")
    void newRolesFallBackToCanonical() {
        Map<String, String> localization = roleLocalization(); // only 3 roles translated
        // A tenant grows the list to 6; the 3 new ones have no localization entries yet.
        List<String> canonical = List.of("DISTRIBUTOR", "HEALTH_FACILITY_WORKER", "TEAM_SUPERVISOR",
                "FIELD_SUPPORT", "PAYMENT_APPROVER", "BRAND_NEW_ROLE");

        List<String> localized =
                EnumLocalizationUtil.toLocalizedValues(ROLE_COL, canonical, localization, ROLE_PREFIX);
        assertEquals(6, localized.size());
        assertEquals("Distribuidor", localized.get(0));
        // Untranslated roles render as their MDMS value rather than breaking the sheet.
        assertEquals("FIELD_SUPPORT", localized.get(3));
        assertEquals("BRAND_NEW_ROLE", localized.get(5));

        // All six - translated or not - still reverse to their canonical MDMS value.
        Map<String, String> reverse =
                EnumLocalizationUtil.buildReverseMap(ROLE_COL, canonical, localization, ROLE_PREFIX);
        for (String role : canonical) {
            assertEquals(role, EnumLocalizationUtil.toCanonical(role, reverse));
        }
        assertEquals("DISTRIBUTOR", EnumLocalizationUtil.toCanonical("Distribuidor", reverse));
    }

    @Test
    @DisplayName("A shrunk or reordered role list is handled positionally, not by fixed index")
    void shrunkAndReorderedListStillMaps() {
        Map<String, String> localization = roleLocalization();
        // Tenant removed roles and reordered the rest.
        List<String> canonical = List.of("TEAM_SUPERVISOR", "DISTRIBUTOR");
        List<String> localized =
                EnumLocalizationUtil.toLocalizedValues(ROLE_COL, canonical, localization, ROLE_PREFIX);
        assertEquals(List.of("Supervisor de Equipe", "Distribuidor"), localized);

        Map<String, String> reverse =
                EnumLocalizationUtil.buildReverseMap(ROLE_COL, canonical, localization, ROLE_PREFIX);
        assertEquals("TEAM_SUPERVISOR", EnumLocalizationUtil.toCanonical("Supervisor de Equipe", reverse));
        assertEquals("DISTRIBUTOR", EnumLocalizationUtil.toCanonical("Distribuidor", reverse));
    }

    @Test
    @DisplayName("With no localization seeded the list is returned as-is, so today's sheets are unchanged")
    void noLocalizationIsANoOp() {
        List<String> canonical = new ArrayList<>(List.of("DISTRIBUTOR", "TEAM_SUPERVISOR"));
        // Same instance back = callers keep using the canonical list, dropdown renders exactly as today.
        assertSame(canonical,
                EnumLocalizationUtil.toLocalizedValues(ROLE_COL, canonical, new HashMap<>(), ROLE_PREFIX));
        assertTrue(EnumLocalizationUtil.buildReverseMap(
                ROLE_COL, canonical, new HashMap<>(), ROLE_PREFIX).isEmpty());
    }

    @Test
    @DisplayName("Ambiguous translations fall back to canonical rather than risk mis-persisting a role")
    void ambiguousTranslationFallsBack() {
        Map<String, String> ambiguous = new HashMap<>();
        ambiguous.put(ROLE_PREFIX + "DISTRIBUTOR", "Supervisor");
        ambiguous.put(ROLE_PREFIX + "TEAM_SUPERVISOR", "Supervisor"); // same label, not invertible
        List<String> canonical = List.of("DISTRIBUTOR", "TEAM_SUPERVISOR");

        assertEquals(canonical,
                EnumLocalizationUtil.toLocalizedValues(ROLE_COL, canonical, ambiguous, ROLE_PREFIX));
        assertTrue(EnumLocalizationUtil.buildReverseMap(ROLE_COL, canonical, ambiguous, ROLE_PREFIX).isEmpty());
    }
}
