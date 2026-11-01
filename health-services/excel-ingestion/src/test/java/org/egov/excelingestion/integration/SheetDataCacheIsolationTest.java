package org.egov.excelingestion.integration;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.repository.ProcessingRepository;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.repository.SheetDataTempRepository;
import org.egov.excelingestion.service.CampaignCacheEvictor;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.ConfigBasedProcessingService;
import org.egov.excelingestion.service.FileStoreService;
import org.egov.excelingestion.service.ImmutableJoinService;
import org.egov.excelingestion.service.LocalizationService;
import org.egov.excelingestion.util.EnumValueNormalizer;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Guards HCS#2262 against the real Spring cache: validation and creation of the SAME uploaded file run
 * back to back. Validation's enum normalization rewrites the shared cached rows to canonical values, so
 * creation must evict them first - otherwise its immutable join compares canonical rows against the
 * still-localized baseline and rejects an unedited file as tampered.
 */
@SpringBootTest
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
class SheetDataCacheIsolationTest {

    @Autowired
    private ImmutableJoinService immutableJoinService;

    @Autowired
    private EnumValueNormalizer enumValueNormalizer;

    @Autowired
    private ExcelUtil excelUtil;

    @Autowired
    private CampaignCacheEvictor campaignCacheEvictor;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private CampaignService campaignService;

    @MockBean
    private FileStoreService fileStoreService;

    @MockBean
    private GeneratedFileRepository generatedFileRepository;

    // Context-quieting mocks, same set as ExcelIngestionIntegrationTest.
    @MockBean
    private ServiceRequestRepository serviceRequestRepository;

    @MockBean
    private LocalizationService localizationService;

    @MockBean
    private ConfigBasedProcessingService configBasedProcessingService;

    @MockBean
    private ProcessingRepository processingRepository;

    @MockBean
    private SheetDataTempRepository sheetDataTempRepository;

    private static final String TENANT = "mz";
    private static final String CAMPAIGN = "campaign-1";
    private static final String GEN_ID = "gen-1";
    private static final String BASELINE_FS = "baseline-fs";
    private static final String UPLOAD_FS = "upload-fs";
    private static final String SHEET = "User List";
    private static final String ROW_ID = "row-1";

    private static final String NAME_COL = "HCM_ADMIN_CONSOLE_USER_NAME";
    private static final String ROLE_COL = "HCM_ADMIN_CONSOLE_USER_ROLE";
    private static final String ROLE_1 = ROLE_COL + "_MULTISELECT_1";
    private static final String ROLE_2 = ROLE_COL + "_MULTISELECT_2";
    private static final String ROLE_PREFIX = "ACCESSCONTROL_ROLES_ROLES_";

    // The template shows roles by their localized label; the MDMS enum holds the canonical code.
    private static final Map<String, String> LOCALIZATION = Map.of(
            ROLE_PREFIX + "PROXIMITY_SUPERVISOR", "Proximity Supervisor",
            ROLE_PREFIX + "DISTRIBUTOR", "Distributor");

    private byte[] baselineBytes;

    @BeforeEach
    void setUp() throws Exception {
        cacheManager.getCache("excelSheetData").clear();
        baselineBytes = userWorkbook("Proximity Supervisor");

        when(generatedFileRepository.findByGenerationId(GEN_ID, TENANT)).thenReturn(
                GenerateResource.builder().id(GEN_ID).fileStoreId(BASELINE_FS)
                        .referenceId(CAMPAIGN).type("unified-console").tenantId(TENANT).build());
        // A fresh baseline workbook per download: the join closes the one it is given.
        when(fileStoreService.downloadExcelFromFileStore(BASELINE_FS, TENANT))
                .thenAnswer(inv -> open(baselineBytes));
        when(campaignService.searchCampaignById(anyString(), anyString(), any()))
                .thenReturn(CampaignSearchResponse.CampaignDetail.builder().id(CAMPAIGN).tenantId(TENANT).build());
    }

    @Test
    void creationAfterValidationOfTheSameUnchangedFilePasses() throws Exception {
        byte[] upload = userWorkbook("Proximity Supervisor"); // the user changed nothing

        runUpload("unified-console-validation", UPLOAD_FS, upload, true);

        // Validation left canonical values in the shared cache (the in-place mutation behind HCS#2262).
        Workbook probe = open(upload);
        List<Map<String, Object>> cached = excelUtil.convertSheetToMapListCached(
                UPLOAD_FS, SHEET, probe.getSheet(SHEET));
        assertEquals("PROXIMITY_SUPERVISOR", cached.get(0).get(ROLE_1));

        assertDoesNotThrow(() -> runUpload("unified-console-parse", UPLOAD_FS, upload, true));
    }

