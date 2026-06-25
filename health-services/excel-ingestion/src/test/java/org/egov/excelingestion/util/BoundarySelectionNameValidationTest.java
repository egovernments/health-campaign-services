package org.egov.excelingestion.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests the server-side boundary SELECTION-NAME guard ({@code BoundaryUtil#validateBoundarySelectionNames}).
 *
 * <p>{@code HierarchyRelation.boundary} is {@code final = null} (Lombok therefore omits it from the builder),
 * so a relationship tree cannot be assembled through the model in a unit test. We instead spy {@link BoundaryUtil}
 * and stub {@code getHierarchyBoundaryDisplayNames} with a controlled valid-name set, then exercise the
 * column-matching / zero-width-space normalization / error-emission logic that is the point of the guard.</p>
 */
@ExtendWith(MockitoExtension.class)
class BoundarySelectionNameValidationTest {

    @Mock
    private CampaignService campaignService;
    @Mock
    private BoundaryService boundaryService;

    private BoundaryUtil spy;
    private RequestInfo requestInfo;
    private Map<String, String> localizationMap;

    private static final String HIERARCHY = "TEST2";
    private static final Set<String> VALID_NAMES =
            new HashSet<>(Arrays.asList("Country X", "Province 1", "District 1", "Village 1"));

    @BeforeEach
    void setUp() {
        spy = spy(new BoundaryUtil(campaignService, boundaryService));
        requestInfo = new RequestInfo();
        localizationMap = new HashMap<>();
        // Deterministically stub the hierarchy-name source (a Mockito spy intercepts this internal self-call).
        lenient().doReturn(VALID_NAMES).when(spy).getHierarchyBoundaryDisplayNames(any(), any(), any(), any());
    }

    private Map<String, Object> row(int rowNumber, Object... keyValues) {
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("__actualRowNumber__", rowNumber);
        for (int i = 0; i < keyValues.length; i += 2) {
            rowData.put((String) keyValues[i], keyValues[i + 1]);
        }
        return rowData;
    }

    @Test
    void validSelections_produceNoErrors() {
        List<Map<String, Object>> data = Collections.singletonList(
                row(3, "TEST2_COUNTRY", "Country X", "TEST2_PROVINCE", "Province 1",
                        "TEST2_DISTRICT", "District 1", "TEST2_VILLAGE", "Village 1"));
        List<ValidationError> errors = new ArrayList<>();

        spy.validateBoundarySelectionNames(data, "dev", HIERARCHY, requestInfo, localizationMap, errors);

        assertTrue(errors.isEmpty(), "legitimate dropdown selections must not be flagged");
    }

    @Test
    void offHierarchyName_isFlagged() {
        List<Map<String, Object>> data = Collections.singletonList(
                row(5, "TEST2_PROVINCE", "Province 7")); // hand-typed, off-dropdown value
        List<ValidationError> errors = new ArrayList<>();

        spy.validateBoundarySelectionNames(data, "dev", HIERARCHY, requestInfo, localizationMap, errors);

        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(Integer.valueOf(5), error.getRowNumber());
        assertEquals("TEST2_PROVINCE", error.getColumnName());
        assertEquals("invalid", error.getStatus());
        assertTrue(error.getErrorDetails().contains("Province 7"),
                "message should surface the offending value for the user");
    }

    @Test
    void zeroWidthSpaceSuffix_isNormalizedAndAccepted() {
        // Generation appends U+200B to disambiguate duplicate sibling names; the persisted cell carries it.
        // After normalization it must still match the base name "Village 1".
        List<Map<String, Object>> data = Collections.singletonList(
                row(3, "TEST2_VILLAGE", "Village 1​​"));
        List<ValidationError> errors = new ArrayList<>();

        spy.validateBoundarySelectionNames(data, "dev", HIERARCHY, requestInfo, localizationMap, errors);

        assertTrue(errors.isEmpty(), "zero-width-space disambiguated names must be accepted");
    }

    @Test
    void helperCodeAndNonBoundaryColumns_areSkipped() {
        List<Map<String, Object>> data = Collections.singletonList(
                row(3,
                        "TEST2_PROVINCE_HELPER", "garbage-helper",          // _HELPER companion -> skipped
                        "HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "TEST2_ANYCODE",  // computed code column -> skipped
                        "HCM_ADMIN_CONSOLE_USER_NAME", "Province 7"));       // non-boundary column -> skipped
        List<ValidationError> errors = new ArrayList<>();

        spy.validateBoundarySelectionNames(data, "dev", HIERARCHY, requestInfo, localizationMap, errors);

        assertTrue(errors.isEmpty(), "helper / code / non-boundary columns must not be name-validated");
    }

    @Test
    void emptySelections_areSkipped() {
        List<Map<String, Object>> data = Collections.singletonList(
                row(3, "TEST2_PROVINCE", "", "TEST2_DISTRICT", null));
        List<ValidationError> errors = new ArrayList<>();

        spy.validateBoundarySelectionNames(data, "dev", HIERARCHY, requestInfo, localizationMap, errors);

        assertTrue(errors.isEmpty(), "blank selections are skipped here (required-ness is enforced elsewhere)");
    }

    @Test
    void failsOpen_whenHierarchyResolvesNoNames() {
        doReturn(Collections.emptySet()).when(spy).getHierarchyBoundaryDisplayNames(any(), any(), any(), any());
        List<Map<String, Object>> data = Collections.singletonList(
                row(3, "TEST2_PROVINCE", "Province 7"));
        List<ValidationError> errors = new ArrayList<>();

        spy.validateBoundarySelectionNames(data, "dev", HIERARCHY, requestInfo, localizationMap, errors);

        assertTrue(errors.isEmpty(), "must fail open (never mass-flag) when no hierarchy names are resolved");
    }

    @Test
    void multipleBadSelectionsAcrossRows_areEachFlagged() {
        List<Map<String, Object>> data = Arrays.asList(
                row(3, "TEST2_PROVINCE", "Province 1", "TEST2_VILLAGE", "Village 99"), // one bad
                row(4, "TEST2_PROVINCE", "Province 7"));                                // one bad
        List<ValidationError> errors = new ArrayList<>();

        spy.validateBoundarySelectionNames(data, "dev", HIERARCHY, requestInfo, localizationMap, errors);

        assertEquals(2, errors.size());
        Set<String> flaggedColumns = new HashSet<>();
        for (ValidationError e : errors) {
            flaggedColumns.add(e.getRowNumber() + ":" + e.getColumnName());
        }
        assertTrue(flaggedColumns.contains("3:TEST2_VILLAGE"));
        assertTrue(flaggedColumns.contains("4:TEST2_PROVINCE"));
    }
}
