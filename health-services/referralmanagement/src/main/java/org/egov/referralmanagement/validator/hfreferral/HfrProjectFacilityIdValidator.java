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
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkResponse;
import org.egov.common.models.project.ProjectFacilitySearch;
import org.egov.common.models.project.ProjectFacilitySearchRequest;
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
public class HfrProjectFacilityIdValidator implements Validator<HFReferralBulkRequest, HFReferral> {
    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration referralManagementConfiguration;

    public HfrProjectFacilityIdValidator(ServiceRequestClient serviceRequestClient, ReferralManagementConfiguration referralManagementConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }

    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        log.info("validating project facility id");
        Map<HFReferral, List<Error>> errorDetailsMap = new HashMap<>();
        List<HFReferral> entities = request.getHfReferrals();
        Map<String, List<HFReferral>> tenantIdReferralMap = entities.stream().collect(Collectors.groupingBy(HFReferral::getTenantId));
        tenantIdReferralMap.forEach((tenantId, hfReferralList) -> {
            /** Get all the existing project in the hfReferral list from Project Service
             */
            List<ProjectFacility> existingProjectFacilities = getExistingProjects(tenantId, hfReferralList, request);
            /** Validate project and populate error map if invalid entities are found
             */
            validateAndPopulateErrors(existingProjectFacilities, entities, errorDetailsMap);
        });
        return errorDetailsMap;
    }
    private void addIgnoreNull(List<String> list, String item) {
        if(Objects.nonNull(item)) list.add(item);
    }

    private List<ProjectFacility> getExistingProjects(String tenantId, List<HFReferral> hfReferrals, HFReferralBulkRequest request) {
        List<ProjectFacility> existingProjectFacilities = new ArrayList<>();
        final List<String> projectFacilityIdList = new ArrayList<>();
        hfReferrals.forEach(hfReferral -> {
            addIgnoreNull(projectFacilityIdList, hfReferral.getProjectFacilityId());
        });
        if(!projectFacilityIdList.isEmpty()) {
            ProjectFacilitySearch projectFacilitySearch = ProjectFacilitySearch.builder()
                    .id(!projectFacilityIdList.isEmpty()? projectFacilityIdList : null)
                    .tenantId(tenantId)
                    .build();
            try {
                // using project facility search and fetching the valid ids.
                ProjectFacilityBulkResponse projectFacilityBulkResponse = serviceRequestClient.fetchResult(
                        new StringBuilder(referralManagementConfiguration.getProjectHost()
                                + referralManagementConfiguration.getProjectFacilitySearchUrl()
                                +"?limit=" + hfReferrals.size()
                                + "&offset=0&tenantId=" + tenantId),
                        ProjectFacilitySearchRequest.builder()
                                .requestInfo(request.getRequestInfo())
                                .projectFacility(projectFacilitySearch)
                                .build(),
                        ProjectFacilityBulkResponse.class
                );
                existingProjectFacilities = projectFacilityBulkResponse.getProjectFacilities();
            } catch (Exception e) {
                throw new CustomException("Project Facilities failed to fetch", "Exception : "+e.getMessage());
            }
        }

        return existingProjectFacilities;
    }

    private void validateAndPopulateErrors(List<ProjectFacility> existingProjectFacilities, List<HFReferral> entities, Map<HFReferral, List<Error>> errorDetailsMap) {
        final List<String> existingProjectFacilityIds = new ArrayList<>();
        existingProjectFacilities.forEach(projectFacility -> {
            existingProjectFacilityIds.add(projectFacility.getId());
        });
        List<HFReferral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                Objects.nonNull(entity.getProjectFacilityId()) && !existingProjectFacilityIds.contains(entity.getProjectFacilityId())
        ).collect(Collectors.toList());
        invalidEntities.forEach(hfReferral -> {
            Error error = getErrorForNonExistentEntity();
            populateErrorDetails(hfReferral, error, errorDetailsMap);
        });
    }
}
