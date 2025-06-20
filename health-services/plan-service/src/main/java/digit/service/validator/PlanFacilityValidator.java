package digit.service.validator;

import com.jayway.jsonpath.JsonPath;
import digit.repository.PlanConfigurationRepository;
import digit.repository.PlanFacilityRepository;
import digit.service.enrichment.PlanFacilityEnricher;
import digit.util.*;
import digit.web.models.*;
import digit.web.models.facility.Facility;
import digit.web.models.facility.FacilityResponse;
import digit.web.models.projectFactory.CampaignDetail;
import digit.web.models.projectFactory.CampaignResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import static digit.config.ServiceConstants.*;

import java.math.BigDecimal;
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
    private PlanFacilityEnricher enrichment;

    public PlanFacilityValidator(PlanFacilityRepository planFacilityRepository, PlanConfigurationRepository planConfigurationRepository, CampaignUtil campaignUtil, MultiStateInstanceUtil centralInstanceUtil, MdmsUtil mdmsUtil, FacilityUtil facilityUtil, CommonUtil commonUtil, PlanFacilityEnricher enrichment) {
        this.planFacilityRepository = planFacilityRepository;
        this.planConfigurationRepository = planConfigurationRepository;
        this.campaignUtil = campaignUtil;
        this.centralInstanceUtil = centralInstanceUtil;
        this.mdmsUtil = mdmsUtil;
        this.facilityUtil = facilityUtil;
        this.commonUtil = commonUtil;
        this.enrichment = enrichment;
    }

    /**
     * This method validates the Plan Facility Create request.
     * It performs multiple validations such as plan configuration, facility existence,
     * and campaign-related validations.
     *
     * @param planFacilityRequest
     */
    public void validatePlanFacilityCreate(@Valid PlanFacilityRequest planFacilityRequest) {
        // Retrieve the root-level tenant ID (state-level) based on the facility's tenant ID
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(planFacilityRequest.getPlanFacility().getTenantId());

        // Validate duplicate records for plan facility
        validateDuplicateRecords(planFacilityRequest);

        // Validate PlanConfiguration Existence and fetch the plan configuration details using the PlanConfigurationId
        List<PlanConfiguration> planConfigurations = fetchPlanConfigurationById(planFacilityRequest.getPlanFacility().getPlanConfigurationId(), rootTenantId);

        // Validate facility existence
        validateFacilityExistence(planFacilityRequest);

        // Validate service boundaries and residing boundaries with campaign id
        validateCampaignDetails(planConfigurations.get(0).getCampaignId(), rootTenantId, planFacilityRequest);
    }

    /**
     * Validates if plan facility linkage for the provided planConfiguration id and facility id already exists
     *
     * @param planFacilityRequest The plan facility linkage create request
     */
    private void validateDuplicateRecords(@Valid PlanFacilityRequest planFacilityRequest) {
        PlanFacility planFacility = planFacilityRequest.getPlanFacility();

        PlanFacilitySearchCriteria searchCriteria = PlanFacilitySearchCriteria.builder().planConfigurationId(planFacility.getPlanConfigurationId()).facilityId(planFacility.getFacilityId()).build();

        List<PlanFacility> planFacilityList = planFacilityRepository.search(searchCriteria);

        if (!CollectionUtils.isEmpty(planFacilityList)) {
            throw new CustomException(PLAN_FACILITY_LINKAGE_ALREADY_EXISTS_CODE, PLAN_FACILITY_LINKAGE_ALREADY_EXISTS_MESSAGE);
        }
    }

    /**
     * This method validates the Plan Facility Update request.
     * It performs multiple validations such as plan facility existence
     * and campaign-related validations.
     *
     * @param planFacilityRequest
     */
    public void validatePlanFacilityUpdate(PlanFacilityRequest planFacilityRequest) {
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(planFacilityRequest.getPlanFacility().getTenantId());

        //validate plan facility existence
        validatePlanFacilityExistence(planFacilityRequest);

        List<PlanConfiguration> planConfigurations = fetchPlanConfigurationById(planFacilityRequest.getPlanFacility().getPlanConfigurationId(), rootTenantId);

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

        // Validate hierarchy type for campaign
        Map<String, String> hierarchyMap = commonUtil.getMicroplanHierarchy(mdmsData);
        String lowestHierarchy = hierarchyMap.get(LOWEST_HIERARCHY_FIELD_FOR_MICROPLAN);

        // Collect all boundary code for the campaign
        Set<String> lowestHierarchyBCodes = fetchBoundaryCodes(campaignResponse.getCampaignDetails().get(0), lowestHierarchy);
        Set<String> allBoundaryCodes = fetchAllBoundaryCodes(campaignResponse.getCampaignDetails().get(0));

        // Validate residing boundaries against boundary codes across all hierarchy levels,
        // as a facility's residing boundary may correspond to any jurisdiction level.
        validateResidingBoundaries(allBoundaryCodes, planFacility);

        // Validate service boundaries against the lowest hierarchy boundary codes,
        // as a facility can only be mapped to boundaries at the lowest hierarchy level.
        validateServiceBoundaries(lowestHierarchyBCodes, planFacility);

        //Enrich jurisdiction mapping and boundary ancestral path
        enrichment.enrichJurisdictionMapping(planFacilityRequest, campaignResponse.getCampaignDetails().get(0).getHierarchyType());
    }

    /**
     * This method returns a set of all boundary codes for the given campaign.
     *
     * @param campaignDetail the campaign details whose BCodes are required.
     * @return returns a set of boundaries for the given campaign.
     */
    private Set<String> fetchAllBoundaryCodes(CampaignDetail campaignDetail) {
        Set<String> boundaryCodes = campaignDetail.getBoundaries().stream()
                .map(Boundary::getCode)
                .collect(Collectors.toSet());

        return boundaryCodes;
    }

    /**
     * This method filters the boundaries based on given hierarchy type for the campaign and returns a set of those boundaries.
     *
     * @param campaignDetail the campaign details whose BCodes are required.
     * @param hierarchyType  hierarchy type of the required boundaries.
     * @return returns a set of boundaries of the given hierarchy type.
     */
    private Set<String> fetchBoundaryCodes(CampaignDetail campaignDetail, String hierarchyType) {
        Set<String> boundaryCodes = campaignDetail.getBoundaries().stream()
                .filter(boundary -> hierarchyType.equals(boundary.getType().toLowerCase()))
                .map(Boundary::getCode)
                .collect(Collectors.toSet());

        return boundaryCodes;
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
        List<String> serviceBoundaries = planFacility.getServiceBoundaries();

        // Check for duplicate service boundaries
        Set<String> uniqueBoundaries = new HashSet<>(serviceBoundaries);
        if (uniqueBoundaries.size() != serviceBoundaries.size()) {
            throw new CustomException(INVALID_SERVICE_BOUNDARY_CODE, "Duplicate service boundaries are not allowed");
        }

        planFacility.getServiceBoundaries().forEach(serviceBoundary -> {
            if (!boundaryCodes.contains(serviceBoundary)) {
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
        // Get the hierarchy type from the campaign response
        String hierarchyType = campaignResponse.getCampaignDetails().get(0).getHierarchyType();

        // Define the JSON path to fetch hierarchy configurations from MDMS data
        final String jsonPathForHierarchy = JSON_ROOT_PATH + MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_HIERARCHY_CONFIG + "[*]";

        List<Map<String, Object>> hierarchyConfigList = null;
        try {
            hierarchyConfigList = JsonPath.read(mdmsData, jsonPathForHierarchy);
        } catch (Exception e) {
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }

        // Iterate through the hierarchy configuration list
        for (Map<String, Object> hierarchyConfig : hierarchyConfigList) {
            if (hierarchyType.equals(hierarchyConfig.get(MDMS_MASTER_HIERARCHY))) {
                // Return the lowest hierarchy value from the configuration
                return (String) hierarchyConfig.get(LOWEST_HIERARCHY_FIELD_FOR_MICROPLAN);
            }
        }
        // Throw exception if no matching hierarchy is found
        throw new CustomException(HIERARCHY_NOT_FOUND_IN_MDMS_CODE, HIERARCHY_NOT_FOUND_IN_MDMS_MESSAGE);
    }

    /**
     * This method validates if the plan facility id provided in the update request exists
     *
     * @param planFacilityRequest
     */
    private void validatePlanFacilityExistence(PlanFacilityRequest planFacilityRequest) {
        List<PlanFacility> planFacilityListFromSearch = planFacilityRepository.search(PlanFacilitySearchCriteria.builder()
                .ids(Collections.singleton(planFacilityRequest.getPlanFacility().getId()))
                .build());

        // If plan facility id provided is invalid, throw an exception
        if (CollectionUtils.isEmpty(planFacilityListFromSearch)) {
            throw new CustomException(INVALID_PLAN_FACILITY_ID_CODE, INVALID_PLAN_FACILITY_ID_MESSAGE);
        }

        enrichInitialServiceBoundaries(planFacilityListFromSearch, planFacilityRequest);
    }

    private void enrichInitialServiceBoundaries(List<PlanFacility> planFacilityListFromSearch, PlanFacilityRequest planFacilityRequest) {

        List<String> initiallySetServiceBoundaries = planFacilityListFromSearch.get(0).getServiceBoundaries();
        planFacilityRequest.getPlanFacility().setInitiallySetServiceBoundaries(initiallySetServiceBoundaries);
    }

    /**
     * Searches the plan config based on the plan config id provided
     *
     * @param planConfigurationId
     * @param tenantId
     * @return
     */
    public List<PlanConfiguration> fetchPlanConfigurationById(String planConfigurationId, String tenantId) {
        List<PlanConfiguration> planConfigurations = planConfigurationRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(planConfigurationId)
                .tenantId(tenantId)
                .build());
        log.info("planConfigurations: " + planConfigurations);

        // Validate planConfiguration exists
        if (CollectionUtils.isEmpty(planConfigurations)) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }

        return planConfigurations;
    }

    /**
     * Validates if the facility with the provided ID exists in the system.
     *
     * @param planFacilityRequest
     */
    private void validateFacilityExistence(PlanFacilityRequest planFacilityRequest) {
        FacilityResponse facilityResponse = facilityUtil.fetchFacilityData(planFacilityRequest);

        // Use ObjectUtils and CollectionUtils to handle null or empty checks
        if (ObjectUtils.isEmpty(facilityResponse) || CollectionUtils.isEmpty(facilityResponse.getFacilities())) {
            throw new CustomException("FACILITY_NOT_FOUND", "Facility with ID " + planFacilityRequest.getPlanFacility().getFacilityId() + " not found in the system.");
        }

        enrichFacilityDetails(facilityResponse.getFacilities().get(0), planFacilityRequest);
    }

    private void enrichFacilityDetails(Facility facility, PlanFacilityRequest planFacilityRequest) {
        String facilityName = facility.getName();
        planFacilityRequest.getPlanFacility().setFacilityName(facilityName);
        BigDecimal initialServingPop = BigDecimal.ZERO;

        Map<String, Object> fieldsToBeAdded = new HashMap<>();
        fieldsToBeAdded.put("facilityUsage", facility.getUsage());
        fieldsToBeAdded.put("capacity", facility.getStorageCapacity());
        fieldsToBeAdded.put("facilityStatus", facility.getAddress().getType());
        fieldsToBeAdded.put("facilityType", facility.getUsage());
        fieldsToBeAdded.put("isPermanent", facility.isPermanent());
        fieldsToBeAdded.put("servingPopulation", initialServingPop);

        planFacilityRequest.getPlanFacility().setAdditionalDetails(
                commonUtil.updateFieldInAdditionalDetails(planFacilityRequest.getPlanFacility().getAdditionalDetails(), fieldsToBeAdded));
    }

}
