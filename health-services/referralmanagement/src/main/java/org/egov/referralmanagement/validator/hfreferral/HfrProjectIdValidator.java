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
 * Validate whether project exist in db or not using project id for HFReferral object
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

    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        log.info("validating project  id");
        Map<HFReferral, List<Error>> errorDetailsMap = new HashMap<>();
        List<HFReferral> entities = request.getHfReferrals();
        Map<String, List<HFReferral>> tenantIdReferralMap = entities.stream().collect(Collectors.groupingBy(HFReferral::getTenantId));
        tenantIdReferralMap.forEach((tenantId, hfReferralList) -> {
            /** Get all the existing project in the hfReferral list from Project Service
             */
            List<Project> existingProjects = getExistingProjects(tenantId, hfReferralList, request);
            /** Validate project and populate error map if invalid entities are found
             */
            validateAndPopulateErrors(existingProjects, entities, errorDetailsMap);
        });
        return errorDetailsMap;
    }
    private void addIgnoreNull(List<String> list, String item) {
        if(Objects.nonNull(item)) list.add(item);
    }

    private List<Project> getExistingProjects(String tenantId, List<HFReferral> hfReferrals, HFReferralBulkRequest request) {
        List<Project> existingProjects = null;
        final List<String> projectIdList = new ArrayList<>();
        hfReferrals.forEach(hfReferral -> {
            addIgnoreNull(projectIdList, hfReferral.getProjectId());
        });
        if(!projectIdList.isEmpty()) {
            List<Project> projects = new ArrayList<>();
            projectIdList.forEach(projectId -> projects.add(Project.builder().id(projectId).tenantId(tenantId).build()));
            try {
                // using project search and fetching the valid ids.
                ProjectResponse projectResponse = serviceRequestClient.fetchResult(
                        new StringBuilder(referralManagementConfiguration.getProjectHost()
                                + referralManagementConfiguration.getProjectSearchUrl()
                                +"?limit=" + hfReferrals.size()
                                + "&offset=0&tenantId=" + tenantId),
                        ProjectRequest.builder()
                                .requestInfo(request.getRequestInfo())
                                .projects(projects)
                                .build(),
                        ProjectResponse.class
                );
                existingProjects = projectResponse.getProject();
            } catch (Exception e) {
                throw new CustomException("Projects failed to fetch", "Exception : "+e.getMessage());
            }
        }

        return existingProjects;
    }

    private void validateAndPopulateErrors(List<Project> existingProjects, List<HFReferral> entities, Map<HFReferral, List<Error>> errorDetailsMap) {
        final List<String> existingProjectIds = new ArrayList<>();
        existingProjects.forEach(project -> {
            existingProjectIds.add(project.getId());
        });
        List<HFReferral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                Objects.nonNull(entity.getProjectId()) && !existingProjectIds.contains(entity.getProjectId())
        ).collect(Collectors.toList());
        invalidEntities.forEach(hfReferral -> {
            Error error = getErrorForNonExistentEntity();
            populateErrorDetails(hfReferral, error, errorDetailsMap);
        });
    }
}
