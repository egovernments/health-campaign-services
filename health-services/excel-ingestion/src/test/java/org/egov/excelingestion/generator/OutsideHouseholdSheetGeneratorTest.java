package org.egov.excelingestion.generator;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.FacilityService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.OutsideHouseholdTypeMasterService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.ColumnDefMaker;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.util.ExcelStyleHelper;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.Boundary;
import org.egov.excelingestion.web.models.BoundaryHierarchy;
import org.egov.excelingestion.web.models.BoundaryHierarchyChild;
import org.egov.excelingestion.web.models.BoundaryHierarchyResponse;
import org.egov.excelingestion.web.models.BoundarySearchResponse;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Outside Household sheet generation driven through the REAL ExcelDataPopulator, CellProtectionManager,
 * HierarchicalBoundaryUtil and SchemaColumnDefUtil (only remote services are mocked): stored site rows are
 * pre-filled as-is, carry-over from earlier campaigns (permanent only, lowest OR second-lowest level, not
 * already listed), and the inline Site Status default formula. Site categories exist only in the (test) MDMS
 * schema below. Which usages are sites comes entirely from the (mocked) HCM.OUTSIDE_HOUSEHOLD_TYPES
 * master, not from the schema.
 */
@ExtendWith(MockitoExtension.class)
class OutsideHouseholdSheetGeneratorTest {

    private static final String SHEET = "LocalizedOHSheet";
    private static final String HIERARCHY = "ADMIN";
    private static final String TENANT = "mz";
    private static final String CAMPAIGN_ID = "campaign-1";
    private static final String CAMPAIGN_NUMBER = "CMP-1";
    private static final List<String> SCHEMA_CATEGORIES = Arrays.asList("SCHOOL", "MARKET", "BUS_STATION", "OTHER");
    private static final Map<String, Boolean> ACTIVE_OUTSIDE_HOUSEHOLD_TYPES = Map.of("OUTSIDE_HOUSEHOLD_PLACES", true, "TRANSIT_POST", false);

    @Mock private MDMSService mdmsService;
    @Mock private CampaignService campaignService;
    @Mock private FacilityService facilityService;
    @Mock private BoundaryService boundaryService;
    @Mock private BoundaryUtil boundaryUtil;
    @Mock private CustomExceptionHandler exceptionHandler;
    @Mock private ExcelIngestionConfig config;
    @Mock private OutsideHouseholdTypeMasterService outsideHouseholdTypeMasterService;

    private OutsideHouseholdSheetGenerator generator;

    @BeforeEach
    void setUp() {
        lenient().when(config.getExcelRowLimit()).thenReturn(200);
        lenient().when(config.getExcelSheetPassword()).thenReturn("pw");
        lenient().when(config.getValidationErrorColor()).thenReturn("#ff0000");
        lenient().when(config.getDefaultHeaderColor()).thenReturn("#93C47D");

        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        HierarchicalBoundaryUtil hierarchicalBoundaryUtil =
                new HierarchicalBoundaryUtil(config, boundaryService, boundaryUtil, styleHelper);
        SchemaColumnDefUtil schemaColumnDefUtil = new SchemaColumnDefUtil(new ColumnDefMaker(), exceptionHandler);

        generator = new OutsideHouseholdSheetGenerator(mdmsService, campaignService, facilityService, boundaryUtil,
                protectionManager, exceptionHandler, schemaColumnDefUtil, populator, hierarchicalBoundaryUtil,
                outsideHouseholdTypeMasterService, config);

        lenient().when(outsideHouseholdTypeMasterService.getOutsideHouseholdTypes(any(), eq(TENANT))).thenReturn(ACTIVE_OUTSIDE_HOUSEHOLD_TYPES);
        stubSchema();
        stubBoundaries();
        stubCampaign();
    }

