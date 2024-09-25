package digit.service.validator;

import digit.repository.PlanConfigurationRepository;
import digit.repository.PlanFacilityRepository;
import digit.util.CampaignUtil;
import digit.web.models.*;
import digit.web.models.projectFactory.CampaignDetail;
import digit.web.models.projectFactory.CampaignResponse;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import digit.web.models.projectFactory.Boundary;

import static digit.config.ServiceConstants.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.INVALID_PLAN_CONFIG_ID_CODE;
import static digit.config.ServiceConstants.INVALID_PLAN_CONFIG_ID_MESSAGE;

@Component
@Slf4j
public class PlanFacilityValidator {
    private PlanFacilityRepository planFacilityRepository;
    private PlanConfigurationRepository planConfigurationRepository;
    private CampaignUtil campaignUtil;
    private MultiStateInstanceUtil centralInstanceUtil;

    public PlanFacilityValidator(PlanFacilityRepository planFacilityRepository, PlanConfigurationRepository planConfigurationRepository,CampaignUtil campaignUtil,MultiStateInstanceUtil centralInstanceUtil) {
        this.planFacilityRepository = planFacilityRepository;
        this.planConfigurationRepository = planConfigurationRepository;
        this.campaignUtil=campaignUtil;
        this.centralInstanceUtil = centralInstanceUtil;
    }

    public void validatePlanFacilityUpdate(PlanFacilityRequest body)
    {
        String rootTenantId=centralInstanceUtil.getStateLevelTenant(body.getPlanFacility().getTenantId());
        List<PlanConfiguration> planConfigurations = searchPlanConfigId(body.getPlanFacility().getPlanConfigurationId(),rootTenantId);
        if(planConfigurations.isEmpty())
        {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE,INVALID_PLAN_CONFIG_ID_MESSAGE);
        }

        //validate plan facility existence
        validatePlanFacilityExistence(body);

        //validate service boundaries and residing boundaries with campaign id
        validateCampaignDetails(planConfigurations.get(0).getCampaignId(),rootTenantId,body);
    }

    /**
     * This method validates campaign id and service boundaries against project factory
     *
     * @param campaignId    the campaign id corresponding to the plan config id provided in the request
     * @param rootTenantId  the tenant id provided in the request
     * @param body the plan facility request provided
     */
    private void validateCampaignDetails(String campaignId,String rootTenantId, PlanFacilityRequest body)
    {
        PlanFacility planFacility=body.getPlanFacility();
        CampaignResponse campaignResponse=campaignUtil.fetchCampaignData(body.getRequestInfo(),campaignId,rootTenantId);

        // Validate if campaign id exists
        validateCampaignId(campaignResponse);

        //validate service boundaries
        validateServiceBoundaries(campaignResponse.getCampaignDetails().get(0),planFacility);

        //validate residing boundaries
        validateResidingBoundaries(campaignResponse.getCampaignDetails().get(0),planFacility);
        
    }

    /**
     * This method validates if residing boundaries exist in campaign details
     *
     * @param campaignDetail the campaign details for the corresponding campaign id
     * @param planFacility
     */
    private void validateResidingBoundaries(CampaignDetail campaignDetail, PlanFacility planFacility)
    {
        // Collect all boundary code for the campaign
        Set<String> boundaryCode = campaignDetail.getBoundaries().stream()
                .map(Boundary::getCode)
                .collect(Collectors.toSet());

        String residingBoundary=planFacility.getResidingBoundary();
        if (residingBoundary != null && !boundaryCode.contains(residingBoundary)) {
            throw new CustomException(INVALID_RESIDING_BOUNDARY_CODE, INVALID_RESIDING_BOUNDARY_MESSAGE);
        }
    }

    /**
     * This method validates if service boundaries exist in campaign details
     *
     * @param campaignDetail the campaign details for the corresponding campaign id
     * @param planFacility
     */
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

    /**
     * This method validates if the campaign id provided in the request exists
     *
     * @param campaignResponse The campaign details response from project factory
     */
    private void validateCampaignId(CampaignResponse campaignResponse) {

        if (CollectionUtils.isEmpty(campaignResponse.getCampaignDetails())) {
            throw new CustomException(NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE, NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE);
        }
    }

    /**
     * This method validates if the plan facility id provided in the update request exists
     *
     * @param body
     */
    private void validatePlanFacilityExistence(PlanFacilityRequest body) {
        // If plan facility id provided is invalid, throw an exception
        if (CollectionUtils.isEmpty(planFacilityRepository.search(PlanFacilitySearchCriteria.builder()
                .ids(Collections.singleton(body.getPlanFacility().getId()))
                .build()))) {
            throw new CustomException(INVALID_PLAN_FACILITY_ID_CODE, INVALID_PLAN_FACILITY_ID_MESSAGE);
        }
    }

    /**
     * Searches the plan config based on the plan config id provided
     *
     * @param planConfigurationId
     * @param tenantId
     * @return
     */
    public List<PlanConfiguration> searchPlanConfigId(String planConfigurationId, String tenantId) {
        List<PlanConfiguration> planConfigurations = planConfigurationRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(planConfigurationId)
                .tenantId(tenantId)
                .build());

        return planConfigurations;
    }

}
