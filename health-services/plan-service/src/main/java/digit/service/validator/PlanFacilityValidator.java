package digit.service.validator;

import com.jayway.jsonpath.JsonPath;
import digit.repository.PlanConfigurationRepository;
import digit.repository.PlanFacilityRepository;
import digit.util.*;
import digit.web.models.*;
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
import static digit.config.ServiceConstants.*;
import java.util.*;
import java.util.stream.Collectors;
import digit.web.models.projectFactory.Boundary;

@Component
@Slf4j
public class PlanFacilityValidator {
    private PlanFacilityRepository planFacilityRepository;
    private PlanConfigurationRepository planConfigurationRepository;
    private CampaignUtil campaignUtil;
    private MultiStateInstanceUtil centralInstanceUtil;
    private MdmsUtil mdmsUtil;
    private FacilityUtil facilityUtil;
    private CommonUtil commonUtil;

    /**
     * This method validates the Plan Facility Create request.
     * It performs multiple validations such as plan configuration, facility existence,
     * and campaign-related validations.
     *
     * @param planFacilityRequest
     */
    public void validatePlanFacilityCreate(@Valid PlanFacilityRequest planFacilityRequest) {
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(planFacilityRequest.getPlanFacility().getTenantId());

        List<PlanConfiguration> planConfigurations = searchPlanConfigId(planFacilityRequest.getPlanFacility().getPlanConfigurationId(),rootTenantId);

        // Validate plan configuration existence
        validatePlanConfigurationExistence(planFacilityRequest);

        // Validate facility existence
        validateFacilityExistence(planFacilityRequest);

        //validate service boundaries and residing boundaries with campaign id
        validateCampaignDetails(planConfigurations.get(0).getCampaignId(),rootTenantId,planFacilityRequest);
    }

