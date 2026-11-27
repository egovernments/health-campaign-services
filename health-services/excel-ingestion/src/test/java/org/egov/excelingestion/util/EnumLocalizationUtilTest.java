package org.egov.excelingestion.util;

import org.egov.excelingestion.config.ProcessingConstants;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the enum dropdown localization round-trip: values are shown in the sheet's locale, and
 * map back to the canonical MDMS values the processors and project-factory match on.
 */
class EnumLocalizationUtilTest {

    private static final String STATUS_COL = ProcessingConstants.FACILITY_STATUS_COLUMN_KEY;
    private static final List<String> STATUS_VALUES =
            List.of(ProcessingConstants.STATUS_TEMPORARY, ProcessingConstants.STATUS_PERMANENT);

    /** Localization map with French labels for the facility-status enum. */
    private static Map<String, String> frenchStatusMap() {
        Map<String, String> map = new HashMap<>();
        map.put(STATUS_COL + "_TEMPORARY", "Temporaire");
        map.put(STATUS_COL + "_PERMANENT", "Permanent(e)");
        return map;
    }

    @Test
    void buildsKeyFromColumnAndValue() {
        assertEquals("HCM_ADMIN_CONSOLE_FACILITY_STATUS_PERMANENT",
                EnumLocalizationUtil.buildKey(STATUS_COL, "Permanent"));
        // Non-alphanumerics collapse to underscores so the key stays a safe identifier
        assertEquals("COL_NOT_APPLICABLE", EnumLocalizationUtil.buildKey("COL", "Not Applicable"));
    }

    @Test
    void dropdownValuesAreLocalizedForDisplay() {
        List<String> localized = EnumLocalizationUtil.toLocalizedValues(
                STATUS_COL, STATUS_VALUES, frenchStatusMap());

        assertEquals(List.of("Temporaire", "Permanent(e)"), localized);
    }

    @Test
    void localizedValuesMapBackToCanonical() {
        Map<String, String> reverse = EnumLocalizationUtil.buildReverseMap(
                STATUS_COL, STATUS_VALUES, frenchStatusMap());

        assertEquals("Permanent", EnumLocalizationUtil.toCanonical("Permanent(e)", reverse));
        assertEquals("Temporary", EnumLocalizationUtil.toCanonical("Temporaire", reverse));
    }

    @Test
    void reverseMapAcceptsCanonicalAndDifferentCasing() {
        Map<String, String> reverse = EnumLocalizationUtil.buildReverseMap(
                STATUS_COL, STATUS_VALUES, frenchStatusMap());

        // A pasted English value (or a pre-localization file) still normalizes
        assertEquals("Permanent", EnumLocalizationUtil.toCanonical("Permanent", reverse));
        // Retyped with different casing / stray whitespace
        assertEquals("Temporary", EnumLocalizationUtil.toCanonical("  temporaire  ", reverse));
    }

    @Test
    void unknownValueIsNotMappedSoValidatorsStillRejectIt() {
        Map<String, String> reverse = EnumLocalizationUtil.buildReverseMap(
                STATUS_COL, STATUS_VALUES, frenchStatusMap());

        assertNull(EnumLocalizationUtil.toCanonical("Nonsense", reverse));
        assertNull(EnumLocalizationUtil.toCanonical("", reverse));
        assertNull(EnumLocalizationUtil.toCanonical(null, reverse));
    }

    @Test
    void missingLocalizationFallsBackToCanonicalValues() {
        // No entries at all: sheets must render exactly as they do today
        List<String> localized = EnumLocalizationUtil.toLocalizedValues(
                STATUS_COL, STATUS_VALUES, new HashMap<>());
        assertEquals(STATUS_VALUES, localized);

        // and there is nothing to reverse, so the normalizer skips the column
        assertTrue(EnumLocalizationUtil.buildReverseMap(STATUS_COL, STATUS_VALUES, new HashMap<>()).isEmpty());
    }

    @Test
    void partialLocalizationLeavesUntranslatedValueCanonical() {
        Map<String, String> partial = new HashMap<>();
        partial.put(STATUS_COL + "_PERMANENT", "Permanent(e)");

        assertEquals(List.of("Temporary", "Permanent(e)"),
                EnumLocalizationUtil.toLocalizedValues(STATUS_COL, STATUS_VALUES, partial));

        Map<String, String> reverse = EnumLocalizationUtil.buildReverseMap(STATUS_COL, STATUS_VALUES, partial);
        assertEquals("Permanent", EnumLocalizationUtil.toCanonical("Permanent(e)", reverse));
        assertEquals("Temporary", EnumLocalizationUtil.toCanonical("Temporary", reverse));
    }

    @Test
    void blankLocalizationEntryFallsBackInsteadOfEmptyingTheDropdown() {
        Map<String, String> blank = new HashMap<>();
        blank.put(STATUS_COL + "_PERMANENT", "   ");

        assertEquals(STATUS_VALUES, EnumLocalizationUtil.toLocalizedValues(STATUS_COL, STATUS_VALUES, blank));
    }

    @Test
    void ambiguousLocalizationFallsBackToCanonicalRatherThanCorruptingData() {
        // Both values translate to the same label: the reverse mapping would be a coin flip, so the
        // column must fall back to canonical English instead of mis-persisting one of them.
        Map<String, String> ambiguous = new HashMap<>();
        ambiguous.put(STATUS_COL + "_TEMPORARY", "Fixe");
        ambiguous.put(STATUS_COL + "_PERMANENT", "Fixe");

        assertEquals(STATUS_VALUES, EnumLocalizationUtil.toLocalizedValues(STATUS_COL, STATUS_VALUES, ambiguous));
        assertTrue(EnumLocalizationUtil.buildReverseMap(STATUS_COL, STATUS_VALUES, ambiguous).isEmpty());
    }

    @Test
    void usageEnumRoundTripsForBothUserAndFacilityColumns() {
        // Same labels, different columns: each is keyed per column so a tenant can translate them
        // independently, and each reverses to the canonical value its processor compares against.
        List<String> usageValues = List.of("Active", "Inactive");
        Map<String, String> map = new HashMap<>();
        map.put(ProcessingConstants.USER_USAGE_COLUMN_KEY + "_ACTIVE", "Actif");
        map.put(ProcessingConstants.USER_USAGE_COLUMN_KEY + "_INACTIVE", "Inactif");
        map.put(ProcessingConstants.FACILITY_USAGE_COLUMN_KEY + "_ACTIVE", "Ouvert");
        map.put(ProcessingConstants.FACILITY_USAGE_COLUMN_KEY + "_INACTIVE", "Ferme");

        Map<String, String> userReverse = EnumLocalizationUtil.buildReverseMap(
                ProcessingConstants.USER_USAGE_COLUMN_KEY, usageValues, map);
        Map<String, String> facilityReverse = EnumLocalizationUtil.buildReverseMap(
                ProcessingConstants.FACILITY_USAGE_COLUMN_KEY, usageValues, map);

        assertEquals("Active", EnumLocalizationUtil.toCanonical("Actif", userReverse));
        assertEquals("Active", EnumLocalizationUtil.toCanonical("Ouvert", facilityReverse));
        assertEquals("Inactive", EnumLocalizationUtil.toCanonical("Ferme", facilityReverse));
        // A facility label must not resolve through the user column's map
        assertNull(EnumLocalizationUtil.toCanonical("Ouvert", userReverse));
    }
}
