package org.egov.referralmanagement.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkResponse;
import org.egov.common.models.project.ProjectStaffSearch;
import org.egov.common.models.project.ProjectStaffSearchRequest;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;

/**
 *
 */
@Component
@Order(value = 3)
@Slf4j
public class RmReferrerIdValidator implements Validator<ReferralBulkRequest, Referral> {

    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration referralManagementConfiguration;

    public RmReferrerIdValidator(ServiceRequestClient serviceRequestClient, ReferralManagementConfiguration referralManagementConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }

    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        log.info("validating referrer id");
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        List<Referral> entities = request.getReferrals();
        Map<String, List<Referral>> tenantIdReferralMap = entities.stream().collect(Collectors.groupingBy(Referral::getTenantId));
        tenantIdReferralMap.forEach((tenantId, referralList) -> {
            List<ProjectStaff> existingProjectStaffList = new ArrayList<>();
            final List<String> projectStaffUuidList = new ArrayList<>();
            referralList.forEach(referral -> addIgnoreNull(projectStaffUuidList, referral.getReferrerId()));
            ProjectStaffSearch projectStaffSearch = ProjectStaffSearch.builder()
                    .id(projectStaffUuidList.isEmpty() ? null : projectStaffUuidList)
                    .build();
            try {
                ProjectStaffBulkResponse projectStaffBulkResponse = serviceRequestClient.fetchResult(
                        new StringBuilder(referralManagementConfiguration.getProjectHost()
                                + referralManagementConfiguration.getProjectStaffSearchUrl()
                                +"?limit=" + entities.size()
                                + "&offset=0&tenantId=" + tenantId),
                        ProjectStaffSearchRequest.builder().requestInfo(request.getRequestInfo()).projectStaff(projectStaffSearch).build(),
                        ProjectStaffBulkResponse.class
                );
                existingProjectStaffList = projectStaffBulkResponse.getProjectStaff();
            } catch (Exception e) {
                throw new CustomException("Project Staff failed to fetch", "Exception : "+e.getMessage());
            }
            final List<String> existingProjectStaffUuids = new ArrayList<>();
            existingProjectStaffList.forEach(projectStaff -> existingProjectStaffUuids.add(projectStaff.getId()));
            List<Referral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                    !existingProjectStaffUuids.contains(entity.getReferrerId())
            ).collect(Collectors.toList());
            invalidEntities.forEach(referral -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(referral, error, errorDetailsMap);
            });
        });
        return errorDetailsMap;
    }
    private void addIgnoreNull(List<String> list, String item) {
        if(Objects.nonNull(item)) list.add(item);
    }
}
