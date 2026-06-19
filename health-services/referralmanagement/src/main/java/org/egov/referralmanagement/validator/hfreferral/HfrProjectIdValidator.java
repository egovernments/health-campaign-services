package org.egov.referralmanagement.validator.hfreferral;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.models.project.ProjectResponse;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;

/**
 * Validator for checking the existence of Project entities based on their IDs in HFReferral objects.
 *
 * Author: kanishq-egov
 */
@Component
@Order(value = 3)
@Slf4j
public class HfrProjectIdValidator implements Validator<HFReferralBulkRequest, HFReferral> {

    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration referralManagementConfiguration;

    public HfrProjectIdValidator(ServiceRequestClient serviceRequestClient, ReferralManagementConfiguration referralManagementConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }

    /**
     * Validates whether projects exist in the database or not using project IDs for HFReferral objects.
     *
     * @param request The HFReferralBulkRequest containing a list of HFReferral entities
     * @return A Map containing HFReferral entities as keys and lists of errors as values
     */
    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        log.info("Validating project IDs");
        Map<HFReferral, List<Error>> errorDetailsMap = new HashMap<>();
        List<HFReferral> entities = request.getHfReferrals();

        // Grouping HFReferrals by tenantId to fetch projects for each tenant
        Map<String, List<HFReferral>> tenantIdReferralMap = entities.stream().collect(Collectors.groupingBy(HFReferral::getTenantId));
        tenantIdReferralMap.forEach((tenantId, hfReferralList) -> {
            // Get all the existing projects in the hfReferral list from Project Service
            List<Project> existingProjects = getExistingProjects(tenantId, hfReferralList, request);
            // Validate projects and populate error map if invalid entities are found
            validateAndPopulateErrors(existingProjects, entities, errorDetailsMap);
        });

        return errorDetailsMap;
    }

    // Helper method to add an item to a list if it is not null
    private void addIgnoreNull(List<String> list, String item) {
        if (Objects.nonNull(item)) list.add(item);
    }

    // Fetches existing projects from Project Service based on their IDs
    private List<Project> getExistingProjects(String tenantId, List<HFReferral> hfReferrals, HFReferralBulkRequest request) {
        List<Project> existingProjects = null;
        final List<String> projectIdList = new ArrayList<>();

        // Collecting project IDs from HFReferrals
        hfReferrals.forEach(hfReferral -> {
            addIgnoreNull(projectIdList, hfReferral.getProjectId());
        });

        if (!projectIdList.isEmpty()) {
            List<Project> projects = new ArrayList<>();
            projectIdList.forEach(projectId -> projects.add(Project.builder().id(projectId).tenantId(tenantId).build()));

            try {
                // Using project search and fetching the valid IDs.
                ProjectResponse projectResponse = serviceRequestClient.fetchResult(
                        new StringBuilder(referralManagementConfiguration.getProjectHost()
                                + referralManagementConfiguration.getProjectSearchUrl()
                                + "?limit=" + hfReferrals.size()
                                + "&offset=0&tenantId=" + tenantId),
                        ProjectRequest.builder()
                                .requestInfo(request.getRequestInfo())
                                .projects(projects)
                                .build(),
                        ProjectResponse.class
                );
                existingProjects = projectResponse.getProject();
            } catch (Exception e) {
                throw new CustomException("Projects failed to fetch", "Exception : " + e.getMessage());
            }
        }

        return existingProjects;
    }

    // Validates projects and populates the error map if invalid entities are found
    private void validateAndPopulateErrors(List<Project> existingProjects, List<HFReferral> entities, Map<HFReferral, List<Error>> errorDetailsMap) {
        final List<String> existingProjectIds = new ArrayList<>();

        // Extracting IDs from existing projects
        existingProjects.forEach(project -> {
            existingProjectIds.add(project.getId());
        });

        // Filtering invalid entities
        List<HFReferral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                Objects.nonNull(entity.getProjectId()) && !existingProjectIds.contains(entity.getProjectId())
        ).collect(Collectors.toList());

        // Populating error details for invalid entities
        invalidEntities.forEach(hfReferral -> {
            log.error("Project doesn't exist for HF Referral: {}", hfReferral.getProjectId());
            Error error = getErrorForNonExistentEntity();
            populateErrorDetails(hfReferral, error, errorDetailsMap);
        });
    }
}
