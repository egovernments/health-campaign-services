package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.web.models.excel.ColumnDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translates MDMS enum dropdown values (e.g. Permanent/Temporary, Active/Inactive) for DISPLAY in the
 * sheet's generation locale, and translates them back to their canonical MDMS values on upload.
 *
 * <p><b>Why display-only.</b> The canonical English enum value is structural, not cosmetic: the
 * upload-side processors compare it case-sensitively (see {@code UserValidationProcessor} /
 * {@code FacilityValidationProcessor}), it is persisted verbatim into {@code eg_cm_sheet_data_temp.rowJson},
 * and project-factory maps it by exact key ({@code createAndSearch.ts} turns "Permanent" into
 * {@code isPermanent=true} / {@code employeeType=PERMANENT}). Writing a localized value all the way
 * through would silently break every one of those. So the localized string exists only in the cell the
 * user sees, and {@link #toCanonical} reverses it before any validation, persistence or downstream
 * hand-off runs.
 *
 * <p><b>Key convention.</b> {@code <COLUMN_TECHNICAL_NAME>_<VALUE_UPPER_SNAKE>}, e.g.
 * {@code HCM_ADMIN_CONSOLE_FACILITY_STATUS_PERMANENT}. Per-column rather than a flat shared key so a
 * tenant can translate "Active" differently for users and facilities if it needs to.
 *
 * <p><b>Fallback.</b> A missing or blank localization entry falls back to the canonical value itself.
 * That is what keeps this change safe to ship before the localization data is seeded: with no entries,
 * every sheet renders exactly as it does today and the reverse map is an identity.
 */
@Slf4j
public final class EnumLocalizationUtil {

    /** ColumnDef type assigned to each column a multi-select is expanded into. */
    private static final String MULTISELECT_ITEM_TYPE = "multiselect_item";

    private EnumLocalizationUtil() {
        // Utility class
    }

    /**
     * Builds the localization key for one enum value of one column.
     * Non-alphanumeric characters in the value become underscores so the key stays a safe identifier.
     */
    public static String buildKey(String columnName, String canonicalValue) {
        return buildKey(columnName, canonicalValue, null);
    }

    /**
     * Builds the localization key for one enum value, honouring an explicit key prefix when the MDMS
     * column declares one.
     *
     * <p>Some columns carry their own established localization namespace rather than deriving one from
     * the column name. The user sheet's role column declares {@code "prefix": "ACCESSCONTROL_ROLES_ROLES_"},
     * whose entries are already seeded platform-wide, so a role must resolve to
     * {@code ACCESSCONTROL_ROLES_ROLES_DISTRIBUTOR} and NOT to a column-derived key. Using the prefix also
     * keeps the five expanded role columns ({@code ..._USER_ROLE_MULTISELECT_1..5}) on one key per role
     * instead of five duplicates.
     *
     * <p>With a blank/null prefix this is exactly the original column-derived behaviour, so every
     * already-shipped enum column (facility type/status/usage, employment type) is unaffected.
     */
    public static String buildKey(String columnName, String canonicalValue, String keyPrefix) {
        String normalizedValue = canonicalValue.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        if (keyPrefix != null && !keyPrefix.trim().isEmpty()) {
            return keyPrefix.trim() + normalizedValue;
        }
        return columnName + "_" + normalizedValue;
    }

    /**
     * Localized label to show for a canonical enum value, or the canonical value itself when no
     * usable translation exists.
     */
    public static String toLocalized(String columnName, String canonicalValue, Map<String, String> localizationMap) {
        return toLocalized(columnName, canonicalValue, localizationMap, null);
    }

    /** As {@link #toLocalized(String, String, Map)}, honouring the column's explicit key prefix. */
    public static String toLocalized(String columnName, String canonicalValue, Map<String, String> localizationMap,
                                     String keyPrefix) {
        if (canonicalValue == null || canonicalValue.trim().isEmpty() || localizationMap == null) {
            return canonicalValue;
        }
        String localized = localizationMap.get(buildKey(columnName, canonicalValue, keyPrefix));
        return (localized != null && !localized.trim().isEmpty()) ? localized.trim() : canonicalValue;
    }

    /**
     * The display-ordered localized values for an enum column, used to build the dropdown list.
     *
     * <p>Returns the canonical list unchanged when nothing is translated, so callers can keep using it
     * directly. When two values translate to the same label the translation is dropped for that column
     * and the canonical list is returned: a non-invertible list would make {@link #toCanonical}
     * ambiguous, and silently guessing one of the two would corrupt data at upload. Better to show
     * English for that column than to mis-persist.
     */
    public static List<String> toLocalizedValues(String columnName, List<String> canonicalValues,
                                                 Map<String, String> localizationMap) {
        return toLocalizedValues(columnName, canonicalValues, localizationMap, null);
    }

    /** As {@link #toLocalizedValues(String, List, Map)}, honouring the column's explicit key prefix. */
    public static List<String> toLocalizedValues(String columnName, List<String> canonicalValues,
                                                 Map<String, String> localizationMap, String keyPrefix) {
        if (canonicalValues == null || canonicalValues.isEmpty() || localizationMap == null) {
            return canonicalValues;
        }
        List<String> localizedValues = new ArrayList<>(canonicalValues.size());
        Map<String, String> seen = new HashMap<>();
        boolean anyTranslated = false;

        for (String canonical : canonicalValues) {
            String localized = toLocalized(columnName, canonical, localizationMap, keyPrefix);
            String previous = seen.putIfAbsent(localized, canonical);
            if (previous != null && !previous.equals(canonical)) {
                log.warn("Enum localization for column '{}' is ambiguous: '{}' and '{}' both localize to "
                                + "'{}'. Falling back to canonical values for this column so the upload-side "
                                + "reverse mapping stays unambiguous.",
                        columnName, previous, canonical, localized);
                return canonicalValues;
            }
            if (!localized.equals(canonical)) {
                anyTranslated = true;
            }
            localizedValues.add(localized);
        }
        return anyTranslated ? localizedValues : canonicalValues;
    }

    /**
     * Reverse lookup map (localized label -> canonical value) for one enum column.
     *
     * <p>Keyed on the trimmed lower-cased label so a user who retypes a dropdown value with different
     * casing still resolves. Canonical values are mapped to themselves, so a file generated before
     * localization existed - or one where the user pasted the English value - still normalizes cleanly.
     * Returns an empty map when the column has no usable translation, letting callers skip the column.
     */
    public static Map<String, String> buildReverseMap(String columnName, List<String> canonicalValues,
                                                      Map<String, String> localizationMap) {
        return buildReverseMap(columnName, canonicalValues, localizationMap, null);
    }

    /** As {@link #buildReverseMap(String, List, Map)}, honouring the column's explicit key prefix. */
    public static Map<String, String> buildReverseMap(String columnName, List<String> canonicalValues,
                                                      Map<String, String> localizationMap, String keyPrefix) {
        List<String> localizedValues = toLocalizedValues(columnName, canonicalValues, localizationMap, keyPrefix);
        if (localizedValues == null || localizedValues == canonicalValues) {
            return Map.of(); // nothing translated (or ambiguous): cells already hold canonical values
        }
        Map<String, String> reverse = new LinkedHashMap<>();
        for (int i = 0; i < canonicalValues.size(); i++) {
            String canonical = canonicalValues.get(i);
            reverse.put(normalizeLookupKey(canonical), canonical);
            reverse.put(normalizeLookupKey(localizedValues.get(i)), canonical);
        }
        return reverse;
    }

    /**
     * Canonical value for a (possibly localized) cell value, or null when it does not match any known
     * value. Null means "leave the cell alone" - an out-of-list value must reach the existing
     * server-side validators so the user still gets the proper invalid-value error.
     */
    public static String toCanonical(String cellValue, Map<String, String> reverseMap) {
        if (cellValue == null || reverseMap == null || reverseMap.isEmpty()) {
            return null;
        }
        String trimmed = cellValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return reverseMap.get(normalizeLookupKey(trimmed));
    }

    private static String normalizeLookupKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * True when the column carries a single-value enum dropdown whose cell holds exactly one enum value.
     *
     * <p>Covers both plain enum columns (facility type/status/usage, employment type) and the individual
     * columns a multi-select is expanded into ({@code ..._USER_ROLE_MULTISELECT_1..5}, built by
     * {@code ExcelDataPopulator.expandMultiSelectColumns}). Each expanded column is populated with the
     * parent's enum list and holds a single selection, so it localizes and reverses exactly like a plain
     * enum column.
     *
     * <p>The multi-select PARENT is deliberately excluded: its cell holds a comma-joined string of several
     * values, so it is not a single-value lookup and is rebuilt from the already-canonical children on
     * upload (see {@code EnumValueNormalizer}).
     */
    public static boolean isEnumColumn(ColumnDef column) {
        return column != null
                && column.getEnumValues() != null
                && !column.getEnumValues().isEmpty()
                && column.getMultiSelectDetails() == null;
    }

    /** True when the column is one of the single-value columns a multi-select was expanded into. */
    public static boolean isExpandedMultiSelectColumn(ColumnDef column) {
        return column != null
                && MULTISELECT_ITEM_TYPE.equals(column.getType())
                && column.getEnumValues() != null
                && !column.getEnumValues().isEmpty();
    }

    /**
     * True when the column holds a single localizable enum value - either a plain enum column or one
     * expanded multi-select column.
     */
    public static boolean isLocalizableEnumCell(ColumnDef column) {
        return isEnumColumn(column) || isExpandedMultiSelectColumn(column);
    }
}
