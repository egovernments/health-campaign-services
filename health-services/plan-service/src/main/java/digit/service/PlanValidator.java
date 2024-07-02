package digit.service;

import com.jayway.jsonpath.JsonPath;
import digit.repository.PlanConfigurationRepository;
import digit.repository.PlanRepository;
import digit.util.MdmsUtil;
import digit.web.models.*;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.INVALID_PLAN_CONFIG_ID_CODE;
import static digit.config.ServiceConstants.INVALID_PLAN_CONFIG_ID_MESSAGE;
import static digit.config.ServiceConstants.JSONPATH_ERROR_CODE;
import static digit.config.ServiceConstants.JSONPATH_ERROR_MESSAGE;
import static digit.config.ServiceConstants.MDMS_MASTER_METRIC;
import static digit.config.ServiceConstants.MDMS_MASTER_UOM;
import static digit.config.ServiceConstants.MDMS_PLAN_MODULE_NAME;
import static digit.config.ServiceConstants.METRIC_NOT_FOUND_IN_MDMS_CODE;
import static digit.config.ServiceConstants.METRIC_NOT_FOUND_IN_MDMS_MESSAGE;
import static digit.config.ServiceConstants.METRIC_UNIT_NOT_FOUND_IN_MDMS_CODE;
import static digit.config.ServiceConstants.METRIC_UNIT_NOT_FOUND_IN_MDMS_MESSAGE;

@Component
public class PlanValidator {

    private PlanRepository planRepository;

    private PlanConfigurationRepository planConfigurationRepository;

    private MdmsUtil mdmsUtil;

    public PlanValidator(PlanRepository planRepository, PlanConfigurationRepository planConfigurationRepository, MdmsUtil mdmsUtil) {
        this.planRepository = planRepository;
        this.planConfigurationRepository = planConfigurationRepository;
        this.mdmsUtil = mdmsUtil;
    }

