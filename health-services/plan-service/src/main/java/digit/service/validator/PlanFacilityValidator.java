package digit.service.validator;

import com.jayway.jsonpath.JsonPath;
import digit.repository.PlanConfigurationRepository;
import digit.util.*;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationSearchCriteria;
import digit.web.models.PlanFacility;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.facility.FacilityResponse;
import digit.web.models.mdmsV2.Mdms;
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
import java.util.Map;
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

    /**
     * Validates campaign details, including hierarchy type and boundaries,
     * ensuring the plan facility complies with campaign rules.
     *
     * @param campaignId   The ID of the campaign.
     * @param rootTenantId The root tenant ID.
     * @param body         The request body containing plan facility data.
     */
    private void validateCampaignDetails(String campaignId,String rootTenantId, PlanFacilityRequest body)
    {
        PlanFacility planFacility=body.getPlanFacility();
        Object mdmsData = mdmsUtil.fetchMdmsData(body.getRequestInfo(), rootTenantId);
        CampaignResponse campaignResponse=campaignUtil.fetchCampaignData(body.getRequestInfo(),campaignId,rootTenantId);
        // Validate if campaign id exists
        validateCampaignId(campaignResponse);

        // validate hierarchy type for campaign
        String lowestHierarchy = validateHierarchyType(campaignResponse, mdmsData);

        // Collect all boundary code for the campaign
        CampaignDetail campaignDetail = campaignResponse.getCampaignDetails().get(0);
        Set<String> boundaryCode = campaignDetail.getBoundaries().stream()
                .filter(boundary -> lowestHierarchy.equals(boundary.getType()))
                .map(Boundary::getCode)
                .collect(Collectors.toSet());

        //validate service boundaries
        validateServiceBoundaries(boundaryCode, planFacility);

        //validate residing boundaries
        validateResidingBoundaries(boundaryCode, planFacility);
    }


    /**
     * This method validates if residing boundaries exist in campaign details
     *
     * @param boundaryCode
     * @param planFacility
     */
    private void validateResidingBoundaries(Set<String> boundaryCode, PlanFacility planFacility) {
        String residingBoundary = planFacility.getResidingBoundary();
        if (residingBoundary != null && !boundaryCode.contains(residingBoundary)) {
            throw new CustomException(INVALID_RESIDING_BOUNDARY_CODE, INVALID_RESIDING_BOUNDARY_MESSAGE);
        }
    }

    /**
     * This method validates if the hierarchy type provided in the request exists
     *
     * @param campaignResponse
     * @param mdmsData
     */
    private String validateHierarchyType(CampaignResponse campaignResponse, Object mdmsData) {
        String hierarchyType = campaignResponse.getCampaignDetails().get(0).getHierarchyType();
        final String jsonPathForHierarchy = "$.HCM-ADMIN-CONSOLE.hierarchyConfig[*]";

        List<Map<String, Object>> hierarchyConfigList = null;
        System.out.println("Jsonpath for hierarchy config -> " + jsonPathForHierarchy);
        try {
            hierarchyConfigList = JsonPath.read(mdmsData, jsonPathForHierarchy);
        } catch (Exception e) {
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }

        for (Map<String, Object> hierarchyConfig : hierarchyConfigList) {
            if (hierarchyType.equals(hierarchyConfig.get("hierarchy"))) {
                return (String) hierarchyConfig.get("lowestHierarchy");
            }
        }
        // Throw exception if no matching hierarchy is found
        throw new CustomException(HIERARCHY_NOT_FOUND_IN_MDMS_CODE, HIERARCHY_NOT_FOUND_IN_MDMS_MESSAGE);
    }

    /**
     * This method validates if the campaign response contains campaign details.
     *
     * @param campaignResponse
     */
    private void validateCampaignId(CampaignResponse campaignResponse) {

        if (CollectionUtils.isEmpty(campaignResponse.getCampaignDetails())) {
            throw new CustomException(NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE, NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE);
        }
    }

    /**
     * This method validates if service boundaries exist in campaign details
     *
     * @param boundaryCode
     * @param planFacility
     */
    private void validateServiceBoundaries(Set<String> boundaryCode, PlanFacility planFacility) {
        planFacility.getServiceBoundaries().stream()
                .forEach(service -> {
                    if (!boundaryCode.contains(service)) {
                        throw new CustomException(INVALID_SERVICE_BOUNDARY_CODE, INVALID_SERVICE_BOUNDARY_MESSAGE);
                    }
                });
    }

    /**
     * Searches for a PlanConfiguration by ID and tenant ID.
     *
     * @param planConfigurationId The plan configuration ID.
     * @param tenantId            The tenant ID.
     * @return The list of plan configurations matching the search criteria.
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
     * @param facilityId The facility ID to validate.
     * @param tenantId   The tenant ID.
     * @param requestInfo The request information for the API call.
     */
    private void validateFacilityExistence(String facilityId, String tenantId, RequestInfo requestInfo) {
        FacilityResponse facilityResponse = facilityUtil.fetchFacilityData(requestInfo, facilityId, tenantId);

        // Check if the facility response is null or if the facilities list is null or empty
        if (facilityResponse == null || facilityResponse.getFacilities() == null || facilityResponse.getFacilities().isEmpty()) {
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
