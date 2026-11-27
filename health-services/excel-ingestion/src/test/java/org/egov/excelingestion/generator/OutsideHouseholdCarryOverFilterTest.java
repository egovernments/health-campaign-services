package org.egov.excelingestion.generator;

import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Bug 3: carried sites whose locked values the upload would reject are not put into the template. */
class OutsideHouseholdCarryOverFilterTest {

    private static final List<ColumnDef> COLUMNS = List.of(
            ColumnDef.builder().name(ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY).minLength(2).maxLength(200).build());
    private static final Map<String, List<String>> CATEGORIES = Map.of(
            "OUTSIDE_HOUSEHOLD_PLACES", List.of("SCHOOL", "MARKET"), "TRANSIT_POST", List.of("BUS_STATION", "CHECKPOINT"),
            "NO_LIST_TYPE", List.of());

    private static Map<String, Object> site(String id, String type, String category, String name) {
        Map<String, Object> row = new HashMap<>();
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY, id);
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY, type);
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, category);
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY, name);
        return row;
    }

    private static List<Object> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> r.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY)).toList();
    }

    @Test
    void dropsSitesTheUploadWouldRejectAndKeepsTheRest() {
        List<Map<String, Object>> carried = List.of(
                site("ok", "OUTSIDE_HOUSEHOLD_PLACES", "SCHOOL", "Zabi School"),
                site("long-name", "OUTSIDE_HOUSEHOLD_PLACES", "SCHOOL", "N".repeat(302)),
                site("one-letter", "OUTSIDE_HOUSEHOLD_PLACES", "SCHOOL", "N"),
                site("not-a-category", "OUTSIDE_HOUSEHOLD_PLACES", "NOT_A_CATEGORY", "Bad Category Site"),
                site("transit-school", "TRANSIT_POST", "SCHOOL", "Transit With School"),
                site("blank-category", "TRANSIT_POST", null, "Old Site Without Category"),
                site("blank-name", "TRANSIT_POST", "CHECKPOINT", null),
                site("type-without-list", "NO_LIST_TYPE", "ANYTHING", "Free Category Site"),
                site("exactly-200", "OUTSIDE_HOUSEHOLD_PLACES", "market", "M".repeat(200)));

        List<Map<String, Object>> kept = OutsideHouseholdSheetGenerator.withoutRowsTheUploadWouldReject(carried, COLUMNS, CATEGORIES);

        assertEquals(List.of("ok", "blank-category", "blank-name", "type-without-list", "exactly-200"), ids(kept));
    }

    @Test
    void withoutLimitsOrCategoriesNothingIsDropped() {
        List<Map<String, Object>> carried = List.of(site("long", "OUTSIDE_HOUSEHOLD_PLACES", "X", "N".repeat(302)));

        assertEquals(List.of("long"), ids(OutsideHouseholdSheetGenerator.withoutRowsTheUploadWouldReject(carried, List.of(), Map.of())));
    }
}
