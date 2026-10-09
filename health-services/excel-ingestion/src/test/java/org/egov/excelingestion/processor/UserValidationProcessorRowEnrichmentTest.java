package org.egov.excelingestion.processor;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.*;
import org.egov.excelingestion.util.*;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ValidationError;
import org.egov.common.contract.request.RequestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the validator enriches the cached sheetData with per-row
 * #status# and #errorDetails# so the persistence step writes per-row validity
 * into rowJson for downstream consumers (project-factory).
 */
class UserValidationProcessorRowEnrichmentTest {

    @Mock private ValidationService validationService;
    @Mock private RestTemplate restTemplate;
    @Mock private ExcelIngestionConfig config;
    @Mock private EnrichmentUtil enrichmentUtil;
    @Mock private CampaignService campaignService;
    @Mock private BoundaryService boundaryService;
    @Mock private BoundaryUtil boundaryUtil;
    @Mock private ExcelUtil excelUtil;
    @Mock private CustomExceptionHandler exceptionHandler;

    private UserValidationProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new UserValidationProcessor(
                validationService, restTemplate, config, enrichmentUtil, campaignService,
                boundaryService, boundaryUtil, excelUtil, exceptionHandler
        );
    }

    @Test
    void tagsInvalidRowWithStatusAndErrorDetails() throws Exception {
        Map<String, Object> validRow = new HashMap<>();
        validRow.put("__actualRowNumber__", 2);
        validRow.put("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", "+91-1");

        Map<String, Object> invalidRow = new HashMap<>();
        invalidRow.put("__actualRowNumber__", 3);
        invalidRow.put("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", "+91-2");

        List<Map<String, Object>> sheetData = new ArrayList<>(Arrays.asList(validRow, invalidRow));

        ValidationError err = new ValidationError();
        err.setRowNumber(3);
        err.setErrorDetails("phone format invalid");
        err.setStatus(ValidationConstants.STATUS_INVALID);
        List<ValidationError> errors = new ArrayList<>(Collections.singletonList(err));

        Method m = UserValidationProcessor.class.getDeclaredMethod(
                "enrichSheetDataWithRowStatus", List.class, List.class);
        m.setAccessible(true);
        m.invoke(processor, sheetData, errors);

        assertEquals(ValidationConstants.ROW_STATUS_VALID, validRow.get(ValidationConstants.ROW_JSON_STATUS_KEY));
        assertNull(validRow.get(ValidationConstants.ROW_JSON_ERROR_DETAILS_KEY));

        assertEquals(ValidationConstants.ROW_STATUS_INVALID, invalidRow.get(ValidationConstants.ROW_JSON_STATUS_KEY));
        assertEquals("phone format invalid", invalidRow.get(ValidationConstants.ROW_JSON_ERROR_DETAILS_KEY));
    }

    @Test
    void joinsMultipleErrorsForSameRowAndDeduplicates() throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("__actualRowNumber__", 2);
        List<Map<String, Object>> sheetData = new ArrayList<>(Collections.singletonList(row));

        ValidationError e1 = new ValidationError();
        e1.setRowNumber(2);
        e1.setErrorDetails("phone bad");
        e1.setStatus(ValidationConstants.STATUS_INVALID);
        ValidationError e2 = new ValidationError();
        e2.setRowNumber(2);
        e2.setErrorDetails("boundary bad");
        e2.setStatus(ValidationConstants.STATUS_INVALID);
        ValidationError e3 = new ValidationError();
        e3.setRowNumber(2);
        e3.setErrorDetails("phone bad"); // duplicate
        e3.setStatus(ValidationConstants.STATUS_INVALID);

        Method m = UserValidationProcessor.class.getDeclaredMethod(
                "enrichSheetDataWithRowStatus", List.class, List.class);
        m.setAccessible(true);
        m.invoke(processor, sheetData, Arrays.asList(e1, e2, e3));

        assertEquals(ValidationConstants.ROW_STATUS_INVALID, row.get(ValidationConstants.ROW_JSON_STATUS_KEY));
        String details = (String) row.get(ValidationConstants.ROW_JSON_ERROR_DETAILS_KEY);
        assertNotNull(details);
        assertTrue(details.contains("phone bad"));
        assertTrue(details.contains("boundary bad"));
        // Deduplication — "phone bad" appears once
        int firstIdx = details.indexOf("phone bad");
        int secondIdx = details.indexOf("phone bad", firstIdx + 1);
        assertEquals(-1, secondIdx, "duplicate error message should be deduplicated");
    }

    @Test
    void doesNotDowngradeAlreadyInvalidRowsOnSecondPass() throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("__actualRowNumber__", 2);
        row.put(ValidationConstants.ROW_JSON_STATUS_KEY, ValidationConstants.ROW_STATUS_INVALID);
        List<Map<String, Object>> sheetData = new ArrayList<>(Collections.singletonList(row));

        // Empty errors (this validation pass produced none, but row was already INVALID)
        Method m = UserValidationProcessor.class.getDeclaredMethod(
                "enrichSheetDataWithRowStatus", List.class, List.class);
        m.setAccessible(true);
        m.invoke(processor, sheetData, Collections.<ValidationError>emptyList());

        assertEquals(ValidationConstants.ROW_STATUS_INVALID, row.get(ValidationConstants.ROW_JSON_STATUS_KEY),
                "once a row is INVALID it should stay INVALID even if a later validation pass finds no errors");
    }
}
