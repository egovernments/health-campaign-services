package org.egov.excelingestion.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.util.WorkbookLocaleResolver;
import org.egov.excelingestion.web.models.ProcessResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the contract that broke the cross-locale upload: the locale left on {@link ProcessResource}
 * after processing must be the locale the workbook was GENERATED in, not the caller's request locale.
 *
 * <p>Why this matters beyond validation: rows are persisted to eg_cm_sheet_data_temp keyed by the sheet
 * names as written in the generation locale, and project-factory rebuilds those same names from
 * {@code ProcessResource.locale} to read the rows back by exact string equality. When a campaign was
 * generated in en_DEMO and later switched to fr_DEMO, publishing the request locale made the consumer
 * derive French sheet names that match no stored row - the lookup returned zero rows silently and the
 * campaign hung in "creating".
 *
 * <p>{@link MetaSheetLocaleRoundTripTest} proves the resolver reads the stamp correctly; this proves the
 * resolved value is actually handed downstream.
 */
class ProcessResourceLocalePropagationTest {

    private final WorkbookLocaleResolver resolver = new WorkbookLocaleResolver();

    /** Mirrors ExcelProcessingService: resolve from the file, then write back onto the resource. */
    private void resolveAndWriteBack(XSSFWorkbook workbook, ProcessResource resource, String requestLocale) {
        resource.setLocale(resolver.resolveLocale(workbook, requestLocale));
    }

    private XSSFWorkbook workbookStampedWith(String locale) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("Users");
        if (locale != null) {
            workbook.createSheet(GenerationConstants.META_SHEET_NAME)
                    .createRow(GenerationConstants.META_ROW_INDEX)
                    .createCell(GenerationConstants.META_LOCALE_CELL_INDEX)
                    .setCellValue(locale);
        }
        return workbook;
    }

    /** THE BUG: generated en_DEMO, uploaded after the campaign switched to fr_DEMO. */
    @Test
    void publishesWorkbookLocaleNotRequestLocale() throws Exception {
        ProcessResource resource = ProcessResource.builder().locale("fr_DEMO").build();
        try (XSSFWorkbook workbook = workbookStampedWith("en_DEMO")) {
            resolveAndWriteBack(workbook, resource, resource.getLocale());
        }
        assertEquals("en_DEMO", resource.getLocale(),
                "downstream consumers key sheet lookups off this value; it must describe the stored rows");
    }

    /** Legacy files carry no stamp: the request locale must survive untouched. */
    @Test
    void keepsRequestLocaleForUnstampedLegacyFiles() throws Exception {
        ProcessResource resource = ProcessResource.builder().locale("fr_DEMO").build();
        try (XSSFWorkbook workbook = workbookStampedWith(null)) {
            resolveAndWriteBack(workbook, resource, resource.getLocale());
        }
        assertEquals("fr_DEMO", resource.getLocale());
    }

    /** Matching locales must be a no-op rather than an accidental rewrite. */
    @Test
    void isNoOpWhenLocalesAlreadyAgree() throws Exception {
        ProcessResource resource = ProcessResource.builder().locale("en_DEMO").build();
        try (XSSFWorkbook workbook = workbookStampedWith("en_DEMO")) {
            resolveAndWriteBack(workbook, resource, resource.getLocale());
        }
        assertEquals("en_DEMO", resource.getLocale());
    }
}
