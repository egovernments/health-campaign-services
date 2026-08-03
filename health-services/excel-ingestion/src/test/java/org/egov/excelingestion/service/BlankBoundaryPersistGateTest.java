package org.egov.excelingestion.service;

import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the fail-closed persist gate that stops an active user row being stored with no resolved boundary
 * code (demo incident 2026-07-30: blanks were persisted while the run reported success, project-factory
 * built a jurisdiction with null boundary/boundaryType, and HRMS rejected all 19,998 user creates).
 *
 * <p>The gate is private and deliberately sits at the persistence choke point, so it is exercised here
 * reflectively - calling it via handlePostProcessing would require MDMS/Kafka collaborators and would test
 * those rather than the rule.
 */
class BlankBoundaryPersistGateTest {

    private static final String SHEET = "User List";
    private static final String PARSE_TYPE = ProcessingConstants.UNIFIED_CONSOLE_TYPE + "-parse";

    private ConfigBasedProcessingService service;
    private Method gate;
    private ProcessResource resource;

    @BeforeEach
    void setUp() throws Exception {
        service = new ConfigBasedProcessingService(null, new CustomExceptionHandler(), null, null, null, null, null);
        gate = ConfigBasedProcessingService.class.getDeclaredMethod(
                "assertBoundaryCodesResolved", String.class, List.class, ProcessResource.class);
        gate.setAccessible(true);
        resource = new ProcessResource();
        resource.setId("res-1");
        resource.setType(PARSE_TYPE);
    }

    private void invoke(String sheetName, List<Map<String, Object>> rows) throws Exception {
        try {
            gate.invoke(service, sheetName, rows, resource);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    private Map<String, Object> row(Object rowNumber, String usage, String code) {
        Map<String, Object> data = new HashMap<>();
        if (rowNumber != null) {
            data.put(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY, rowNumber);
        }
        if (usage != null) {
            data.put(ProcessingConstants.USER_USAGE_COLUMN_KEY, usage);
        }
        if (code != null) {
            data.put(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, code);
        }
        return data;
    }

    // --- the incident ------------------------------------------------------------------------

    @Test
    void rejectsActiveRowWithNoBoundaryCode() throws Exception {
        List<Map<String, Object>> rows = List.of(row(3, ValidationConstants.USAGE_ACTIVE, null));

        CustomException ex = assertThrows(CustomException.class, () -> invoke(SHEET, rows));

        assertEquals(ErrorConstantsRef.CODE, ex.getCode());
        assertTrue(ex.getMessage().contains(SHEET), "message names the sheet");
        assertTrue(ex.getMessage().contains("3"), "message names the offending row");
    }

    @Test
    void rejectsWhitespaceOnlyBoundaryCode() {
        List<Map<String, Object>> rows = List.of(row(3, ValidationConstants.USAGE_ACTIVE, "   "));
        assertThrows(CustomException.class, () -> invoke(SHEET, rows));
    }

    @Test
    void reportsTheFullOffendingCountAndCapsTheRowList() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 3; i < 103; i++) {
            rows.add(row(i, ValidationConstants.USAGE_ACTIVE, null));
        }

        CustomException ex = assertThrows(CustomException.class, () -> invoke(SHEET, rows));

        assertTrue(ex.getMessage().contains("100 active row(s)"), "full count reported");
        assertTrue(ex.getMessage().contains("and 80 more"), "row list capped at 20");
    }

    // --- rows and runs that must NOT be rejected ---------------------------------------------

    @Test
    void allowsActiveRowsWithResolvedCodes() {
        List<Map<String, Object>> rows = List.of(
                row(3, ValidationConstants.USAGE_ACTIVE, "LNPERF20K_NI_01"),
                row(4, ValidationConstants.USAGE_ACTIVE, "LNPERF20K_NI_02"));
        assertDoesNotThrow(() -> invoke(SHEET, rows));
    }

    @Test
    void allowsInactiveRowWithNoCode() {
        assertDoesNotThrow(() -> invoke(SHEET, List.of(row(3, "Inactive", null))));
    }

    @Test
    void allowsRowAlreadyMarkedInvalidByValidation() {
        Map<String, Object> invalid = row(3, ValidationConstants.USAGE_ACTIVE, null);
        invalid.put(ValidationConstants.ROW_JSON_STATUS_KEY, ValidationConstants.ROW_STATUS_INVALID);
        assertDoesNotThrow(() -> invoke(SHEET, List.of(invalid)));
    }

    @Test
    void stillRejectsRowExplicitlyMarkedValid() {
        Map<String, Object> valid = row(3, ValidationConstants.USAGE_ACTIVE, null);
        valid.put(ValidationConstants.ROW_JSON_STATUS_KEY, ValidationConstants.ROW_STATUS_VALID);
        assertThrows(CustomException.class, () -> invoke(SHEET, List.of(valid)));
    }

    @Test
    void ignoresSheetsWithoutTheUserUsageColumn() {
        Map<String, Object> boundaryRow = new HashMap<>();
        boundaryRow.put(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY, 3);
        boundaryRow.put(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, "");
        assertDoesNotThrow(() -> invoke("Boundary List", List.of(boundaryRow)));
    }

    /**
     * A validation-only run annotates the sheet with a friendly per-row error list; the gate must never
     * convert that into a hard failure even if a deployment's MDMS marks the sheet persistent.
     */
    @Test
    void neverFailsAValidationOnlyRun() {
        resource.setType(ProcessingConstants.UNIFIED_CONSOLE_TYPE + ProcessingConstants.VALIDATION_TYPE_SUFFIX);
        List<Map<String, Object>> rows = List.of(row(3, ValidationConstants.USAGE_ACTIVE, null));
        assertDoesNotThrow(() -> invoke(SHEET, rows));
    }

    @Test
    void handlesNullAndEmptyRowLists() {
        assertDoesNotThrow(() -> invoke(SHEET, null));
        assertDoesNotThrow(() -> invoke(SHEET, List.of()));
    }

    @Test
    void rendersUnknownRowNumberWithoutFailing() {
        CustomException ex = assertThrows(CustomException.class,
                () -> invoke(SHEET, List.of(row(null, ValidationConstants.USAGE_ACTIVE, null))));
        assertTrue(ex.getMessage().contains("?"), "unknown row number renders as '?'");
    }

    /** Keeps the expected error code in one place without importing a package-private constant twice. */
    private static final class ErrorConstantsRef {
        static final String CODE = org.egov.excelingestion.config.ErrorConstants.BOUNDARY_CODE_MISSING_AT_PERSIST;
    }
}
