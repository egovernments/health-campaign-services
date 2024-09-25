package digit.service.validator;

import digit.repository.PlanConfigurationRepository;
import digit.util.CampaignUtil;
import digit.util.CommonUtil;
import digit.util.FacilityUtil;
import digit.util.MdmsUtil;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationSearchCriteria;
import digit.web.models.PlanFacility;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.facility.FacilityResponse;
import digit.web.models.projectFactory.Boundary;
import digit.web.models.projectFactory.CampaignDetail;
import digit.web.models.projectFactory.CampaignResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;

@Component
@Slf4j
public class PlanFacilityValidator {

    private MdmsUtil mdmsUtil;

    private MultiStateInstanceUtil centralInstanceUtil;

    private PlanConfigurationRepository planConfigurationRepository;

    private FacilityUtil facilityUtil;

    private CommonUtil commonUtil;

    private CampaignUtil campaignUtil;

    public PlanFacilityValidator(MdmsUtil mdmsUtil, MultiStateInstanceUtil centralInstanceUtil, PlanConfigurationRepository planConfigurationRepository, FacilityUtil facilityUtil, CommonUtil commonUtil, CampaignUtil campaignUtil) {
        this.mdmsUtil = mdmsUtil;
        this.centralInstanceUtil = centralInstanceUtil;
        this.planConfigurationRepository = planConfigurationRepository;
        this.facilityUtil = facilityUtil;
        this.commonUtil = commonUtil;
        this.campaignUtil = campaignUtil;
    }

    public void validatePlanFacilityCreate(@Valid PlanFacilityRequest planFacilityRequest) {
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(planFacilityRequest.getPlanFacility().getTenantId());

        List<PlanConfiguration> planConfigurations = searchPlanConfigId(planFacilityRequest.getPlanFacility().getPlanConfigurationId(),rootTenantId);
        if(planConfigurations.isEmpty())
        {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE,INVALID_PLAN_CONFIG_ID_MESSAGE);
        }

        // Validate plan configuration existence
        validatePlanConfigurationExistence(planFacilityRequest);

        // Validate facility existence
        validateFacilityExistence(planFacilityRequest.getPlanFacility().getFacilityId(),
                planFacilityRequest.getPlanFacility().getTenantId(),
                planFacilityRequest.getRequestInfo());

        //validate service boundaries and residing boundaries with campaign id
        validateCampaignDetails(planConfigurations.get(0).getCampaignId(),rootTenantId,planFacilityRequest);
    }



    private void validateCampaignDetails(String campaignId,String rootTenantId, PlanFacilityRequest body)
    {
        PlanFacility planFacility=body.getPlanFacility();
        CampaignResponse campaignResponse=campaignUtil.fetchCampaignData(body.getRequestInfo(),campaignId,rootTenantId);
        // Validate if campaign id exists
        validateCampaignId(campaignResponse);

        //validate service boundaries
        validateServiceBoundaries(campaignResponse.getCampaignDetails().get(0),planFacility);

    }

    private void validateCampaignId(CampaignResponse campaignResponse) {

        if (CollectionUtils.isEmpty(campaignResponse.getCampaignDetails())) {
            throw new CustomException(NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE, NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE);
        }
    }

    private void validateServiceBoundaries(CampaignDetail campaignDetail, PlanFacility planFacility)
    {
        // Collect all boundary code for the campaign
        Set<String> boundaryCode = campaignDetail.getBoundaries().stream()
                .map(Boundary::getCode)
                .collect(Collectors.toSet());

        planFacility.getServiceBoundaries().stream()
                .forEach(service -> {
                    if (!boundaryCode.contains(service)) {
                        throw new CustomException(INVALID_SERVICE_BOUNDARY_CODE, INVALID_SERVICE_BOUNDARY_MESSAGE);
                    }
                });
    }

    public List<PlanConfiguration> searchPlanConfigId(String planConfigurationId, String tenantId) {
        List<PlanConfiguration> planConfigurations = planConfigurationRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(planConfigurationId)
                .tenantId(tenantId)
                .build());

        return planConfigurations;
    }



    private void validateFacilityExistence(String facilityId, String tenantId, RequestInfo requestInfo) {
        FacilityResponse facilityResponse = facilityUtil.fetchFacilityData(requestInfo, facilityId, tenantId);

        // Check if the facility response is null or if the facilities list is null or empty
        if (facilityResponse == null || facilityResponse.getFacilities() == null || facilityResponse.getFacilities().isEmpty()) {
            throw new CustomException("FACILITY_NOT_FOUND", "Facility with ID " + facilityId + " not found in the system.");
        }
    }

    private void validatePlanConfigurationExistence(PlanFacilityRequest request) {
        // If plan configuration id provided is invalid, throw an exception
        if(!ObjectUtils.isEmpty(request.getPlanFacility().getPlanConfigurationId()) && CollectionUtils.isEmpty(
                planConfigurationRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(request.getPlanFacility().getPlanConfigurationId())
                .tenantId(request.getPlanFacility().getTenantId())
                .build()))) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }
    }
}
