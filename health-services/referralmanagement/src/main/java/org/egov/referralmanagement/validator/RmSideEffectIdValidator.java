package org.egov.referralmanagement.validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearch;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearchRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.service.SideEffectService;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;

/**
 * Validate Side Effect id from the Referral Objects
 */
@Component
@Order(value = 3)
@Slf4j
public class RmSideEffectIdValidator implements Validator<ReferralBulkRequest, Referral> {
    private final SideEffectService sideEffectService;
    public RmSideEffectIdValidator(SideEffectService sideEffectService) {
        this.sideEffectService = sideEffectService;
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
            List<String> sideEffectIds = new ArrayList<>();
            referralList.forEach(referral -> {
                if (Objects.nonNull(referral.getSideEffect()))
                    addIgnoreNull(sideEffectIds, referral.getSideEffect().getId());
            });
            List<String> validSideEffectIds = new ArrayList<>();
            if(!sideEffectIds.isEmpty()) {
                try {
                    validSideEffectIds = sideEffectService.search(
                            SideEffectSearchRequest.builder().sideEffect(SideEffectSearch.builder().id(sideEffectIds).build()).build(),
                            sideEffectIds.size(), 0, tenantId, null, false
                    ).getResponse().stream().map(SideEffect::getId).collect(Collectors.toList());
                } catch (Exception e) {
                    throw new CustomException("Side Effect failed to fetch", "Exception : " + e.getMessage());
                }
            }
            sideEffectIds.removeAll(validSideEffectIds);
            List<String> invalidSideEffectIds = new ArrayList<>(sideEffectIds);

            validateAndPopulateErrors(entities, invalidSideEffectIds, errorDetailsMap);

        });

        return errorDetailsMap;
    }
    private void addIgnoreNull(List<String> list, String item) {
        if(Objects.nonNull(item)) list.add(item);
    }

    private void validateAndPopulateErrors(List<Referral> entities, List<String> invalidSideEffectIds, Map<Referral, List<Error>> errorDetailsMap) {
        List<Referral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                Objects.nonNull(entity.getSideEffect()) && invalidSideEffectIds.contains(entity.getSideEffect().getId())
        ).collect(Collectors.toList());

        invalidEntities.forEach(referral -> {
            Error error = getErrorForNonExistentEntity();
            populateErrorDetails(referral, error, errorDetailsMap);
        });
    }
}
