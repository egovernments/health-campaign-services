package digit.service.validator;

import digit.repository.PlanConfigurationRepository;
import digit.util.CampaignUtil;
import digit.util.CommonUtil;
import digit.util.MdmsUtil;
import digit.util.MdmsV2Util;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.mdmsV2.MdmsCriteriaV2;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.junit.jupiter.api.BeforeEach;
import digit.web.models.mdmsV2.Mdms;
import digit.web.models.mdmsV2.MdmsCriteriaReqV2;
import digit.web.models.projectFactory.CampaignDetail;
import digit.web.models.projectFactory.CampaignResponse;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class PlanConfigurationValidatorTest {

    private MdmsUtil mdmsUtil;
    private MdmsV2Util mdmsV2Util;
    private PlanConfigurationRepository planConfigRepository;
    private CommonUtil commonUtil;
    private MultiStateInstanceUtil centralInstanceUtil;
    private CampaignUtil campaignUtil;
    private PlanConfigurationValidator validator;

    @BeforeEach
    void setUp() {
        mdmsUtil = mock(MdmsUtil.class);
        mdmsV2Util = mock(MdmsV2Util.class);
        planConfigRepository = mock(PlanConfigurationRepository.class);
        commonUtil = mock(CommonUtil.class);
        centralInstanceUtil = mock(MultiStateInstanceUtil.class);
        campaignUtil = mock(CampaignUtil.class);
        validator = new PlanConfigurationValidator(mdmsUtil, mdmsV2Util, planConfigRepository, commonUtil, centralInstanceUtil, campaignUtil);
    }

    @Test
    void testValidateCreate_CallsDependenciesCorrectly() {
        // Use real objects for request and planConfig
        PlanConfiguration planConfig = new PlanConfiguration();
        planConfig.setTenantId("tenant");
        planConfig.setCampaignId("cid");
        planConfig.setAssumptions(Collections.emptyList());
        planConfig.setFiles(Collections.emptyList());

        PlanConfigurationRequest request = new PlanConfigurationRequest();
        request.setPlanConfiguration(planConfig);

        // Mock MDMS data with expected structure
        Map<String, Object> microplanNamingRegex = new HashMap<>();
        microplanNamingRegex.put("data", "SOME_REGEX");
        Map<String, Object> mdmsData = new HashMap<>();
        mdmsData.put("hcm-microplanning", Map.of("MicroplanNamingRegex", List.of(microplanNamingRegex)));

        MdmsCriteriaV2 mdmsCriteriaV2 = mock(MdmsCriteriaV2.class);
        CampaignResponse campaignResponse = mock(CampaignResponse.class);

        when(centralInstanceUtil.getStateLevelTenant("tenant")).thenReturn("rootTenant");
        when(mdmsUtil.fetchMdmsData(any(), eq("rootTenant"))).thenReturn(mdmsData);
        when(mdmsV2Util.getMdmsCriteriaV2(any(), any())).thenReturn(mdmsCriteriaV2);
        when(mdmsV2Util.fetchMdmsV2Data(any(MdmsCriteriaReqV2.class))).thenReturn(Collections.emptyList());
        when(campaignUtil.fetchCampaignData(any(), eq("cid"), eq("rootTenant"))).thenReturn(campaignResponse);
        when(planConfigRepository.search(any())).thenReturn(Collections.emptyList());
        when(campaignResponse.getCampaignDetails()).thenReturn(List.of(mock(CampaignDetail.class)));

        // Spy the validator and stub out all validation methods except the external service calls
        PlanConfigurationValidator spyValidator = spy(validator);
        doNothing().when(spyValidator).validateDuplicateRecord(any());
        doNothing().when(spyValidator).validateCampaignId(any());
        doNothing().when(spyValidator).validateAssumptionKeyAgainstMDMS(any(), any());
        doNothing().when(spyValidator).validateAssumptionUniqueness(any());
        doNothing().when(spyValidator).validateTemplateIdentifierAgainstMDMS(any(), any());
        doNothing().when(spyValidator).validateOperations(any(), any());
        doNothing().when(spyValidator).validatePlanConfigName(any(), any());
        doNothing().when(commonUtil).validateUserInfo(any());
        doNothing().when(spyValidator).validateVehicleIdsFromAdditionalDetailsAgainstMDMS(any(), any());

        // Act
        spyValidator.validateCreate(request);

        verify(mdmsUtil).fetchMdmsData(any(), eq("rootTenant"));
        verify(mdmsV2Util).getMdmsCriteriaV2(eq("rootTenant"), any());
        verify(mdmsV2Util).fetchMdmsV2Data(any(MdmsCriteriaReqV2.class));
        verify(campaignUtil).fetchCampaignData(any(), eq("cid"), eq("rootTenant"));
    }
}