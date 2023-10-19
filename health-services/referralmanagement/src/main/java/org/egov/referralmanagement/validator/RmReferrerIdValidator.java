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
import org.egov.common.service.UserService;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.service.FacilityService;
import org.egov.referralmanagement.util.ValidatorUtil;
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
import static org.egov.referralmanagement.Constants.STAFF;

/**
 * Validate the referrer using user service
 */
@Component
@Order(value = 3)
@Slf4j
public class RmReferrerIdValidator implements Validator<ReferralBulkRequest, Referral> {

    private final FacilityService facilityService;
    private final UserService userService;

    public RmReferrerIdValidator(FacilityService facilityService, UserService userService) {
        this.facilityService = facilityService;
        this.userService = userService;
    }

    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        log.info("validating referrer id");
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        List<Referral> entities = request.getReferrals();
        Map<String, List<Referral>> tenantIdReferralMap = entities.stream().collect(Collectors.groupingBy(Referral::getTenantId));
        tenantIdReferralMap.forEach((tenantId, referralList) -> {
            final List<String> projectStaffUuidList = new ArrayList<>();
            referralList.forEach(referral -> addIgnoreNull(projectStaffUuidList, referral.getReferrerId()));
            List<String> invalidStaffIds = new ArrayList<>(projectStaffUuidList);
            try {
                ValidatorUtil.validateAndEnrichStaffIds(request.getRequestInfo(), userService, projectStaffUuidList, invalidStaffIds);
            } catch (Exception e) {
                throw new CustomException("Project Staff failed to fetch", "Exception : "+e.getMessage());
            }
            List<Referral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                    invalidStaffIds.contains(entity.getReferrerId())
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