    /**
     * This method performs business validations on plan create requests
     * @param request
     */
    public void validatePlanCreate(PlanRequest request) {
        String rootTenantId = request.getPlan().getTenantId().split("\\.")[0];
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);

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
    }

    /**
     * This validation method validates if the dependent activities are valid and if they form a cycle
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
     * @param request
     */
    private void checkForCycleInActivityDependencies(PlanRequest request) {
        Map<String, List<String>> activityCodeVsDependenciesMap = request.getPlan().getActivities().stream()
                .collect(Collectors.toMap(Activity::getCode,
                        activity -> CollectionUtils.isEmpty(activity.getDependencies()) ? List.of() : activity.getDependencies()));

        activityCodeVsDependenciesMap.keySet().forEach(activityCode -> {
            activityCodeVsDependenciesMap.get(activityCode).forEach(dependency -> {
                if(activityCodeVsDependenciesMap.get(dependency).contains(activityCode))
                    throw new CustomException("CYCLIC_ACTIVITY_DEPENDENCY", "Cyclic activity dependency found");
            });
        });
    }

    /**
     * This method validates if the dependent activity codes are valid
     * @param request
     */
    private void validateDependentActivityCodes(PlanRequest request) {
        // Collect all activity codes
        Set<String> activityCodes = request.getPlan().getActivities().stream()
                .map(Activity::getCode)
                .collect(Collectors.toSet());

        // Check if the dependent activity codes are valid
        request.getPlan().getActivities().forEach(activity -> {
            if(!CollectionUtils.isEmpty(activity.getDependencies())) {
                activity.getDependencies().forEach(dependency -> {
                    if(!activityCodes.contains(dependency))
                        throw new CustomException("INVALID_ACTIVITY_DEPENDENCY", "Activity dependency is invalid");
                });
            }
        });
    }


    /**
     * This method validates the activities provided in the request
     * @param request
     */
    private void validateActivities(PlanRequest request) {
        // Collect all activity codes
        if(request.getPlan().getActivities() == null)
            throw new CustomException("ACTIVITIES_CANNOT_BE_NULL","Activities list in Plan cannot be null");

        Set<String> activityCodes = request.getPlan().getActivities().stream()
                .map(Activity::getCode)
                .collect(Collectors.toSet());

        // If activity codes are not unique, throw an exception
        if(activityCodes.size() != request.getPlan().getActivities().size()) {
            throw new CustomException("DUPLICATE_ACTIVITY_CODES", "Activity codes within the plan should be unique");
        }

        // If execution plan id is not provided, providing activities is mandatory
        if(ObjectUtils.isEmpty(request.getPlan().getExecutionPlanId())
                && CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            throw new CustomException("PLAN_ACTIVITIES_MANDATORY", "Activities are mandatory if execution plan id is not provided");
        }

        // If execution plan id is provided, providing activities is not allowed
        if(!ObjectUtils.isEmpty(request.getPlan().getExecutionPlanId())
                && !CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            throw new CustomException("PLAN_ACTIVITIES_NOT_ALLOWED", "Activities are not allowed if execution plan id is provided");
        }

        // Validate activity dates
        if(!CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            request.getPlan().getActivities().forEach(activity -> {
                if(activity.getPlannedEndDate() < activity.getPlannedStartDate())
                    throw new CustomException("INVALID_ACTIVITY_DATES", "Planned end date cannot be before planned start date");
            });
        }
    }

    /**
     * This method validates if the plan configuration id provided in the request exists
     * @param request
     */
    private void validatePlanConfigurationExistence(PlanRequest request) {
        // If plan id provided is invalid, throw an exception
        if(!ObjectUtils.isEmpty(request.getPlan().getPlanConfigurationId()) && CollectionUtils.isEmpty(planConfigurationRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(request.getPlan().getPlanConfigurationId())
                .tenantId(request.getPlan().getTenantId())
                .build()))) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }
    }

    /**
     * This method validates the resources provided in the request
     * @param request
     */
    private void validateResources(PlanRequest request) {
        // If plan configuration id is not provided, providing resources is mandatory
        if(ObjectUtils.isEmpty(request.getPlan().getPlanConfigurationId())
                && CollectionUtils.isEmpty(request.getPlan().getResources())) {
            throw new CustomException("PLAN_RESOURCES_MANDATORY", "Resources are mandatory if plan configuration id is not provided");
        }

        // If plan configuration id is provided, providing resources is not allowed
        if(!ObjectUtils.isEmpty(request.getPlan().getPlanConfigurationId())
                && !CollectionUtils.isEmpty(request.getPlan().getResources())) {
            throw new CustomException("PLAN_RESOURCES_NOT_ALLOWED", "Resources are not allowed if plan configuration id is provided");
        }

        // Validate resource type existence
        if(!CollectionUtils.isEmpty(request.getPlan().getResources())) {
            request.getPlan().getResources().forEach(resource -> {
                // Validate resource type existence
            });
        }
    }

    /**
     * This method validates the linkage between resources and activities
     * @param request
     */
    private void validateResourceActivityLinkage(PlanRequest request) {
        if(ObjectUtils.isEmpty(request.getPlan().getPlanConfigurationId())
                && !CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            // Collect all activity codes
            Set<String> activityCodes = request.getPlan().getActivities().stream()
                    .map(Activity::getCode)
                    .collect(Collectors.toSet());

            // Validate resource-activity linkage
            request.getPlan().getResources().forEach(resource -> {
                if(!activityCodes.contains(resource.getActivityCode()))
                    throw new CustomException("INVALID_RESOURCE_ACTIVITY_LINKAGE", "Resource-Activity linkage is invalid");
            });
        }
    }

    /**
     * This method validates the linkage between targets and activities
     * @param request
     */
    private void validateTargetActivityLinkage(PlanRequest request) {
        if(!CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            // Collect all activity codes
            Set<String> activityCodes = request.getPlan().getActivities().stream()
                    .map(Activity::getCode)
                    .collect(Collectors.toSet());

            // Validate target-activity linkage
            request.getPlan().getTargets().forEach(target -> {
                if(!activityCodes.contains(target.getActivityCode()))
                    throw new CustomException("INVALID_TARGET_ACTIVITY_LINKAGE", "Target-Activity linkage is invalid");
            });
        }
    }

    /**
     * This method performs business validations on plan update requests
     * @param request
     */
    public void validatePlanUpdate(PlanRequest request) {
        // Validate plan existence
        validatePlanExistence(request);

        String rootTenantId = request.getPlan().getTenantId().split("\\.")[0];
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);

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

    }

    private void validateTargetUuidUniqueness(PlanRequest request) {
        // Collect all target uuids
        Set<String> targetUuids = request.getPlan().getTargets().stream()
                .map(Target::getId)
                .collect(Collectors.toSet());

        // If target uuids are not unique, throw an exception
        if(targetUuids.size() != request.getPlan().getTargets().size()) {
            throw new CustomException("DUPLICATE_TARGET_UUIDS", "Target uuids should be unique");
        }
    }

    private void validateResourceUuidUniqueness(PlanRequest request) {
        // Collect all resource uuids
        Set<String> resourceUuids = request.getPlan().getResources().stream()
                .map(Resource::getId)
                .collect(Collectors.toSet());

        // If resource uuids are not unique, throw an exception
        if(resourceUuids.size() != request.getPlan().getResources().size()) {
            throw new CustomException("DUPLICATE_RESOURCE_UUIDS", "Resource uuids should be unique");
        }
    }

    private void validateActivitiesUuidUniqueness(PlanRequest request) {
        // Collect all activity uuids
        Set<String> activityUuids = request.getPlan().getActivities().stream()
                .map(Activity::getId)
                .collect(Collectors.toSet());

        // If activity uuids are not unique, throw an exception
        if(activityUuids.size() != request.getPlan().getActivities().size()) {
            throw new CustomException("DUPLICATE_ACTIVITY_UUIDS", "Activity uuids should be unique");
        }
    }

    /**
     * This method validates if the plan id provided in the update request exists
     * @param request
     */
    private void validatePlanExistence(PlanRequest request) {
        // If plan id provided is invalid, throw an exception
        if(CollectionUtils.isEmpty(planRepository.search(PlanSearchCriteria.builder()
                .ids(Collections.singleton(request.getPlan().getId()))
                .build()))) {
            throw new CustomException("INVALID_PLAN_ID", "Plan id provided is invalid");
        }
    }

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

        for (Target target : plan.getTargets()) {
            if (!metricListFromMDMS.contains(target.getMetric())) {
                throw new CustomException(METRIC_NOT_FOUND_IN_MDMS_CODE, METRIC_NOT_FOUND_IN_MDMS_MESSAGE);
            }
        }
    }

    public void validateMetricDetailUnit(PlanRequest request, Object mdmsData) {
        Plan plan = request.getPlan();

        List<MetricDetail> metricDetails = plan.getTargets().stream()
                .map(Target::getMetricDetail)
                .toList();

        List<Object> metricUnitListFromMDMS = null;
        final String jsonPathForMetricUnit = "$." + MDMS_PLAN_MODULE_NAME + "." + MDMS_MASTER_UOM + ".*.code";
        try {
            metricUnitListFromMDMS = JsonPath.read(mdmsData, jsonPathForMetricUnit);
        } catch (Exception e) {
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }

        for (MetricDetail metricDetail : metricDetails) {
            if (!metricUnitListFromMDMS.contains(metricDetail.getMetricUnit())) {
                throw new CustomException(METRIC_UNIT_NOT_FOUND_IN_MDMS_CODE, METRIC_UNIT_NOT_FOUND_IN_MDMS_MESSAGE);
            }
        }
    }

}
