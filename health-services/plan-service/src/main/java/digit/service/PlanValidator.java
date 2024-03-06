package digit.service;

import digit.web.models.Activity;
import digit.web.models.PlanCreateRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PlanValidator {

    public void validatePlanCreate(PlanCreateRequest request) {

        // Validate execution plan existence
        validateExecutionPlanExistence(request);

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

    }

    private void validateExecutionPlanExistence(PlanCreateRequest request) {
    }

    private void validateActivities(PlanCreateRequest request) {
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

    private void validatePlanConfigurationExistence(PlanCreateRequest request) {

    }

    private void validateResources(PlanCreateRequest request) {
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

    private void validateResourceActivityLinkage(PlanCreateRequest request) {
        if(!CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            Set<String> activityCodes = request.getPlan().getActivities().stream()
                    .map(Activity::getCode)
                    .collect(Collectors.toSet());
            request.getPlan().getResources().forEach(resource -> {
                if(!activityCodes.contains(resource.getActivityCode()))
                    throw new CustomException("INVALID_RESOURCE_ACTIVITY_LINKAGE", "Resource-Activity linkage is invalid");
            });
        }
    }

    private void validateTargetActivityLinkage(PlanCreateRequest request) {
        if(!CollectionUtils.isEmpty(request.getPlan().getActivities())) {
            Set<String> activityCodes = request.getPlan().getActivities().stream()
                    .map(Activity::getCode)
                    .collect(Collectors.toSet());
            request.getPlan().getTargets().forEach(target -> {
                if(!activityCodes.contains(target.getActivityCode()))
                    throw new CustomException("INVALID_TARGET_ACTIVITY_LINKAGE", "Target-Activity linkage is invalid");
            });
        }
    }

}
