package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.BoundaryColumnUtil;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.egov.excelingestion.util.WorkbookLocaleResolver;
import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check of the locale stamp: runs the REAL generation-side metadata write
 * (ConfigBasedGenerationService.applyWorkbookSettings), serializes the workbook to bytes the way
 * generation does, re-reads it, and resolves the locale with the REAL upload-side resolver.
 *
 * <p>This is what proves the cross-locale bug is fixed: a file generated in en_IN resolves to en_IN
 * even when the uploading user's session locale is fr_FR.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetaSheetLocaleRoundTripTest {

    @Mock private ApplicationContext applicationContext;
    @Mock private ExcelDataPopulator excelDataPopulator;
    @Mock private BoundaryColumnUtil boundaryColumnUtil;
    @Mock private HierarchicalBoundaryUtil hierarchicalBoundaryUtil;
    @Mock private CellProtectionManager cellProtectionManager;
    @Mock private ExcelIngestionConfig config;
    @Mock private CustomExceptionHandler exceptionHandler;
    @Mock private GenerationConfigValidationService validationService;

    private ConfigBasedGenerationService service;
    private final WorkbookLocaleResolver resolver = new WorkbookLocaleResolver();

    @BeforeEach
    void setUp() {
        // Excel's real sheet-name cap; without this the mock returns 0 and truncates every name to "".
        org.mockito.Mockito.when(config.getSheetNameMaxLength()).thenReturn(31);
        service = new ConfigBasedGenerationService(applicationContext, excelDataPopulator,
                boundaryColumnUtil, hierarchicalBoundaryUtil, cellProtectionManager, config,
                exceptionHandler, validationService);
    }

    /** Invokes the real private generation-side stamp. */
    private XSSFWorkbook stamp(String generationId, boolean unprotectedJoinMode, String locale) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("Users");

        ProcessorGenerationConfig processorConfig = ProcessorGenerationConfig.builder()
                .applyWorkbookProtection(false)
                .zoomLevel(80)
                .sheets(Collections.singletonList(SheetGenerationConfig.builder()
                        .sheetName("Users").visible(true).order(1).build()))
                .build();

        Method m = ConfigBasedGenerationService.class.getDeclaredMethod("applyWorkbookSettings",
                XSSFWorkbook.class, ProcessorGenerationConfig.class, String.class, java.util.Map.class,
                String.class, boolean.class, String.class);
        m.setAccessible(true);
        m.invoke(service, workbook, processorConfig, "Users", new HashMap<String, String>(),
                generationId, unprotectedJoinMode, locale);
        return workbook;
    }

    /** Round-trips through bytes, as real generation/upload does. */
    private XSSFWorkbook reopen(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        return new XSSFWorkbook(new ByteArrayInputStream(bos.toByteArray()));
    }

    /** THE BUG: generated en_IN, uploaded by a fr_FR user. Must resolve to en_IN. */
    @Test
    void generatedInOneLocaleResolvesToThatLocaleWhenUploadedInAnother() throws Exception {
        try (XSSFWorkbook reopened = reopen(stamp("gen-123", true, "en_IN"))) {
            assertEquals("en_IN", resolver.resolveLocale(reopened, "fr_FR"));
        }
    }

    /** Non-join-mode file: no generationId, but locale must still be stamped and resolved. */
    @Test
    void localeStampedForNonJoinModeFiles() throws Exception {
        try (XSSFWorkbook reopened = reopen(stamp("gen-123", false, "pt_MZ"))) {
            assertNotNull(reopened.getSheet(GenerationConstants.META_SHEET_NAME));
            assertEquals("pt_MZ", resolver.resolveLocale(reopened, "en_IN"));
        }
    }

    /**
     * The constraint: ImmutableJoinService's generationId read must be unaffected. Cell 0 must still
     * hold exactly the generationId, and must stay EMPTY for non-join-mode files (fail-closed contract).
     */
    @Test
    void generationIdCellIsUnchangedByTheLocaleStamp() throws Exception {
        try (XSSFWorkbook joinMode = reopen(stamp("gen-123", true, "en_IN"))) {
            assertEquals("gen-123", joinMode.getSheet(GenerationConstants.META_SHEET_NAME)
                    .getRow(GenerationConstants.META_ROW_INDEX)
                    .getCell(GenerationConstants.META_GENERATION_ID_CELL_INDEX)
                    .getStringCellValue());
        }
        // Non-join-mode: locale present, generationId cell absent so ImmutableJoinService still
        // sees "no generationId" exactly as before this change.
        try (XSSFWorkbook nonJoin = reopen(stamp("gen-123", false, "en_IN"))) {
            assertNull(nonJoin.getSheet(GenerationConstants.META_SHEET_NAME)
                    .getRow(GenerationConstants.META_ROW_INDEX)
                    .getCell(GenerationConstants.META_GENERATION_ID_CELL_INDEX));
        }
    }

    /** The metadata sheet must be hidden so users never see it. */
    @Test
    void metaSheetIsHidden() throws Exception {
        try (XSSFWorkbook reopened = reopen(stamp(null, false, "en_IN"))) {
            int idx = reopened.getSheetIndex(GenerationConstants.META_SHEET_NAME);
            assertTrue(idx >= 0);
            assertTrue(reopened.isSheetHidden(idx));
        }
    }

    /** No locale and no generationId: no metadata sheet at all, and no stray empty sheet. */
    @Test
    void noMetaSheetWhenNothingToStamp() throws Exception {
        try (XSSFWorkbook reopened = reopen(stamp(null, false, null))) {
            assertNull(reopened.getSheet(GenerationConstants.META_SHEET_NAME));
            assertEquals("en_IN", resolver.resolveLocale(reopened, "en_IN"));
        }
    }

    /** A visible data sheet must remain visible and untouched. */
    @Test
    void dataSheetRemainsVisible() throws Exception {
        try (XSSFWorkbook reopened = reopen(stamp("gen-123", true, "en_IN"))) {
            Sheet users = reopened.getSheet("Users");
            assertNotNull(users);
            assertTrue(!reopened.isSheetHidden(reopened.getSheetIndex("Users")));
        }
    }
}
