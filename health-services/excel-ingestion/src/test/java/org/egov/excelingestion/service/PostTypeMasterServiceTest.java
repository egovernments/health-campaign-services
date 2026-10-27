package org.egov.excelingestion.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * PostTypeMasterService reads which facility usages are posts from the MDMS master
 * HCM.OUTSIDE_HOUSEHOLD_POST_TYPES (active rows + isPermanentByDefault, missing field = false), and decides
 * whether a delivery-method-gated sheet (e.g. posts) applies to a campaign.
 *
 * <p>Critical rule (regression guard): an MDMS reply with no active rows is read as NO post types (empty,
 * not an error) - a tenant/environment where this master is not yet seeded must behave exactly as before
 * posts existed. Only an actual failed MDMS call is reported as MDMS_SERVICE_ERROR.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class PostTypeMasterServiceTest {

    private static final String TENANT = "mz.citya";
    private static final String STATE_TENANT = "mz";
    private static final String CAMPAIGN_ID = "campaign-1";

    @Mock private MDMSService mdmsService;
    @Mock private CampaignService campaignService;

    private PostTypeMasterService service;

    @BeforeEach
    void setUp() {
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setPostsCampaignTypes(List.of("POLIO"));
        service = new PostTypeMasterService(mdmsService, campaignService, new CustomExceptionHandler(), config);
    }

    @Test
    void readsOnlyActiveCodesAtTheStateTenant_withTheirDefaultPermanence() {
        mockMaster(masterRow("CLF", true, true), masterRow("TRANSIT_POST", true, false),
                masterRow("RETIRED_TYPE", false, true));

        Map<String, Boolean> postTypes = service.getPostTypes(new RequestInfo(), TENANT);

        assertEquals(Map.of("CLF", true, "TRANSIT_POST", false), postTypes);
        assertTrue(postTypes.containsKey(PostTypeMasterService.normalize("clf")));
        assertFalse(postTypes.containsKey("RETIRED_TYPE"), "inactive rows must be ignored");
    }

    @Test
    void categoriesPerActiveTypeComeFromTheMaster_normalized_inOrder() {
        Map<String, Object> ohp = masterRow("OUTSIDE_HOUSEHOLD_PLACES", true, true);
        ((Map<String, Object>) ohp.get("data")).put(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPE_CATEGORIES_FIELD,
                List.of("SCHOOL", " market ", "OTHER"));
        Map<String, Object> transit = masterRow("TRANSIT_POST", true, false); // no categories list -> any category
        Map<String, Object> retired = masterRow("RETIRED_TYPE", false, true);
        ((Map<String, Object>) retired.get("data")).put(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPE_CATEGORIES_FIELD,
                List.of("SCHOOL"));
        mockMaster(ohp, transit, retired);

        Map<String, List<String>> categories = service.getCategoriesByPostType(new RequestInfo(), TENANT);

        assertEquals(List.of("SCHOOL", "MARKET", "OTHER"), categories.get("OUTSIDE_HOUSEHOLD_PLACES"));
        assertEquals(List.of(), categories.get("TRANSIT_POST"));
        assertFalse(categories.containsKey("RETIRED_TYPE"), "inactive rows must be ignored");
    }

    @Test
    void missingIsPermanentByDefaultFieldCountsAsFalse() {
        mockMaster(masterRowWithoutDefaultField("CLF", true));

        assertEquals(Map.of("CLF", false), service.getPostTypes(new RequestInfo(), TENANT));
    }

    @Test
    void failedMdmsCall_throwsMdmsServiceError() {
        when(mdmsService.searchMDMS(any(), eq(STATE_TENANT),
                eq(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPES_SCHEMA_CODE), isNull(), anyInt(), eq(0)))
                .thenThrow(new RuntimeException("MDMS down"));

        CustomException e = assertThrows(CustomException.class, () -> service.getPostTypes(new RequestInfo(), TENANT));

        assertEquals(ErrorConstants.MDMS_SERVICE_ERROR, e.getCode());
    }

    @Test
    void isSheetApplicable_otherSheets_alwaysTrue() {
        assertTrue(service.isSheetApplicable(null, null, null, TENANT, new RequestInfo()));
        assertTrue(service.isSheetApplicable("HCM_ADMIN_CONSOLE_FACILITIES_LIST", CAMPAIGN_ID, null, TENANT, new RequestInfo()));
    }

    @Test
    void isSheetApplicable_fromCycleData_andMasterHasActiveCodes_true() {
        mockMaster(masterRow("CLF", true, true));
        when(campaignService.searchCampaignById(eq(CAMPAIGN_ID), eq(TENANT), any()))
                .thenReturn(campaignWithCycleDataDeliveryMethods("OUTSIDE_HOUSEHOLD"));

        assertTrue(service.isSheetApplicable(ProcessingConstants.POSTS_SHEET_NAME_KEY, CAMPAIGN_ID, null, TENANT, new RequestInfo()));
    }

    @Test
    void isSheetApplicable_campaignDoesNotUseTheMethod_false() {
        lenient().when(mdmsService.searchMDMS(any(), eq(STATE_TENANT),
                eq(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPES_SCHEMA_CODE), isNull(), anyInt(), eq(0)))
                .thenReturn(List.of(masterRow("CLF", true, true)));
        when(campaignService.searchCampaignById(eq(CAMPAIGN_ID), eq(TENANT), any()))
                .thenReturn(campaignWithCycleDataDeliveryMethods("HOUSEHOLDSTRATEGY"));

        assertFalse(service.isSheetApplicable(ProcessingConstants.POSTS_SHEET_NAME_KEY, CAMPAIGN_ID, null, TENANT, new RequestInfo()));
    }

    @Test
    void isSheetApplicable_otherCampaignType_falseEvenIfCampaignUsesTheMethod() {
        mockMaster(masterRow("CLF", true, true));
        CampaignSearchResponse.CampaignDetail smc = campaignWithCycleDataDeliveryMethods("OUTSIDEHOUSEHOLD");
        smc.setProjectType("SMC");
        when(campaignService.searchCampaignById(eq(CAMPAIGN_ID), eq(TENANT), any())).thenReturn(smc);

        assertFalse(service.isSheetApplicable(ProcessingConstants.POSTS_SHEET_NAME_KEY, CAMPAIGN_ID, null, TENANT, new RequestInfo()));
    }

    @Test
    void isSheetApplicable_masterEmpty_falseEvenIfCampaignUsesTheMethod() {
        mockMaster();
        when(campaignService.searchCampaignById(eq(CAMPAIGN_ID), eq(TENANT), any()))
                .thenReturn(campaignWithCycleDataDeliveryMethods("OUTSIDEHOUSEHOLD"));

        assertFalse(service.isSheetApplicable(ProcessingConstants.POSTS_SHEET_NAME_KEY, CAMPAIGN_ID, null, TENANT, new RequestInfo()));
    }

    @Test
    void isSheetApplicable_blankReferenceId_orAttendanceRegister_false() {
        assertFalse(service.isSheetApplicable(ProcessingConstants.POSTS_SHEET_NAME_KEY, "", null, TENANT, new RequestInfo()));
        assertFalse(service.isSheetApplicable(ProcessingConstants.POSTS_SHEET_NAME_KEY, CAMPAIGN_ID,
                ProcessingConstants.REFERENCE_TYPE_ATTENDANCE_REGISTER, TENANT, new RequestInfo()));
    }

    private void mockMaster(Map<String, Object>... rows) {
        lenient().when(mdmsService.searchMDMS(any(), eq(STATE_TENANT),
                eq(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPES_SCHEMA_CODE), isNull(), anyInt(), eq(0)))
                .thenReturn(List.of(rows));
    }

    private static CampaignSearchResponse.CampaignDetail campaignWithCycleDataDeliveryMethods(String... codes) {
        Map<String, Object> cycleData = Map.of("deliveryMethods", List.of(codes));
        CampaignSearchResponse.AdditionalDetails details =
                CampaignSearchResponse.AdditionalDetails.builder().cycleData(cycleData).build();
        return CampaignSearchResponse.CampaignDetail.builder().projectType("POLIO").additionalDetails(details).build();
    }

    private static Map<String, Object> masterRow(String code, boolean active, boolean isPermanentByDefault) {
        Map<String, Object> data = new HashMap<>();
        data.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPE_CODE_FIELD, code);
        data.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPE_ACTIVE_FIELD, active);
        data.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPE_IS_PERMANENT_BY_DEFAULT_FIELD, isPermanentByDefault);
        return Map.of("data", data);
    }

    private static Map<String, Object> masterRowWithoutDefaultField(String code, boolean active) {
        Map<String, Object> data = new HashMap<>();
        data.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPE_CODE_FIELD, code);
        data.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPE_ACTIVE_FIELD, active);
        return Map.of("data", data);
    }
}
