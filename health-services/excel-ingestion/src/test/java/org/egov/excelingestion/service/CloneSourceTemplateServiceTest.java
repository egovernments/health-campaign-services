package org.egov.excelingestion.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.excelingestion.web.models.GenerateResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The user's clone rule: source and clone agree on Outside Household -> the source's file; one adds or drops it -> a new template. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloneSourceTemplateServiceTest {

    @Mock private CampaignService campaignService;
    @Mock private ExcelIngestionConfig config;
    @Mock private FileStoreService fileStoreService;

    private CloneSourceTemplateService service;
    private final RequestInfo requestInfo = new RequestInfo();

    @BeforeEach
    void setUp() {
        when(config.isCloneReuseSourceTemplate()).thenReturn(true);
        service = new CloneSourceTemplateService(campaignService, fileStoreService, config);
    }

    @Test
    void cloneWithOutsideHouseholdOfASourceWithOutsideHouseholdGetsTheSourcesFile() {
        stub(clone(List.of("OUTSIDEHOUSEHOLD"), List.of("A", "B"), null), source(List.of("HOUSEHOLDSTRATEGY", "OUTSIDEHOUSEHOLD"), List.of("B", "A"), "src-file"));
        assertEquals("src-file", service.sourceFileForUnchangedClone(generate(null), requestInfo));
    }

    @Test
    void cloneThatDropsOutsideHouseholdGetsANewTemplate() {
        stub(clone(List.of("HOUSEHOLDSTRATEGY"), List.of("A"), null), source(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), "src-file"));
        assertNull(service.sourceFileForUnchangedClone(generate(null), requestInfo));
    }

    @Test
    void cloneThatAddsOutsideHouseholdToAHouseholdOnlySourceGetsANewTemplate() {
        stub(clone(List.of("HOUSEHOLDSTRATEGY", "OUTSIDEHOUSEHOLD"), List.of("A"), null), source(List.of("HOUSEHOLDSTRATEGY"), List.of("A"), "src-file"));
        assertNull(service.sourceFileForUnchangedClone(generate(null), requestInfo));
    }

    @Test
    void householdOnlyCloneOfAHouseholdOnlySourceGetsTheSourcesFile() {
        stub(clone(List.of("HOUSEHOLDSTRATEGY"), List.of("A"), null), source(List.of("HOUSEHOLDSTRATEGY"), List.of("A"), "src-file"));
        assertEquals("src-file", service.sourceFileForUnchangedClone(generate(null), requestInfo));
    }

    @Test
    void theDeliveryMethodsOfTheSaveThatTriggeredTheGenerationWinOverTheStoredClone() {
        stub(clone(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), null), source(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), "src-file"));
        assertNull(service.sourceFileForUnchangedClone(generate(List.of("HOUSEHOLDSTRATEGY")), requestInfo));
        assertEquals("src-file", service.sourceFileForUnchangedClone(generate(List.of(Map.of("code", "OUTSIDEHOUSEHOLD"))), requestInfo));
    }

    @Test
    void cloneOnADifferentAreaGetsANewTemplate() {
        stub(clone(List.of("OUTSIDEHOUSEHOLD"), List.of("A", "C"), null), source(List.of("OUTSIDEHOUSEHOLD"), List.of("A", "B"), "src-file"));
        assertNull(service.sourceFileForUnchangedClone(generate(null), requestInfo));
    }

    @Test
    void editOfACreatedCampaignIsNotTreatedAsAClone() {
        stub(clone(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), "parent-1"), source(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), "src-file"));
        assertNull(service.sourceFileForUnchangedClone(generate(null), requestInfo));
    }

    @Test
    void campaignThatIsNotACloneGetsANewTemplate() {
        CampaignSearchResponse.CampaignDetail notAClone = clone(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), null);
        notAClone.getAdditionalDetails().setClonedCampaignId(null);
        when(campaignService.searchCampaignById(eq("clone-1"), eq("dev"), any())).thenReturn(notAClone);
        assertNull(service.sourceFileForUnchangedClone(generate(null), requestInfo));
    }

    @Test
    void sourceWithoutAnAttachedFileGivesANewTemplate() {
        stub(clone(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), null), source(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), null));
        assertNull(service.sourceFileForUnchangedClone(generate(null), requestInfo));
    }

    @Test
    void switchOffOrAnotherGenerationTypeGivesANewTemplate() {
        stub(clone(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), null), source(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), "src-file"));
        GenerateResource attendance = generate(null);
        attendance.setType("attendanceRegister");
        assertNull(service.sourceFileForUnchangedClone(attendance, requestInfo));
        when(config.isCloneReuseSourceTemplate()).thenReturn(false);
        assertNull(service.sourceFileForUnchangedClone(generate(null), requestInfo));
    }

    @Test
    void aFailedCampaignLookupFallsBackToANewTemplate() {
        when(campaignService.searchCampaignById(any(), any(), any())).thenThrow(new RuntimeException("project-factory down"));
        assertNull(service.sourceFileForUnchangedClone(generate(null), requestInfo));
    }

    @Test
    void cloneThatDropsOutsideHouseholdGetsTheSourcesUsersCopiedIntoItsNewTemplate() throws Exception {
        stub(clone(List.of("HOUSEHOLDSTRATEGY"), List.of("A"), null), source(List.of("HOUSEHOLDSTRATEGY", "OUTSIDEHOUSEHOLD"), List.of("A"), "src-file"));
        Workbook sourceFile = new XSSFWorkbook();
        CloneSourceDataMergerTest.sheet(sourceFile, "User List", new String[]{"HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"}, new Object[]{9000000001.0});
        when(fileStoreService.downloadExcelFromFileStore("src-file", "dev")).thenReturn(sourceFile);
        Workbook generated = new XSSFWorkbook();
        CloneSourceDataMergerTest.sheet(generated, "User List", new String[]{"HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"});

        byte[] result = service.withSourceData(generate(null), requestInfo, bytes(generated));

        try (Workbook merged = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertEquals(9000000001.0, merged.getSheet("User List").getRow(2).getCell(0).getNumericCellValue());
        }
    }

    @Test
    void cloneThatAgreesWithItsSourceOrIsNotACloneKeepsTheGeneratedBytes() throws Exception {
        stub(clone(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), null), source(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), "src-file"));
        byte[] generated = bytes(new XSSFWorkbook());
        assertArrayEquals(generated, service.withSourceData(generate(null), requestInfo, generated));
        stub(clone(List.of("HOUSEHOLDSTRATEGY"), List.of("A", "C"), null), source(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), "src-file"));
        assertArrayEquals(generated, service.withSourceData(generate(null), requestInfo, generated));
        verify(fileStoreService, never()).downloadExcelFromFileStore(any(), any());
    }

    @Test
    void aSourceFileThatCannotBeReadKeepsTheGeneratedBytes() throws Exception {
        stub(clone(List.of("HOUSEHOLDSTRATEGY"), List.of("A"), null), source(List.of("OUTSIDEHOUSEHOLD"), List.of("A"), "src-file"));
        when(fileStoreService.downloadExcelFromFileStore(any(), any())).thenThrow(new RuntimeException("filestore down"));
        byte[] generated = bytes(new XSSFWorkbook());
        assertArrayEquals(generated, service.withSourceData(generate(null), requestInfo, generated));
    }

    private static byte[] bytes(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private void stub(CampaignSearchResponse.CampaignDetail clone, CampaignSearchResponse.CampaignDetail source) {
        when(campaignService.searchCampaignById(eq("clone-1"), eq("dev"), any())).thenReturn(clone);
        when(campaignService.searchCampaignById(eq("source-1"), eq("dev"), any())).thenReturn(source);
    }

    private static GenerateResource generate(List<?> requestedMethods) {
        Map<String, Object> details = new HashMap<>();
        if (requestedMethods != null) {
            details.put("deliveryMethods", requestedMethods);
        }
        return GenerateResource.builder().tenantId("dev").type("unified-console").referenceId("clone-1")
                .referenceType("campaign").additionalDetails(details).build();
    }

    private static CampaignSearchResponse.CampaignDetail clone(List<String> methods, List<String> codes, String parentId) {
        CampaignSearchResponse.CampaignDetail c = campaign("clone-1", methods, codes, null);
        c.getAdditionalDetails().setClonedCampaignId("source-1");
        c.setParentId(parentId);
        return c;
    }

    private static CampaignSearchResponse.CampaignDetail source(List<String> methods, List<String> codes, String fileStoreId) {
        return campaign("source-1", methods, codes, fileStoreId);
    }

    private static CampaignSearchResponse.CampaignDetail campaign(String id, List<String> methods, List<String> codes, String fileStoreId) {
        return CampaignSearchResponse.CampaignDetail.builder().id(id).tenantId("dev").projectType("POLIO").hierarchyType("BM1807")
                .boundaries(codes.stream().map(code -> CampaignSearchResponse.BoundaryDetail.builder().code(code).build()).toList())
                .additionalDetails(CampaignSearchResponse.AdditionalDetails.builder().cycleData(Map.of("deliveryMethods", methods)).build())
                .resources(fileStoreId == null ? List.of() : List.of(Map.of("type", "unified-console-resources", "filestoreId", fileStoreId)))
                .build();
    }
}