    // Reproduces the UAT failure: without the run-start evict, creation reads validation's canonical
    // rows from the cache and rejects the unedited file. Proves the harness detects the bug.
    @Test
    void withoutTheEvictCreationRejectsTheUnchangedFile() throws Exception {
        byte[] upload = userWorkbook("Proximity Supervisor");

        runUpload("unified-console-validation", UPLOAD_FS, upload, true);

        CustomException ex = assertThrows(CustomException.class,
                () -> runUpload("unified-console-parse", UPLOAD_FS, upload, false));
        assertEquals(ErrorConstants.IMMUTABLE_CELL_TAMPERED, ex.getCode());
    }

    @Test
    void aRealRoleEditIsStillRejectedAfterTheEvict() throws Exception {
        byte[] edited = userWorkbook("Distributor"); // pre-filled role actually changed

        CustomException ex = assertThrows(CustomException.class,
                () -> runUpload("unified-console-validation", UPLOAD_FS, edited, true));
        assertEquals(ErrorConstants.IMMUTABLE_CELL_TAMPERED, ex.getCode());

        ex = assertThrows(CustomException.class,
                () -> runUpload("unified-console-parse", UPLOAD_FS, edited, true));
        assertEquals(ErrorConstants.IMMUTABLE_CELL_TAMPERED, ex.getCode());
    }

    @Test
    void evictSheetDataDropsOnlyThatFilesSheets() {
        Cache cache = cacheManager.getCache("excelSheetData");
        cache.put("fs-1_User List", List.of());
        cache.put("fs-1_Facilities List", List.of());
        cache.put("fs-10_User List", List.of()); // shares the "fs-1" text but is another file
        cache.put("fs-2_User List", List.of());

        campaignCacheEvictor.evictSheetData("fs-1");

        assertNull(cache.get("fs-1_User List"));
        assertNull(cache.get("fs-1_Facilities List"));
        assertNotNull(cache.get("fs-10_User List"));
        assertNotNull(cache.get("fs-2_User List"));

        // Blank ids are a no-op, never a region-wide clear.
        campaignCacheEvictor.evictSheetData(null);
        campaignCacheEvictor.evictSheetData(" ");
        assertNotNull(cache.get("fs-2_User List"));
    }

    // ---------- helpers ----------

    /**
     * One upload run's cache-relevant steps, in production order (AsyncProcessingService evicts, then
     * ExcelProcessingService joins and normalizes).
     */
    private void runUpload(String type, String fileStoreId, byte[] uploadBytes, boolean evictFirst)
            throws Exception {
        if (evictFirst) {
            campaignCacheEvictor.evictSheetData(fileStoreId);
        }
        Workbook upload = open(uploadBytes);
        ProcessResource resource = ProcessResource.builder()
                .tenantId(TENANT).type(type).referenceId(CAMPAIGN).fileStoreId(fileStoreId).build();
        Map<String, Map<String, Object>> schemas = Map.of(SHEET, userSchema());
        immutableJoinService.applyImmutableBaseline(upload, resource, schemas, RequestInfo.builder().build(),
                new ArrayList<>(), LOCALIZATION);
        enumValueNormalizer.normalizeToCanonical(upload, resource, schemas, LOCALIZATION);
    }

    /** MDMS-shaped user schema: the role multi-select is freezeColumnIfFilled with a key prefix, as on UAT. */
    private Map<String, Object> userSchema() {
        Map<String, Object> name = new HashMap<>();
        name.put("name", NAME_COL);
        name.put("orderNumber", 1);

        Map<String, Object> role = new HashMap<>();
        role.put("name", ROLE_COL);
        role.put("orderNumber", 2);
        role.put("prefix", ROLE_PREFIX);
        role.put("freezeColumnIfFilled", true);
        role.put("multiSelectDetails", Map.of(
                "enum", List.of("DISTRIBUTOR", "PROXIMITY_SUPERVISOR"),
                "maxSelections", 2,
                "minSelections", 1));

        Map<String, Object> schema = new HashMap<>();
        schema.put("stringProperties", List.of(name, role));
        return schema;
    }

    /** A one-user template: technical row, header row, one pre-filled row stamped with a row-id. */
    private static byte[] userWorkbook(String roleLabel) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(SHEET);
            String[] technical = {NAME_COL, ROLE_1, ROLE_2, ProcessingConstants.ROW_ID_COLUMN_NAME};
            String[] headers = {"Name", "User Role 1", "User Role 2", ""};
            Row technicalRow = sheet.createRow(0);
            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < technical.length; i++) {
                technicalRow.createCell(i).setCellValue(technical[i]);
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("PS");
            data.createCell(1).setCellValue(roleLabel);
            data.createCell(3).setCellValue(ROW_ID);

            wb.createSheet(GenerationConstants.META_SHEET_NAME).createRow(0).createCell(0).setCellValue(GEN_ID);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static Workbook open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }
}