    @Test
    void columnsComeFromSchemaAfterTheBoundaryColumnsLikeTheFacilitySheet() {
        Sheet sheet = generate(false).getSheet(SHEET);

        assertEquals(Arrays.asList(
                "ADMIN_PROVINCE", "ADMIN_DISTRICT", "ADMIN_VILLAGE",
                ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY,
                ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY,
                ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY,
                ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY,
                ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY,
                ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY,
                ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY), technicalHeaders(sheet));

        assertTrue(sheet.isColumnHidden(column(sheet, ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY)));
        assertTrue(sheet.isColumnHidden(column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY)));
        assertFalse(sheet.isColumnHidden(column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY)));
        assertTrue(sheet.getRow(0).getZeroHeight(), "Technical header row must be hidden");
    }

    @Test
    void storedOutsideHouseholdRowsArePrefilledAsIs() {
        Sheet sheet = generate(false).getSheet(SHEET);

        assertEquals("fac-ohp-1", cell(sheet, 2, ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY));
        assertEquals("OUTSIDE_HOUSEHOLD_PLACES", cell(sheet, 2, ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY));
        assertEquals("SCHOOL", cell(sheet, 2, ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY));
        assertEquals("Central School OUTSIDE_HOUSEHOLD_PLACES", cell(sheet, 2, ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY));
        assertEquals("Permanent", cell(sheet, 2, ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY));
        assertEquals("Active", cell(sheet, 2, ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY));
        assertEquals("V1", cell(sheet, 2, ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY));
        assertEquals("V1", cell(sheet, 2, "ADMIN_VILLAGE"));
        assertEquals("P1", cell(sheet, 2, "ADMIN_PROVINCE"));

        // The plain facility ("warehouse", no hidden clientReferenceId) is a facility-sheet row, not a
        // site: it must not appear anywhere in the pre-filled sites.
        for (int rowIdx = 2; rowIdx <= 3; rowIdx++) {
            assertFalse("Main Warehouse".equals(cell(sheet, rowIdx, ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY)));
        }
    }

    @Test
    void visibleHeadersAndEnumLabelsComeFromLocalization() {
        Map<String, String> localization = new HashMap<>();
        localization.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY, "Type de site");
        localization.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY + "_INACTIVE", "Inactif");

        Sheet sheet = generate(false, localization).getSheet(SHEET);

        int typeCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY);
        assertEquals("Type de site", sheet.getRow(1).getCell(typeCol).getStringCellValue());
        int nameCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY);
        assertEquals(ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY, sheet.getRow(1).getCell(nameCol).getStringCellValue());
        int usageCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY);
        assertEquals("Inactif", sheet.getRow(3).getCell(usageCol).getStringCellValue());
    }

    @Test
    void prefillsCampaignOutsideHouseholdSitesThenCarryOverOutsideHouseholdSites() {
        Sheet sheet = generate(false).getSheet(SHEET);

        // Row 3: earlier campaign's permanent site on a lowest-level boundary, listed as Inactive. Found
        // via fetchAllPermanentFacilities(tenant, "TRANSIT_POST", ...) - one paged search per active type.
        assertEquals("fac-tp-9", cell(sheet, 3, ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY));
        assertEquals("TRANSIT_POST", cell(sheet, 3, ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY));
        assertEquals("BUS_STATION", cell(sheet, 3, ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY));
        assertEquals("Old Bus Stand", cell(sheet, 3, ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY));
        assertEquals("Permanent", cell(sheet, 3, ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY));
        assertEquals("Inactive", cell(sheet, 3, ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY));
        assertEquals("V2", cell(sheet, 3, ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY));

        // Nothing else: the earlier OUTSIDE_HOUSEHOLD_PLACES site already listed (a), the earlier OUTSIDE_HOUSEHOLD_PLACES site off the carry-over
        // boundaries, and the root-level OUTSIDE_HOUSEHOLD_PLACES site are all left out. Row 4 exists as a templated empty row
        // (it carries the Site Status default formula), so this is checked on the actual site data.
        assertNull(cell(sheet, 4, ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY), "Only the two expected sites may be pre-filled");
        assertNull(cell(sheet, 4, ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY));
    }

    @Test
    void carryOverIncludesSecondLowestLevelNotJustLowest() {
        lenient().when(boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(any(), eq(CAMPAIGN_ID), eq(TENANT),
                eq(HIERARCHY), eq(2), any())).thenReturn(Set.of("V1", "V2", "D1"));

        Sheet sheet = generate(false).getSheet(SHEET);

        // fac-ohp-2 sits on D1 (second-lowest / District level) - now carried over as Inactive. Both active
        // types contribute a carried-over row here, so look the row up by id rather than a fixed index.
        int rowIdx = rowOf(sheet, "fac-ohp-2");
        assertTrue(rowIdx > 0, "fac-ohp-2 (second-lowest level) must be carried over");
        assertEquals("MARKET", cell(sheet, rowIdx, ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY));
        assertEquals("Inactive", cell(sheet, rowIdx, ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY));
        // fac-ohp-3 sits on P1 (root / Province level, higher than second-lowest) - still excluded.
        assertEquals(-1, rowOf(sheet, "fac-ohp-3"), "a root-level site must never be carried over");
    }

    @Test
    void skipsEarlierCampaignFacilitiesWithoutAClientReferenceId() {
        lenient().when(facilityService.fetchAllPermanentFacilities(eq(TENANT), eq("TRANSIT_POST"), any()))
                .thenReturn(List.of(noClientReferenceIdFacility("Unlinked site", "V2")));

        Sheet sheet = generate(false).getSheet(SHEET);

        assertNull(cell(sheet, 3, ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY),
                "A facility without a clientReferenceId must not be offered as a site");
    }

    @Test
    void earlierOutsideHouseholdWithoutCategoryLeavesCategoryBlank() {
        Map<String, Object> facility = permanentFacility("fac-x", "OUTSIDE_HOUSEHOLD_PLACES", "Market OUTSIDE_HOUSEHOLD_PLACES", "V1", null);

        Map<String, Object> row = OutsideHouseholdSheetGenerator.toOutsideHouseholdRowFromFacility(facility, "V1");

        assertNull(row.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY));
        assertEquals("Inactive", row.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY));
        assertEquals("Permanent", row.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY));
        assertEquals("fac-x", row.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY));
    }

    @Test
    void oneSearchCallPerActiveOutsideHouseholdType_notOnePerRowOrOneCombinedCall() {
        generate(false);

        verify(facilityService).fetchAllPermanentFacilities(eq(TENANT), eq("OUTSIDE_HOUSEHOLD_PLACES"), any());
        verify(facilityService).fetchAllPermanentFacilities(eq(TENANT), eq("TRANSIT_POST"), any());
    }

    @Test
    void categoryDropdownDependsOnTheOutsideHouseholdType() {
        lenient().when(outsideHouseholdTypeMasterService.getCategoriesByOutsideHouseholdType(any(), eq(TENANT))).thenReturn(Map.of(
                "OUTSIDE_HOUSEHOLD_PLACES", List.of("SCHOOL", "MARKET"), "TRANSIT_POST", List.of("BUS_STATION")));
        XSSFWorkbook workbook = generate(false);
        Sheet sheet = workbook.getSheet(SHEET);
        int categoryCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY);

        List<String> categoryFormulas = new ArrayList<>();
        for (org.apache.poi.ss.usermodel.DataValidation dv : ((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).getDataValidations()) {
            for (org.apache.poi.ss.util.CellRangeAddress region : dv.getRegions().getCellRangeAddresses()) {
                if (region.getFirstColumn() == categoryCol) {
                    categoryFormulas.add(dv.getValidationConstraint().getFormula1());
                    assertEquals(2, region.getFirstRow(), "dropdown starts on the first data row");
                }
            }
        }
        assertEquals(1, categoryFormulas.size(), "the plain enum dropdown is replaced, not doubled: " + categoryFormulas);
        assertTrue(categoryFormulas.get(0).startsWith("INDIRECT(IFERROR(\"_hOutsideHouseholdCat\"&MATCH("), categoryFormulas.get(0));
        assertTrue(categoryFormulas.get(0).contains("_hOutsideHouseholdTypes"), categoryFormulas.get(0));

        Sheet helper = workbook.getSheet("_h_OHCategories_h_");
        assertNotNull(helper);
        assertTrue(workbook.isSheetHidden(workbook.getSheetIndex(helper)));
        // Types listed by label (rows 1..n) then code (rows n+1..2n); both point at the same category column
        List<String> typesInHelper = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            typesInHelper.add(helper.getRow(r).getCell(0).getStringCellValue());
        }
        int clf = typesInHelper.indexOf("OUTSIDE_HOUSEHOLD_PLACES");
        int transit = typesInHelper.indexOf("TRANSIT_POST");
        assertEquals(workbook.getName("_hOutsideHouseholdCat" + (clf + 1)).getRefersToFormula(),
                workbook.getName("_hOutsideHouseholdCat" + (clf + 3)).getRefersToFormula());
        String clfRange = workbook.getName("_hOutsideHouseholdCat" + (clf + 1)).getRefersToFormula();
        String transitRange = workbook.getName("_hOutsideHouseholdCat" + (transit + 1)).getRefersToFormula();
        assertTrue(clfRange.matches(".*!\\$[A-Z]+\\$1:\\$[A-Z]+\\$2"), "OUTSIDE_HOUSEHOLD_PLACES has 2 categories: " + clfRange);
        assertTrue(transitRange.matches(".*!\\$[A-Z]+\\$1:\\$[A-Z]+\\$1"), "TRANSIT_POST has 1: " + transitRange);
    }

    @Test
    void withoutCategoryListsTheSchemaDropdownStays() {
        Sheet sheet = generate(false).getSheet(SHEET);
        int categoryCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY);
        boolean plainList = false;
        for (org.apache.poi.ss.usermodel.DataValidation dv : ((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).getDataValidations()) {
            if (dv.getRegions().getCellRangeAddresses()[0].getFirstColumn() == categoryCol) {
                plainList = dv.getValidationConstraint().getFormula1() == null
                        || !dv.getValidationConstraint().getFormula1().contains("INDIRECT");
            }
        }
        assertTrue(plainList, "no master categories: keep the schema's own Category dropdown");
    }

    @Test
    void emptyRowsGetAnInlineFormulaListingLabelAndCodeOfPermanentByDefaultTypes() {
        Sheet sheet = generate(false).getSheet(SHEET);
        int statusCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY);
        Row emptyRow = sheet.getRow(4); // past the 2 pre-filled rows (rows 2 and 3)
        assertNotNull(emptyRow, "An empty templated row must still exist (dropdown/formula range)");
        Cell statusCell = emptyRow.getCell(statusCol);
        assertEquals(CellType.FORMULA, statusCell.getCellType());
        String formula = statusCell.getCellFormula();
        assertTrue(formula.contains("OR("), "only OUTSIDE_HOUSEHOLD_PLACES is permanent by default, so the formula must branch");
        assertTrue(formula.contains("\"OUTSIDE_HOUSEHOLD_PLACES\""), "OUTSIDE_HOUSEHOLD_PLACES's code must be in the OR list");
        assertFalse(formula.contains("TRANSIT_POST"), "TRANSIT_POST is not permanent by default, must not appear");
    }

    @Test
    void regressionFormulaMatchesBothTheLabelAndTheCode() {
        Map<String, String> localization = new HashMap<>();
        localization.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY + "_OUTSIDE_HOUSEHOLD_PLACES", "Lieux hors menage");

        Sheet sheet = generate(false, localization).getSheet(SHEET);
        int typeCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY);
        int statusCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY);
        Row emptyRow = sheet.getRow(4);
        Cell typeCell = emptyRow.getCell(typeCol) != null ? emptyRow.getCell(typeCol)
                : emptyRow.createCell(typeCol, CellType.STRING);
        Cell statusCell = emptyRow.getCell(statusCol);

        // EnumValueNormalizer may rewrite the Site Type cell to the CODE before or after this formula is
        // read (HashMap column order) - the default must resolve correctly whichever value is there.
        assertEquals("Permanent", evaluate(sheet, typeCell, "OUTSIDE_HOUSEHOLD_PLACES", statusCell));
        assertEquals("Permanent", evaluate(sheet, typeCell, "Lieux hors menage", statusCell));
        assertEquals("Temporary", evaluate(sheet, typeCell, "TRANSIT_POST", statusCell));
        assertEquals("", evaluate(sheet, typeCell, "", statusCell), "blank type -> blank status");
    }

    @Test
    void noPermanentByDefaultType_alwaysDefaultsToTemporary() {
        lenient().when(outsideHouseholdTypeMasterService.getOutsideHouseholdTypes(any(), eq(TENANT)))
                .thenReturn(Map.of("OUTSIDE_HOUSEHOLD_PLACES", false, "TRANSIT_POST", false));

        Sheet sheet = generate(false).getSheet(SHEET);
        int typeCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY);
        int statusCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY);
        Row emptyRow = sheet.getRow(4);
        Cell statusCell = emptyRow.getCell(statusCol);
        assertFalse(statusCell.getCellFormula().contains("OR("), "no type is permanent by default");

        Cell typeCell = emptyRow.createCell(typeCol, CellType.STRING);
        assertEquals("Temporary", evaluate(sheet, typeCell, "OUTSIDE_HOUSEHOLD_PLACES", statusCell));
    }

    private String evaluate(Sheet sheet, Cell typeCell, String typeValue, Cell statusCell) {
        typeCell.setCellValue(typeValue);
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        evaluator.clearAllCachedResultValues();
        return evaluator.evaluate(statusCell).getStringValue();
    }

    @Test
    void preFilledRowsKeepALiteralOutsideHouseholdStatusNeverAFormula() {
        Sheet sheet = generate(false).getSheet(SHEET);
        int statusCol = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY);

        assertEquals(CellType.STRING, sheet.getRow(2).getCell(statusCol).getCellType());
        assertEquals(CellType.STRING, sheet.getRow(3).getCell(statusCol).getCellType());
    }

    @Test
    void joinModeStampsRowIdsOnPrefilledRowsOnly() {
        Sheet sheet = generate(true).getSheet(SHEET);

        int rowIdCol = column(sheet, ProcessingConstants.ROW_ID_COLUMN_NAME);
        assertTrue(rowIdCol >= 0, "Join mode must add the hidden row-id column");
        assertTrue(sheet.isColumnHidden(rowIdCol));
        assertFalse(sheet.getRow(2).getCell(rowIdCol).getStringCellValue().isEmpty());
        assertFalse(sheet.getRow(3).getCell(rowIdCol).getStringCellValue().isEmpty());
        assertNull(sheet.getRow(4).getCell(rowIdCol), "No row-id beyond the pre-filled rows");
        assertTrue(ProcessingConstants.isUserEntrySheet("org.egov.excelingestion.generator.OutsideHouseholdSheetGenerator"));
    }

    // ---------------------------------------------------------------- helpers

    private XSSFWorkbook generate(boolean joinMode) {
        return generate(joinMode, new HashMap<>());
    }

    private XSSFWorkbook generate(boolean joinMode, Map<String, String> localization) {
        GenerateResource resource = GenerateResource.builder()
                .id("gen-1").tenantId(TENANT).type("unified-console").hierarchyType(HIERARCHY)
                .referenceId(CAMPAIGN_ID).referenceType("campaign").build();
        resource.setUnprotectedJoinMode(joinMode);
        SheetGenerationConfig sheetConfig = SheetGenerationConfig.builder()
                .sheetName("HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_LIST").schemaName("outside-household-microplan-ingestion")
                .generationClass("OutsideHouseholdSheetGenerator").build();
        return generator.generateSheet(new XSSFWorkbook(), SHEET, sheetConfig, resource, new RequestInfo(), localization);
    }

    private void stubSchema() {
        lenient().when(mdmsService.searchMDMS(any(), eq(TENANT), eq(ProcessingConstants.MDMS_SCHEMA_CODE),
                anyMap(), anyInt(), anyInt())).thenReturn(Collections.singletonList(outsideHouseholdSchema()));
    }

    private void stubCampaign() {
        CampaignSearchResponse.CampaignDetail campaign = CampaignSearchResponse.CampaignDetail.builder()
                .id(CAMPAIGN_ID).campaignNumber(CAMPAIGN_NUMBER).build();
        lenient().when(campaignService.searchCampaignById(eq(CAMPAIGN_ID), eq(TENANT), any())).thenReturn(campaign);
        lenient().when(campaignService.getBoundariesFromCampaign(eq(CAMPAIGN_ID), eq(TENANT), any()))
                .thenReturn(Collections.singletonList(CampaignSearchResponse.BoundaryDetail.builder()
                        .code("P1").type("Province").includeAllChildren(true).build()));

        // Stored campaign data: one site row (already in site-column shape, as persisted) and one plain
        // facility row (no hidden clientReferenceId) that must not appear on the Outside Household sheet.
        Map<String, Object> site = new HashMap<>();
        site.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY, "fac-ohp-1");
        site.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY, "OUTSIDE_HOUSEHOLD_PLACES");
        site.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, "SCHOOL");
        site.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY, "Central School OUTSIDE_HOUSEHOLD_PLACES");
        site.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY, "Permanent");
        site.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY, "Active");
        site.put(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, "V1");
        Map<String, Object> warehouse = new HashMap<>();
        warehouse.put(ProcessingConstants.FACILITY_USAGE_COLUMN_KEY, "Active");
        warehouse.put("HCM_ADMIN_CONSOLE_FACILITY_NAME", "Main Warehouse");
        warehouse.put(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, "V1");
        lenient().when(campaignService.searchCampaignDataByUniqueIdentifiers(anyList(), eq("facility"), isNull(),
                eq(CAMPAIGN_NUMBER), eq(TENANT), any()))
                .thenReturn(Arrays.asList(Map.of("data", site), Map.of("data", warehouse)));

        // Earlier campaigns' permanent sites, one paged search per active site type
        lenient().when(facilityService.fetchAllPermanentFacilities(eq(TENANT), eq("OUTSIDE_HOUSEHOLD_PLACES"), any()))
                .thenReturn(Arrays.asList(
                        // already listed from the stored site row (same clientReferenceId) -> skipped
                        permanentFacility("fac-ohp-1", "OUTSIDE_HOUSEHOLD_PLACES", "Central School OUTSIDE_HOUSEHOLD_PLACES", "V1", "SCHOOL"),
                        // on the second-lowest level - skipped unless the test raises carry-over to 2 levels
                        permanentFacility("fac-ohp-2", "OUTSIDE_HOUSEHOLD_PLACES", "District OUTSIDE_HOUSEHOLD_PLACES", "D1", "MARKET"),
                        // on the root boundary - never carried over regardless of level count
                        permanentFacility("fac-ohp-3", "OUTSIDE_HOUSEHOLD_PLACES", "Root OUTSIDE_HOUSEHOLD_PLACES", "P1", "OTHER")));
        lenient().when(facilityService.fetchAllPermanentFacilities(eq(TENANT), eq("TRANSIT_POST"), any()))
                .thenReturn(List.of(permanentFacility("fac-tp-9", "TRANSIT_POST", "Old Bus Stand", "V2", "BUS_STATION")));
        lenient().when(boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(any(), eq(CAMPAIGN_ID), eq(TENANT),
                eq(HIERARCHY), eq(2), any())).thenReturn(Set.of("V1", "V2"));
        lenient().when(boundaryUtil.getEnrichedBoundariesFromCampaign(any(), eq(CAMPAIGN_ID), eq(TENANT),
                eq(HIERARCHY), any())).thenReturn(Collections.singletonList(
                        Boundary.builder().code("P1").name("P1").type("Province").build()));
    }

    private void stubBoundaries() {
        BoundaryHierarchy hierarchy = new BoundaryHierarchy();
        List<BoundaryHierarchyChild> levels = new ArrayList<>();
        for (String type : Arrays.asList("Province", "District", "Village")) {
            BoundaryHierarchyChild child = new BoundaryHierarchyChild();
            child.setBoundaryType(type);
            levels.add(child);
        }
        hierarchy.setBoundaryHierarchy(levels);
        BoundaryHierarchyResponse hierarchyResponse = new BoundaryHierarchyResponse();
        hierarchyResponse.setBoundaryHierarchy(Collections.singletonList(hierarchy));
        lenient().when(boundaryService.fetchBoundaryHierarchy(any(), any(), any())).thenReturn(hierarchyResponse);
        lenient().when(boundaryService.fetchBoundaryRelationship(any(), any(), any()))
                .thenReturn(new BoundarySearchResponse());
        lenient().when(boundaryUtil.buildCodeToBoundaryMap(any())).thenReturn(new HashMap<>());
        lenient().when(boundaryUtil.processBoundariesWithEnrichment(any(), any(), any())).thenReturn(Arrays.asList(
                new BoundaryUtil.BoundaryRowData(Arrays.asList("P1", "D1", "V1"), "V1"),
                new BoundaryUtil.BoundaryRowData(Arrays.asList("P1", "D1", "V2"), "V2")));
    }

    private static Map<String, Object> permanentFacility(String id, String usage, String name, String locality,
                                                         String outsideHouseholdCategory) {
        Map<String, Object> facility = new HashMap<>();
        facility.put("id", id);
        facility.put("clientReferenceId", id);
        facility.put("usage", usage);
        facility.put("name", name);
        facility.put("isPermanent", true);
        facility.put("address", Map.of("locality", Map.of("code", locality)));
        if (outsideHouseholdCategory != null) {
            facility.put("additionalFields", Map.of("schema", "OutsideHousehold", "version", 1,
                    "fields", List.of(Map.of("key", "outsideHouseholdCategory", "value", outsideHouseholdCategory))));
        }
        return facility;
    }

    private static Map<String, Object> noClientReferenceIdFacility(String name, String locality) {
        Map<String, Object> facility = new HashMap<>();
        facility.put("id", "fac-no-crid");
        facility.put("usage", "TRANSIT_POST");
        facility.put("name", name);
        facility.put("isPermanent", true);
        facility.put("address", Map.of("locality", Map.of("code", locality)));
        return facility;
    }

    /** MDMS outside-household-microplan-ingestion record mirroring facility-microplan-ingestion. */
    private static Map<String, Object> outsideHouseholdSchema() {
        Map<String, Object> properties = new HashMap<>();
        List<Map<String, Object>> strings = new ArrayList<>(Arrays.asList(
                prop(ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY, 1, null, true),
                prop(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY, 2, null, false),
                prop(ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY, 4, null, false)));
        List<Map<String, Object>> enums = new ArrayList<>(Arrays.asList(
                prop(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, 3, SCHEMA_CATEGORIES, false),
                prop(ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY, 5, Arrays.asList("Temporary", "Permanent"), false),
                prop(ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY, 6, Arrays.asList("Active", "Inactive"), false)));
        properties.put("stringProperties", strings);
        properties.put("enumProperties", enums);
        return Map.of("data", Map.of("title", "outside-household-microplan-ingestion", "properties", properties));
    }

    private static Map<String, Object> prop(String name, int order, List<String> enumValues, boolean hidden) {
        Map<String, Object> prop = new HashMap<>();
        prop.put("name", name);
        prop.put("orderNumber", order);
        prop.put("isRequired", !hidden);
        if (enumValues != null) {
            prop.put("enum", enumValues);
        } else {
            prop.put("type", "string");
        }
        if (hidden) {
            prop.put("hideColumn", true);
        }
        return prop;
    }

    private static List<String> technicalHeaders(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        Row row = sheet.getRow(0);
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            headers.add(cell == null ? null : cell.getStringCellValue());
        }
        return headers;
    }

    private static int column(Sheet sheet, String technicalName) {
        return technicalHeaders(sheet).indexOf(technicalName);
    }

    private static String cell(Sheet sheet, int rowIdx, String technicalName) {
        Row row = sheet.getRow(rowIdx);
        assertNotNull(row, "Row " + rowIdx + " must exist");
        Cell c = row.getCell(column(sheet, technicalName));
        return c == null ? null : c.getStringCellValue();
    }

    /** Row index (>=2) whose hidden clientReferenceId matches, or -1 if no pre-filled row matches. */
    private static int rowOf(Sheet sheet, String clientReferenceId) {
        int col = column(sheet, ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY);
        for (int r = 2; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            Cell c = row == null ? null : row.getCell(col);
            if (c != null && clientReferenceId.equals(c.getStringCellValue())) {
                return r;
            }
        }
        return -1;
    }
}
