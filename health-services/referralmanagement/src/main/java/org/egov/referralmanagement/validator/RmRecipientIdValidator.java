package org.egov.referralmanagement.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.service.UserService;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.service.FacilityService;
import org.egov.referralmanagement.util.ValidatorUtil;
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
import static org.egov.referralmanagement.Constants.FACILITY;
import static org.egov.referralmanagement.Constants.STAFF;

/**
 *
 */
@Component
@Order(value = 3)
@Slf4j
public class RmRecipientIdValidator implements Validator<ReferralBulkRequest, Referral> {
    private final FacilityService facilityService;
    private final UserService userService;

    public RmRecipientIdValidator(FacilityService facilityService, UserService userService) {
        this.facilityService = facilityService;
        this.userService = userService;
    }

    /**
     * @param request 
     * @return
     */
    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        log.info("validating project beneficiary id");
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        List<Referral> entities = request.getReferrals();
        Map<String, List<Referral>> tenantIdReferralMap = entities.stream().collect(Collectors.groupingBy(Referral::getTenantId));
        tenantIdReferralMap.forEach((tenantId, referralList) -> {
            List<ProjectStaff> existingProjectStaffList = new ArrayList<>();
            List<Facility> existingFacilityList = new ArrayList<>();
            final List<String> projectStaffUuidList = new ArrayList<>();
            final List<String> facilityIdList = new ArrayList<>();
            referralList.forEach(referral -> {
                switch (referral.getRecipientType()) {
                    case STAFF :
                        addIgnoreNull(projectStaffUuidList, referral.getRecipientId());
                        break;
                    case FACILITY:
                        addIgnoreNull(facilityIdList, referral.getRecipientId());
                }
            });

            List<String> invalidStaffIds = new ArrayList<>(projectStaffUuidList);
            // validate and remove valid identifiers from invalidStaffIds
            ValidatorUtil.validateAndEnrichStaffIds(request.getRequestInfo(), userService, projectStaffUuidList, invalidStaffIds);

            // validate and remove valid identifiers from invalidfacilityIds
            List<String> invalidFacilityIds = new ArrayList<>(projectStaffUuidList);
            List<String> validFacilityIds = facilityService.validateFacilityIds(facilityIdList, (List<Referral>) entities,
                    tenantId, errorDetailsMap, request.getRequestInfo());
            invalidFacilityIds.removeAll(validFacilityIds);

            List<Referral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                    entity.getRecipientType().equals(STAFF) ? invalidStaffIds.contains(entity.getRecipientId()) : invalidFacilityIds.contains(entity.getRecipientId())
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
