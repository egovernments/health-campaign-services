package org.egov.referralmanagement.validator;

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
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;

/**
 * Validator for checking the existence of Project entities based on their IDs in Referral objects.
 *
 * Author: hruthvikl-egov
 */
@Component
@Order(value = 3)
@Slf4j
public class RmProjectIdValidator implements Validator<ReferralBulkRequest, Referral> {

    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration referralManagementConfiguration;

    public RmProjectIdValidator(ServiceRequestClient serviceRequestClient, ReferralManagementConfiguration referralManagementConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }

    /**
     * Validates whether projects exist in the database or not using project IDs for Referral objects.
     *
     * @param request The ReferralBulkRequest containing a list of Referral entities
     * @return A Map containing Referral entities as keys and lists of errors as values
     */
    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        log.info("Validating project IDs");
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        List<Referral> entities = request.getReferrals();

        if (!referralManagementConfiguration.getProjectIdValidatorEnabled()) {
            return errorDetailsMap;
        }

        // Grouping Referrals by tenantId to fetch projects for each tenant
        Map<String, List<Referral>> tenantIdReferralMap = entities.stream().collect(Collectors.groupingBy(Referral::getTenantId));
        tenantIdReferralMap.forEach((tenantId, referralList) -> {
            // Get all the existing projects in the referral list from Project Service
            List<Project> existingProjects = getExistingProjects(tenantId, referralList, request);
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
    private List<Project> getExistingProjects(String tenantId, List<Referral> referrals, ReferralBulkRequest request) {
        List<Project> existingProjects = null;
        final List<String> projectIdList = new ArrayList<>();

        // Collecting project IDs from Referrals
        referrals.forEach(referral -> {
            addIgnoreNull(projectIdList, referral.getProjectId());
        });

        if (!projectIdList.isEmpty()) {
            List<Project> projects = new ArrayList<>();
            projectIdList.forEach(projectId -> projects.add(Project.builder().id(projectId).tenantId(tenantId).build()));

            try {
                // Using project search and fetching the valid IDs.
                ProjectResponse projectResponse = serviceRequestClient.fetchResult(
                        new StringBuilder(referralManagementConfiguration.getProjectHost()
                                + referralManagementConfiguration.getProjectSearchUrl()
                                + "?limit=" + referrals.size()
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
    private void validateAndPopulateErrors(List<Project> existingProjects, List<Referral> entities, Map<Referral, List<Error>> errorDetailsMap) {
        final List<String> existingProjectIds = new ArrayList<>();

        // Extracting IDs from existing projects
        existingProjects.forEach(project -> {
            existingProjectIds.add(project.getId());
        });

        // Filtering invalid entities
        List<Referral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                Objects.nonNull(entity.getProjectId()) && !existingProjectIds.contains(entity.getProjectId())
        ).collect(Collectors.toList());

        // Populating error details for invalid entities
        invalidEntities.forEach(referral -> {
            log.error("Project doesn't exist for Referral: {}", referral.getProjectId());
            Error error = getErrorForNonExistentEntity();
            populateErrorDetails(referral, error, errorDetailsMap);
        });
    }
}
