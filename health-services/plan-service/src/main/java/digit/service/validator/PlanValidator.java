package digit.service.validator;

import com.jayway.jsonpath.JsonPath;
import digit.config.Configuration;
import digit.repository.PlanConfigurationRepository;
import digit.repository.PlanRepository;
import digit.service.PlanEmployeeService;
import digit.service.enrichment.PlanEnricher;
import digit.util.BoundaryUtil;
import digit.util.CampaignUtil;
import digit.util.CommonUtil;
import digit.util.MdmsUtil;
import digit.web.models.*;
import digit.web.models.boundary.BoundarySearchResponse;
import digit.web.models.boundary.BoundaryTypeHierarchyResponse;
import digit.web.models.boundary.HierarchyRelation;
import digit.web.models.projectFactory.CampaignResponse;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;

@Component
public class PlanValidator {

    private PlanRepository planRepository;

    private PlanConfigurationRepository planConfigurationRepository;

    private MdmsUtil mdmsUtil;

    private MultiStateInstanceUtil centralInstanceUtil;

    private CommonUtil commonUtil;

    private CampaignUtil campaignUtil;

    private PlanEmployeeService planEmployeeService;

    private Configuration config;

    private PlanEnricher planEnricher;

    private BoundaryUtil boundaryUtil;

    public PlanValidator(PlanRepository planRepository, PlanConfigurationRepository planConfigurationRepository, MdmsUtil mdmsUtil, MultiStateInstanceUtil centralInstanceUtil, CommonUtil commonUtil, CampaignUtil campaignUtil, PlanEmployeeService planEmployeeService, Configuration config, PlanEnricher planEnricher, BoundaryUtil boundaryUtil) {
        this.planRepository = planRepository;
        this.planConfigurationRepository = planConfigurationRepository;
        this.mdmsUtil = mdmsUtil;
        this.centralInstanceUtil = centralInstanceUtil;
        this.commonUtil = commonUtil;
        this.campaignUtil = campaignUtil;
        this.planEmployeeService = planEmployeeService;
        this.config = config;
        this.planEnricher = planEnricher;
        this.boundaryUtil = boundaryUtil;
    }

    /**
     * This method performs business validations on plan create requests
     *
     * @param request
     */
    public void validatePlanCreate(PlanRequest request) {
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(request.getPlan().getTenantId());
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);
        CampaignResponse campaignResponse = campaignUtil.fetchCampaignData(request.getRequestInfo(), request.getPlan().getCampaignId(), rootTenantId);
        BoundarySearchResponse boundarySearchResponse = boundaryUtil.fetchBoundaryData(request.getRequestInfo(), request.getPlan().getLocality(), request.getPlan().getTenantId(), campaignResponse.getCampaignDetails().get(0).getHierarchyType(), Boolean.TRUE, Boolean.FALSE);

        //TODO: remove after setting the flag in consumer
        request.getPlan().setRequestFromResourceEstimationConsumer(Boolean.TRUE);

        // Validate locality against boundary service
        validateBoundaryCode(boundarySearchResponse, request.getPlan());

        // Validate activities
        validateActivities(request);

        // Validate plan configuration existence
        validatePlanConfigurationExistence(request);

        // Validate resources
        validateResources(request);

        // Validate resource-activity linkage
        validateResourceActivityLinkage(request);

        // Validate target-activity linkage
        validateTargetActivityLinkage(request);

        // Validate dependencies
        validateActivityDependencies(request);

        // Validate Target's Metrics against MDMS
        validateTargetMetrics(request, mdmsData);

        // Validate Metric Detail's Unit against MDMS
        validateMetricDetailUnit(request, mdmsData);

        // Validate if campaign id exists against project factory
        validateCampaignId(campaignResponse);

        // Validate the user information in the request
        commonUtil.validateUserInfo(request.getRequestInfo());

