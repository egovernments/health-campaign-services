package digit.service.validator;

import digit.repository.PlanConfigurationRepository;
import digit.repository.PlanFacilityRepository;
import digit.service.enrichment.PlanFacilityEnricher;
import digit.util.*;
import digit.web.models.*;
import digit.web.models.facility.Facility;
import digit.web.models.facility.FacilityResponse;
import digit.web.models.projectFactory.CampaignDetail;
import digit.web.models.projectFactory.CampaignResponse;
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
     * @param planFacilityRequest The plan facility request to be validated.
     */
    public void validatePlanFacilityCreate(PlanFacilityRequest planFacilityRequest) {
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
    private void validateDuplicateRecords(PlanFacilityRequest planFacilityRequest) {
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
     * @param planFacilityRequest the plan facility request to be validated.
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
        Set<String> allBoundaryCodes = fetchBoundaryCodes(campaignResponse.getCampaignDetails().get(0), null);

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
     * Fetches boundary codes from a campaign detail, optionally filtered by hierarchy type.
     *
     * @param campaignDetail the campaign details whose BCodes are required.
     * @param hierarchyType  hierarchy type of the required boundaries.
     * @return A Set of boundary codes matching the given hierarchy type, or all codes if no type specified.
     */
    private Set<String> fetchBoundaryCodes(CampaignDetail campaignDetail, String hierarchyType) {
        return campaignDetail.getBoundaries().stream()
                // Filter boundaries by hierarchyType if hierarchyType is provided, otherwise include all
                .filter(boundary -> hierarchyType == null || hierarchyType.equals(boundary.getType().toLowerCase()))
                .map(Boundary::getCode)
                .collect(Collectors.toSet());
    }

    /**
     * This method validates if residing boundaries exist in campaign details.
     *
     * @param boundaryCodes The boundary codes present in the campaign.
     * @param planFacility The plan facility with residing boundaries.
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
     * @param boundaryCodes The boundary codes present in the campaign.
     * @param planFacility The plan facility with service boundaries.
     */
    private void validateServiceBoundaries(Set<String> boundaryCodes, PlanFacility planFacility) {
        List<String> serviceBoundaries = planFacility.getServiceBoundaries();

        // Check for duplicate service boundaries
        Set<String> uniqueBoundaries = new HashSet<>(serviceBoundaries);
        if (uniqueBoundaries.size() != serviceBoundaries.size()) {
            throw new CustomException(DUPLICATE_SERVICE_BOUNDARY_CODE, DUPLICATE_SERVICE_BOUNDARY_MESSAGE);
        }

        planFacility.getServiceBoundaries().forEach(serviceBoundary -> {
            if (!boundaryCodes.contains(serviceBoundary)) {
                throw new CustomException(INVALID_SERVICE_BOUNDARY_CODE, INVALID_SERVICE_BOUNDARY_MESSAGE);
            }
        });
    }

    /**
     * This method validates if the plan facility id provided in the update request exists
     *
     * @param planFacilityRequest The plan facility request to be validated.
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

    /**
     * This method enriches the plan facility request with the initial service boundaries
     * from the existing plan facility record.
     *
     * @param planFacilityListFromSearch A list of plan facility records retrieved from the search.
     * @param planFacilityRequest        The plan facility request that needs to be enriched with
     *                                   initially set service boundaries.
     */
    private void enrichInitialServiceBoundaries(List<PlanFacility> planFacilityListFromSearch, PlanFacilityRequest planFacilityRequest) {

        List<String> initiallySetServiceBoundaries = planFacilityListFromSearch.get(0).getServiceBoundaries();
        planFacilityRequest.getPlanFacility().setInitiallySetServiceBoundaries(initiallySetServiceBoundaries);
    }

    /**
     * Searches the plan config based on the plan config id provided
     *
     * @param planConfigurationId The plan configuration id to be searched.
     * @param tenantId tenant id of plan configuration.
     * @return Returns a list of plan configurations.
     */
    public List<PlanConfiguration> fetchPlanConfigurationById(String planConfigurationId, String tenantId) {
        List<PlanConfiguration> planConfigurations = planConfigurationRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(planConfigurationId)
                .tenantId(tenantId)
                .build());

        // Validate planConfiguration exists
        if (CollectionUtils.isEmpty(planConfigurations)) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }

        return planConfigurations;
    }

    /**
     * Validates if the facility with the provided ID exists in the system.
     *
     * @param planFacilityRequest The plan facility request with facility id.
     */
    private void validateFacilityExistence(PlanFacilityRequest planFacilityRequest) {
        FacilityResponse facilityResponse = facilityUtil.fetchFacilityData(planFacilityRequest);

        // Use ObjectUtils and CollectionUtils to handle null or empty checks
        if (ObjectUtils.isEmpty(facilityResponse) || CollectionUtils.isEmpty(facilityResponse.getFacilities())) {
            throw new CustomException(FACILITY_NOT_FOUND_CODE, FACILITY_NOT_FOUND_MESSAGE + planFacilityRequest.getPlanFacility().getFacilityId());
        }

        enrichFacilityDetails(facilityResponse.getFacilities().get(0), planFacilityRequest);
    }

    /**
     * This method enriches the plan facility request with details from the given facility,
     * including its name, usage, capacity, status, type, permanence, and initial serving population.
     *
     * @param facility           The facility from which details are extracted.
     * @param planFacilityRequest The plan facility request that needs to be enriched with details.
     */
    private void enrichFacilityDetails(Facility facility, PlanFacilityRequest planFacilityRequest) {
        String facilityName = facility.getName();
        planFacilityRequest.getPlanFacility().setFacilityName(facilityName);
        BigDecimal initialServingPop = BigDecimal.ZERO;

        Map<String, Object> fieldsToBeAdded = new HashMap<>();
        fieldsToBeAdded.put(FACILITY_USAGE_KEY, facility.getUsage());
        fieldsToBeAdded.put(CAPACITY_KEY, facility.getStorageCapacity());
        fieldsToBeAdded.put(FACILITY_STATUS_KEY, facility.getAddress().getType());
        fieldsToBeAdded.put(FACILITY_TYPE_KEY, facility.getUsage());
        fieldsToBeAdded.put(IS_PERMANENT_KEY, facility.isPermanent());
        fieldsToBeAdded.put(SERVING_POPULATION_KEY, initialServingPop);

        planFacilityRequest.getPlanFacility().setAdditionalDetails(
                commonUtil.updateFieldInAdditionalDetails(planFacilityRequest.getPlanFacility().getAdditionalDetails(), fieldsToBeAdded));
    }

}