    public void validatePlanFacilityUpdate(PlanFacilityRequest planFacilityRequest) {
        //validate plan facility existence
        validatePlanFacilityExistence(planFacilityRequest);

        String rootTenantId = centralInstanceUtil.getStateLevelTenant(planFacilityRequest.getPlanFacility().getTenantId());
        List<PlanConfiguration> planConfigurations = searchPlanConfigId(planFacilityRequest.getPlanFacility().getPlanConfigurationId(), rootTenantId);
        if (planConfigurations == null || planConfigurations.isEmpty()) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }
        //validate service boundaries and residing boundaries with campaign id
        validateCampaignDetails(planConfigurations.get(0).getCampaignId(), rootTenantId, planFacilityRequest);
    }

    /**
     * This method validates campaign id and service boundaries against project factory
     *
     * @param campaignId          the campaign id corresponding to the plan config id provided in the request
     * @param rootTenantId        the tenant id provided in the request
     * @param planFacilityRequest the plan facility request provided
     */
    private void validateCampaignDetails(String campaignId, String rootTenantId, PlanFacilityRequest planFacilityRequest) {
        PlanFacility planFacility = planFacilityRequest.getPlanFacility();
        Object mdmsData = mdmsUtil.fetchMdmsData(planFacilityRequest.getRequestInfo(), rootTenantId);
        CampaignResponse campaignResponse = campaignUtil.fetchCampaignData(planFacilityRequest.getRequestInfo(), campaignId, rootTenantId);

        // Validate if campaign id exists
        validateCampaignId(campaignResponse);

        // validate hierarchy type for campaign
        String lowestHierarchy = validateHierarchyType(campaignResponse, mdmsData);

        // Collect all boundary code for the campaign
        CampaignDetail campaignDetail = campaignResponse.getCampaignDetails().get(0);
        Set<String> boundaryCodes = campaignDetail.getBoundaries().stream()
                .filter(boundary -> lowestHierarchy.equals(boundary.getType()))
                .map(Boundary::getCode)
                .collect(Collectors.toSet());

        //validate service boundaries
        validateServiceBoundaries(boundaryCodes, planFacility);

        //validate residing boundaries
        validateResidingBoundaries(boundaryCodes, planFacility);

    }

    /**
     * This method validates if residing boundaries exist in campaign details
     *
     * @param boundaryCodes
     * @param planFacility
     */
    private void validateResidingBoundaries(Set<String> boundaryCodes, PlanFacility planFacility) {
        String residingBoundary = planFacility.getResidingBoundary();
        if (residingBoundary != null && !boundaryCodes.contains(residingBoundary)) {
            throw new CustomException(INVALID_RESIDING_BOUNDARY_CODE, INVALID_RESIDING_BOUNDARY_MESSAGE);
        }
    }

    /**
     * This method validates if service boundaries exist in campaign details
     *
     * @param boundaryCodes
     * @param planFacility
     */
    private void validateServiceBoundaries(Set<String> boundaryCodes, PlanFacility planFacility) {
        planFacility.getServiceBoundaries().stream()
                .forEach(service -> {
                    if (!boundaryCodes.contains(service)) {
                        throw new CustomException(INVALID_SERVICE_BOUNDARY_CODE, INVALID_SERVICE_BOUNDARY_MESSAGE);
                    }
                });
    }

    /**
     * This method validates if the hierarchy type provided in the request exists
     *
     * @param campaignResponse
     * @param mdmsData
     */
    private String validateHierarchyType(CampaignResponse campaignResponse, Object mdmsData) {
        String hierarchyType = campaignResponse.getCampaignDetails().get(0).getHierarchyType();
        final String jsonPathForHierarchy = JSON_ROOT_PATH + MDMS_HCM_ADMIN_CONSOLE + DOT_SEPARATOR + MDMS_MASTER_HIERARCHY_CONFIG + "[*]";

        List<Map<String, Object>> hierarchyConfigList = null;
        System.out.println("Jsonpath for hierarchy config -> " + jsonPathForHierarchy);
        try {
            hierarchyConfigList = JsonPath.read(mdmsData, jsonPathForHierarchy);
        } catch (Exception e) {
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }

        for (Map<String, Object> hierarchyConfig : hierarchyConfigList) {
            if (hierarchyType.equals(hierarchyConfig.get(MDMS_MASTER_HIERARCHY))) {
                return (String) hierarchyConfig.get(MDMS_MASTER_LOWEST_HIERARCHY);
            }
        }
        // Throw exception if no matching hierarchy is found
        throw new CustomException(HIERARCHY_NOT_FOUND_IN_MDMS_CODE, HIERARCHY_NOT_FOUND_IN_MDMS_MESSAGE);
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
     * @param planFacilityRequest
     */
    private void validatePlanFacilityExistence(PlanFacilityRequest planFacilityRequest) {
        // If plan facility id provided is invalid, throw an exception
        if (CollectionUtils.isEmpty(planFacilityRepository.search(PlanFacilitySearchCriteria.builder()
                .ids(Collections.singleton(planFacilityRequest.getPlanFacility().getId()))
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

    /**
     * Validates if the facility with the provided ID exists in the system.
     *
     * @param planFacilityRequest
     */
    private void validateFacilityExistence(PlanFacilityRequest planFacilityRequest) {
        String facilityId = planFacilityRequest.getPlanFacility().getFacilityId();
        String tenantId = planFacilityRequest.getPlanFacility().getTenantId();
        RequestInfo requestInfo = planFacilityRequest.getRequestInfo();

        FacilityResponse facilityResponse = facilityUtil.fetchFacilityData(requestInfo, facilityId, tenantId);

        // Use ObjectUtils and CollectionUtils to handle null or empty checks
        if (ObjectUtils.isEmpty(facilityResponse) || CollectionUtils.isEmpty(facilityResponse.getFacilities())) {
            throw new CustomException("FACILITY_NOT_FOUND", "Facility with ID " + facilityId + " not found in the system.");
        }
    }


    /**
     * Validates if the plan configuration ID provided in the request exists.
     *
     * @param request The request object containing the plan configuration ID.
     */
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