        // Validate plan-employee assignment and jurisdiction is request is from Resource Estimation Consumer
        if(!request.getPlan().isRequestFromResourceEstimationConsumer())
            validatePlanEmployeeAssignmentAndJurisdiction(request);
    }

    /**
     * Validates campaign ID from request against project factory
     *
     * @param campaignResponse The campaign details response from project factory
     */
    private void validateCampaignId(CampaignResponse campaignResponse) {
        if (CollectionUtils.isEmpty(campaignResponse.getCampaignDetails())) {
            throw new CustomException(NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE, NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE);
        }
    }

    /**
     * This validation method validates if the dependent activities are valid and if they form a cycle
     *
     * @param request
     */
    private void validateActivityDependencies(PlanRequest request) {
        // Check if dependent activity codes are valid
        validateDependentActivityCodes(request);

        // Check if dependent activities form a cycle
        checkForCycleInActivityDependencies(request);
    }

    /**
     * This method checks if the activity dependencies form a cycle
     *
     * @param request
     */
    private void checkForCycleInActivityDependencies(PlanRequest request) {
        Map<String, List<String>> activityCodeVsDependenciesMap = request.getPlan().getActivities().stream()
                .collect(Collectors.toMap(Activity::getCode,
                        activity -> CollectionUtils.isEmpty(activity.getDependencies()) ? List.of() : activity.getDependencies()));

        activityCodeVsDependenciesMap.keySet().forEach(activityCode -> {
            activityCodeVsDependenciesMap.get(activityCode).forEach(dependency -> {
                if (activityCodeVsDependenciesMap.get(dependency).contains(activityCode))
                    throw new CustomException(CYCLIC_ACTIVITY_DEPENDENCY_CODE, CYCLIC_ACTIVITY_DEPENDENCY_MESSAGE);
            });
        });
    }

    /**
     * This method validates if the dependent activity codes are valid
     *
     * @param request
     */
    private void validateDependentActivityCodes(PlanRequest request) {
        // Collect all activity codes
        Set<String> activityCodes = request.getPlan().getActivities().stream()
                .map(Activity::getCode)
                .collect(Collectors.toSet());

        // Check if the dependent activity codes are valid
        request.getPlan().getActivities().forEach(activity -> {
            if (!CollectionUtils.isEmpty(activity.getDependencies())) {
                activity.getDependencies().forEach(dependency -> {
                    if (!activityCodes.contains(dependency))
                        throw new CustomException(INVALID_ACTIVITY_DEPENDENCY_CODE, INVALID_ACTIVITY_DEPENDENCY_MESSAGE);
                });
            }
        });
    }


    /**
     * This method validates the activities provided in the request
     *
     * @param request
     */
    private void validateActivities(PlanRequest request) {
        // Collect all activity codes
        if (request.getPlan().getActivities() == null)
            throw new CustomException(ACTIVITIES_CANNOT_BE_NULL_CODE, ACTIVITIES_CANNOT_BE_NULL_MESSAGE);

        Set<String> activityCodes = request.getPlan().getActivities().stream()
                .map(Activity::getCode)
                .collect(Collectors.toSet());

        // If activity codes are not unique, throw an exception
        if (activityCodes.size() != request.getPlan().getActivities().size()) {
            throw new CustomException(DUPLICATE_ACTIVITY_CODES, DUPLICATE_ACTIVITY_CODES_MESSAGE);
        }

        // If execution plan id is not provided, providing activities is mandatory
        if (ObjectUtils.isEmpty(request.getPlan().getCampaignId())
                && CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            throw new CustomException(PLAN_ACTIVITIES_MANDATORY_CODE, PLAN_ACTIVITIES_MANDATORY_MESSAGE);
        }

        // If execution plan id is provided, providing activities is not allowed
        if (!ObjectUtils.isEmpty(request.getPlan().getCampaignId())
                && !CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            throw new CustomException(PLAN_ACTIVITIES_NOT_ALLOWED_CODE, PLAN_ACTIVITIES_NOT_ALLOWED_MESSAGE);
        }

        // Validate activity dates
        if (!CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            request.getPlan().getActivities().forEach(activity -> {
                if (activity.getPlannedEndDate() < activity.getPlannedStartDate())
                    throw new CustomException(INVALID_ACTIVITY_DATES_CODE, INVALID_ACTIVITY_DATES_MESSAGE);
            });
        }
    }

    /**
     * This method validates if the plan configuration id provided in the request exists
     *
     * @param request
     */
    private void validatePlanConfigurationExistence(PlanRequest request) {
        // If plan id provided is invalid, throw an exception
        if (!ObjectUtils.isEmpty(request.getPlan().getPlanConfigurationId()) &&
                CollectionUtils.isEmpty(commonUtil.searchPlanConfigId(request.getPlan().getPlanConfigurationId(), request.getPlan().getTenantId()))) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }
    }

    /**
     * This method validates the resources provided in the request
     *
     * @param request
     */
    private void validateResources(PlanRequest request) {
        // If plan configuration id is not provided, providing resources is mandatory
        if (ObjectUtils.isEmpty(request.getPlan().getPlanConfigurationId())
                && CollectionUtils.isEmpty(request.getPlan().getResources())) {
            throw new CustomException(PLAN_RESOURCES_MANDATORY_CODE, PLAN_RESOURCES_MANDATORY_MESSAGE);
        }
    }

    /**
     * This method validates the linkage between resources and activities
     *
     * @param request
     */
    private void validateResourceActivityLinkage(PlanRequest request) {
        if (ObjectUtils.isEmpty(request.getPlan().getPlanConfigurationId())
                && !CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            // Collect all activity codes
            Set<String> activityCodes = request.getPlan().getActivities().stream()
                    .map(Activity::getCode)
                    .collect(Collectors.toSet());

            // Validate resource-activity linkage
            request.getPlan().getResources().forEach(resource -> {
                if (!activityCodes.contains(resource.getActivityCode()))
                    throw new CustomException(INVALID_RESOURCE_ACTIVITY_LINKAGE_CODE, INVALID_RESOURCE_ACTIVITY_LINKAGE_MESSAGE);
            });
        }
    }

    /**
     * This method validates the linkage between targets and activities
     *
     * @param request
     */
    private void validateTargetActivityLinkage(PlanRequest request) {
        if (!CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            // Collect all activity codes
            Set<String> activityCodes = request.getPlan().getActivities().stream()
                    .map(Activity::getCode)
                    .collect(Collectors.toSet());

            // Validate target-activity linkage
            request.getPlan().getTargets().forEach(target -> {
                if (!activityCodes.contains(target.getActivityCode()))
                    throw new CustomException(INVALID_TARGET_ACTIVITY_LINKAGE_CODE, INVALID_TARGET_ACTIVITY_LINKAGE_MESSAGE);
            });
        }
    }

    /**
     * This method performs business validations on plan update requests
     *
     * @param request
     */
    public void validatePlanUpdate(PlanRequest request) {
        // Validate plan existence
        validatePlanExistence(request);

        String rootTenantId = centralInstanceUtil.getStateLevelTenant(request.getPlan().getTenantId());
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);
        CampaignResponse campaignResponse = campaignUtil.fetchCampaignData(request.getRequestInfo(), request.getPlan().getCampaignId(), rootTenantId);
        BoundaryTypeHierarchyResponse boundaryTypeHierarchyResponse = boundaryUtil.fetchBoundaryHierarchy(request.getRequestInfo(), request.getPlan().getTenantId(), campaignResponse.getCampaignDetails().get(0).getHierarchyType());

        //TODO: remove after setting the flag in consumer
        request.getPlan().setRequestFromResourceEstimationConsumer(Boolean.TRUE);


        // Validate activities
        validateActivities(request);

        // Validate activities uuid uniqueness
        validateActivitiesUuidUniqueness(request);

        // Validate plan configuration existence
        validatePlanConfigurationExistence(request);

        // Validate resources
        validateResources(request);

        // Validate resource uuid uniqueness
        validateResourceUuidUniqueness(request);

        // Validate target uuid uniqueness
        validateTargetUuidUniqueness(request);

        // Validate resource-activity linkage
        validateResourceActivityLinkage(request);

        // Validate target-activity linkage
        validateTargetActivityLinkage(request);

        // Validate dependencies
        validateActivityDependencies(request);

        // Validate Target's Metrics against MDMS
        validateTargetMetrics(request, mdmsData);

        // Validate Metric Detail's Unit against MDMS
        validateMetricDetailUnit(request, mdmsData);

        // Validate the user information in the request
        commonUtil.validateUserInfo(request.getRequestInfo());

        // Validate plan-employee assignment and jurisdiction
        validatePlanEmployeeAssignmentAndJurisdiction(request);

        // Enrich jurisdiction mapping in plan
        planEnricher.enrichJurisdictionMapping(request.getPlan(), boundaryTypeHierarchyResponse.getBoundaryHierarchy().get(0));
    }

    /**
     * Validates that all target UUIDs within the provided PlanRequest are unique.
     *
     * @param request the PlanRequest containing the targets to be validated
     * @throws CustomException if any target UUIDs are not unique
     */
    private void validateTargetUuidUniqueness(PlanRequest request) {
        // Collect all target UUIDs
        Set<String> targetUuids = request.getPlan().getTargets().stream()
                .map(Target::getId)
                .collect(Collectors.toSet());

        // If target UUIDs are not unique, throw an exception
        if (targetUuids.size() != request.getPlan().getTargets().size()) {
            throw new CustomException(DUPLICATE_TARGET_UUIDS_CODE, DUPLICATE_TARGET_UUIDS_MESSAGE);
        }
    }

    /**
     * Validates that all resource UUIDs within the provided PlanRequest are unique.
     *
     * @param request the PlanRequest containing the resources to be validated
     * @throws CustomException if any resource UUIDs are not unique
     */
    private void validateResourceUuidUniqueness(PlanRequest request) {
        // Collect all resource UUIDs
        Set<String> resourceUuids = request.getPlan().getResources().stream()
                .map(Resource::getId)
                .collect(Collectors.toSet());

        // If resource UUIDs are not unique, throw an exception
        if (resourceUuids.size() != request.getPlan().getResources().size()) {
            throw new CustomException(DUPLICATE_RESOURCE_UUIDS_CODE, DUPLICATE_RESOURCE_UUIDS_MESSAGE);
        }
    }

    /**
     * Validates that all activity UUIDs within the provided PlanRequest are unique.
     *
     * @param request the PlanRequest containing the activities to be validated
     * @throws CustomException if any activity UUIDs are not unique
     */
    private void validateActivitiesUuidUniqueness(PlanRequest request) {
        // Collect all activity UUIDs
        Set<String> activityUuids = request.getPlan().getActivities().stream()
                .map(Activity::getId)
                .collect(Collectors.toSet());

        // If activity UUIDs are not unique, throw an exception
        if (activityUuids.size() != request.getPlan().getActivities().size()) {
            throw new CustomException(DUPLICATE_ACTIVITY_UUIDS_CODE, DUPLICATE_ACTIVITY_UUIDS_MESSAGE);
        }
    }


    /**
     * This method validates if the plan id provided in the update request exists
     *
     * @param request the PlanRequest containing the plan
     */
    private void validatePlanExistence(PlanRequest request) {
        // If plan id provided is invalid, throw an exception
        List<Plan> planFromDatabase = planRepository.search(PlanSearchCriteria.builder()
                .ids(Collections.singleton(request.getPlan().getId()))
                .build());
        if (CollectionUtils.isEmpty(planFromDatabase)) {
            throw new CustomException(INVALID_PLAN_ID_CODE, INVALID_PLAN_ID_MESSAGE);
        }
        // enriching boundary ancestral path for incoming plan request from the database
        request.getPlan().setBoundaryAncestralPath(planFromDatabase.get(0).getBoundaryAncestralPath());
    }

    /**
     * Validates the target metrics within the provided PlanRequest against MDMS data.
     *
     * This method checks each target metric in the plan to ensure it exists in the MDMS data.
     * If a metric is not found, it throws a CustomException.
     *
     * @param request  the PlanRequest containing the plan and target metrics to be validated
     * @param mdmsData the MDMS data against which the target metrics are validated
     * @throws CustomException if there is an error reading the MDMS data using JsonPath
     *                         or if any target metric is not found in the MDMS data
     */
    public void validateTargetMetrics(PlanRequest request, Object mdmsData) {
        Plan plan = request.getPlan();
        final String jsonPathForMetric = "$." + MDMS_PLAN_MODULE_NAME + "." + MDMS_MASTER_METRIC + ".*.code";

        List<Object> metricListFromMDMS = null;
        System.out.println("Jsonpath -> " + jsonPathForMetric);
        try {
            metricListFromMDMS = JsonPath.read(mdmsData, jsonPathForMetric);
        } catch (Exception e) {
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }
        HashSet<Object> metricSetFromMDMS = new HashSet<>(metricListFromMDMS);
        plan.getTargets().stream().forEach(target -> {
            if (!metricSetFromMDMS.contains(target.getMetric())) {
                throw new CustomException(METRIC_NOT_FOUND_IN_MDMS_CODE, METRIC_NOT_FOUND_IN_MDMS_MESSAGE);
            }
        });

    }

    /**
     * Validates the metric unit details within the provided PlanRequest against MDMS data.
     *
     * This method extracts metric details from the plan and checks if each metric unit
     * is present in the MDMS data. If a metric unit is not found, it throws a CustomException.
     *
     * @param request  the PlanRequest containing the plan and metric details to be validated
     * @param mdmsData the MDMS data against which the metric units are validated
     * @throws CustomException if there is an error reading the MDMS data using JsonPath
     *                         or if any metric unit is not found in the MDMS data
     */
    public void validateMetricDetailUnit(PlanRequest request, Object mdmsData) {
        Plan plan = request.getPlan();

        List<MetricDetail> metricDetails = plan.getTargets().stream()
                .map(Target::getMetricDetail)
                .toList();

        List<Object> metricUnitListFromMDMS;
        final String jsonPathForMetricUnit = "$." + MDMS_PLAN_MODULE_NAME + "." + MDMS_MASTER_UOM + ".*.uomCode";
        try {
            metricUnitListFromMDMS = JsonPath.read(mdmsData, jsonPathForMetricUnit);
        } catch (Exception e) {
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }

        HashSet<Object> metricUnitSetFromMDMS = new HashSet<>(metricUnitListFromMDMS);
        metricDetails.stream().forEach(metricDetail -> {
            if (!metricUnitSetFromMDMS.contains(metricDetail.getMetricUnit())) {
                throw new CustomException(METRIC_UNIT_NOT_FOUND_IN_MDMS_CODE, METRIC_UNIT_NOT_FOUND_IN_MDMS_MESSAGE);
            }
        });

    }

    /**
     * Validates the plan's employee assignment and ensures the jurisdiction is valid based on tenant, employee, role, and plan configuration.
     * If no assignment is found, throws a custom exception.
     *
     * @param planRequest the request containing the plan and workflow details
     * @throws CustomException if no employee assignment is found or jurisdiction is invalid
     */
    public void validatePlanEmployeeAssignmentAndJurisdiction(PlanRequest planRequest) {
        PlanEmployeeAssignmentSearchCriteria planEmployeeAssignmentSearchCriteria = PlanEmployeeAssignmentSearchCriteria
                .builder()
                .tenantId(planRequest.getPlan().getTenantId())
                .employeeId(Collections.singletonList(planRequest.getRequestInfo().getUserInfo().getUuid()))
                .planConfigurationId(planRequest.getPlan().getPlanConfigurationId())
                .role(config.getPlanEstimationApproverRoles())
                .build();

        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponse = planEmployeeService.search(PlanEmployeeAssignmentSearchRequest.builder()
                .planEmployeeAssignmentSearchCriteria(planEmployeeAssignmentSearchCriteria)
                .requestInfo(planRequest.getRequestInfo()).build());

        if(CollectionUtils.isEmpty(planEmployeeAssignmentResponse.getPlanEmployeeAssignment()))
            throw new CustomException(PLAN_EMPLOYEE_ASSIGNMENT_NOT_FOUND_CODE, PLAN_EMPLOYEE_ASSIGNMENT_NOT_FOUND_MESSAGE + planRequest.getPlan().getLocality());

        validateJurisdiction(planRequest.getPlan(),
                planEmployeeAssignmentResponse.getPlanEmployeeAssignment().get(0).getJurisdiction());
    }

    /**
     * Validates that at least one jurisdiction exists within the hierarchy's boundary codes.
     * If no jurisdiction is found in the boundary set, throws a custom exception.
     *
     * @param plan the plan containing the boundary ancestral path
     * @param jurisdictions the list of jurisdictions to check against the boundary set
     * @throws CustomException if none of the jurisdictions are present in the boundary codes
     */
    public void validateJurisdiction(Plan plan, Set<String> jurisdictions) {
        Set<String> boundarySet = new HashSet<>(Arrays.asList(plan.getBoundaryAncestralPath()
                .split(PIPE_REGEX)));

        // Check if any jurisdiction is present in the boundary set
        if (jurisdictions.stream().noneMatch(boundarySet::contains))
            throw new CustomException(JURISDICTION_NOT_FOUND_CODE, JURISDICTION_NOT_FOUND_MESSAGE);

        // Enrich jurisdiction of current assignee
        plan.setAssigneeJurisdiction(new ArrayList<>(jurisdictions));

    }

    /**
     * Validates the boundary code provided in plan request against boundary service.
     *
     * @param boundarySearchResponse response from the boundary service.
     * @param plan                 Plan record whose loclality is to be validated.
     */
    private void validateBoundaryCode(BoundarySearchResponse boundarySearchResponse, Plan plan) {
        HierarchyRelation tenantBoundary = boundarySearchResponse.getTenantBoundary().get(0);

        if (CollectionUtils.isEmpty(tenantBoundary.getBoundary())) {
            throw new CustomException(NO_BOUNDARY_DATA_FOUND_FOR_GIVEN_BOUNDARY_CODE_CODE, NO_BOUNDARY_DATA_FOUND_FOR_GIVEN_BOUNDARY_CODE_MESSAGE);
        }

        // Enrich the boundary ancestral path and jurisdiction mapping for the provided boundary code
        if(plan.isRequestFromResourceEstimationConsumer())
            planEnricher.enrichBoundaryAncestralPath(plan, tenantBoundary);
    }

    /**
     * @param bulkPlanRequest
     */
    public void validateBulkPlanUpdate(BulkPlanRequest bulkPlanRequest) {
        CampaignResponse campaignResponse = campaignUtil.fetchCampaignData(bulkPlanRequest.getRequestInfo(), bulkPlanRequest.getPlans().get(0).getCampaignId(), bulkPlanRequest.getPlans().get(0).getTenantId());
        BoundaryTypeHierarchyResponse boundaryTypeHierarchyResponse = boundaryUtil.fetchBoundaryHierarchy(bulkPlanRequest.getRequestInfo(), bulkPlanRequest.getPlans().get(0).getTenantId(), campaignResponse.getCampaignDetails().get(0).getHierarchyType());

        // Validate attributes across each plan in the bulk request
        validatePlanAttributes(bulkPlanRequest);

        // Validate if plans provided in the request body exist
        validatePlanExistence(bulkPlanRequest);

        // Validate plan employee assignment and jurisdiction
        validatePlanEmployeeAssignmentAndJurisdiction(bulkPlanRequest);

        // Enrich jurisdiction mapping in plan
        planEnricher.enrichJurisdictionMapping(bulkPlanRequest.getPlans(), boundaryTypeHierarchyResponse.getBoundaryHierarchy().get(0));
    }

    /**
     *
     * @param bulkPlanRequest
     */
    private void validatePlanEmployeeAssignmentAndJurisdiction(BulkPlanRequest bulkPlanRequest) {
        // Prepare plan employee assignment search criteria
        PlanEmployeeAssignmentSearchCriteria planEmployeeAssignmentSearchCriteria = PlanEmployeeAssignmentSearchCriteria
                .builder()
                .tenantId(bulkPlanRequest.getPlans().get(0).getTenantId())
                .employeeId(Collections.singletonList(bulkPlanRequest.getRequestInfo().getUserInfo().getUuid()))
                .planConfigurationId(bulkPlanRequest.getPlans().get(0).getPlanConfigurationId())
                .role(config.getPlanEstimationApproverRoles())
                .build();

        // Fetch plan employee assignment
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponse = planEmployeeService.search(PlanEmployeeAssignmentSearchRequest.builder()
                .planEmployeeAssignmentSearchCriteria(planEmployeeAssignmentSearchCriteria)
                .requestInfo(bulkPlanRequest.getRequestInfo())
                .build());

        // Throw exception if the employee taking action is not a part of plan
        if(CollectionUtils.isEmpty(planEmployeeAssignmentResponse.getPlanEmployeeAssignment())) {
            throw new CustomException(PLAN_EMPLOYEE_ASSIGNMENT_NOT_FOUND_CODE,
                    PLAN_EMPLOYEE_ASSIGNMENT_NOT_FOUND_MESSAGE);
        }

        // Validate jurisdiction for each plan
        bulkPlanRequest.getPlans().forEach(plan -> validateJurisdiction(plan,
                planEmployeeAssignmentResponse.getPlanEmployeeAssignment().get(0).getJurisdiction()));

    }

    /**
     *
     * @param bulkPlanRequest
     */
    private void validatePlanAttributes(BulkPlanRequest bulkPlanRequest) {
        if(bulkPlanRequest.getPlans().stream().map(Plan :: getId).collect(Collectors.toSet()).size()
                != bulkPlanRequest.getPlans().size()) {
            throw new CustomException("BULK_UPDATE_ERROR",
                    "Plans provided in the bulk update request are not unique.");
        }

        if(!bulkPlanRequest.getPlans().stream().allMatch(plan ->
                plan.getTenantId().equals(bulkPlanRequest.getPlans().get(0).getTenantId()) &&
                        plan.getPlanConfigurationId().equals(bulkPlanRequest.getPlans().get(0).getPlanConfigurationId()))) {
            throw new CustomException("BULK_UPDATE_ERROR",
                    "Tenant id and plan configuration ids should be same across all entries for bulk update.");
        }

        bulkPlanRequest.getPlans().forEach(plan -> {
            if(ObjectUtils.isEmpty(plan.getWorkflow())) {
                throw new CustomException("BULK_UPDATE_ERROR",
                        "Workflow information is mandatory for each entry for bulk update");
            }
        });

        if(!bulkPlanRequest.getPlans().stream().allMatch(plan ->
                plan.getStatus().equals(bulkPlanRequest.getPlans().get(0).getStatus()) &&
                        plan.getWorkflow().getAction().equals(bulkPlanRequest.getPlans().get(0).getWorkflow().getAction()))) {
            throw new CustomException("BULK_UPDATE_ERROR",
                    "All entries should be in the same state for bulk transitioning plan records.");
        }

    }

    /**
     *
     * @param bulkPlanRequest
     */
    private void validatePlanExistence(BulkPlanRequest bulkPlanRequest) {
        // Get all plan ids to validate existence
        List<Plan> planListFromDatabase = planRepository.search(PlanSearchCriteria.builder()
                .ids(bulkPlanRequest.getPlans().stream().map(Plan :: getId).collect(Collectors.toSet()))
                .offset(0)
                .limit(bulkPlanRequest.getPlans().size())
                .build());

        // If plan id provided is invalid, throw an exception
        if (planListFromDatabase.size() != bulkPlanRequest.getPlans().size()) {
            throw new CustomException(INVALID_PLAN_ID_CODE, INVALID_PLAN_ID_MESSAGE);
        }

        // Enrich ancestral materialized path for each plan object being passed in the request
        enrichAncestralMaterializedPath(bulkPlanRequest, planListFromDatabase);

    }

    /**
     *
     * @param bulkPlanRequest
     * @param planListFromDatabase
     */
    private void enrichAncestralMaterializedPath(BulkPlanRequest bulkPlanRequest, List<Plan> planListFromDatabase) {
        Map<String, String> planIdVsAncestralMaterializedPathMap = planListFromDatabase.stream()
                .collect(Collectors.toMap(Plan :: getId, Plan :: getBoundaryAncestralPath));

        bulkPlanRequest.getPlans().forEach(plan ->
                plan.setBoundaryAncestralPath(planIdVsAncestralMaterializedPathMap
                        .get(plan.getId()))
        );
    }
}