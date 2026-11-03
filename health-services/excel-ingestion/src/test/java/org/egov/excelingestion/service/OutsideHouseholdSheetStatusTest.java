package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Bug 10: schema errors on the Outside Household sheet make outsideHouseholdSheetStatus invalid. */
class OutsideHouseholdSheetStatusTest {

    private static Sheet sheetWithHeader(String... technicalNames) {
        Sheet sheet = new XSSFWorkbook().createSheet("any localized name");
        Row header = sheet.createRow(0);
        for (int i = 0; i < technicalNames.length; i++) {
            header.createCell(i).setCellValue(technicalNames[i]);
        }
        return sheet;
    }

    @Test
    void theOutsideHouseholdSheetIsFoundByItsTechnicalColumnsInAnyLocale() {
        assertEquals(ValidationConstants.SHEET_KIND_OUTSIDE_HOUSEHOLD, ExcelProcessingService.sheetKindOf(
                sheetWithHeader("BM1807_COUNTRY", "HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_NAME")));
        assertNull(ExcelProcessingService.sheetKindOf(sheetWithHeader("HCM_ADMIN_CONSOLE_FACILITY_NAME", "HCM_ADMIN_CONSOLE_FACILITY_USAGE")));
        assertNull(ExcelProcessingService.sheetKindOf(new XSSFWorkbook().createSheet("empty")));
    }

    @Test
    void aSchemaErrorKeepsTheSheetInvalidAfterItsProcessorFindsNothing() {
        EnrichmentUtil enrichment = new EnrichmentUtil();
        ProcessResource resource = ProcessResource.builder().additionalDetails(new HashMap<>()).build();
        ValidationError blankName = ValidationError.builder().rowNumber(5).status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Required field 'Site Name' is missing").build();

        // step 1 (schema), then the Outside Household processor with no duplicate/boundary error
        enrichment.enrichErrorAndStatusInAdditionalDetails(resource, List.of(blankName), ValidationConstants.SHEET_KIND_OUTSIDE_HOUSEHOLD);
        enrichment.enrichErrorAndStatusInAdditionalDetails(resource, List.of(), ValidationConstants.SHEET_KIND_OUTSIDE_HOUSEHOLD);

        assertEquals(ValidationConstants.STATUS_INVALID, resource.getAdditionalDetails().get(ValidationConstants.ADDITIONAL_DETAILS_OUTSIDE_HOUSEHOLD_SHEET_STATUS));
        assertEquals(1L, ((Number) resource.getAdditionalDetails().get("totalErrors")).longValue());
    }
}
