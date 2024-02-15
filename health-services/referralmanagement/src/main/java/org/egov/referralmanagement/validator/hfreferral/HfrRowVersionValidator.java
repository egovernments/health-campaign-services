package org.egov.referralmanagement.validator.hfreferral;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.HFReferralRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Order(value = 5)
@Slf4j
public class HfrRowVersionValidator implements Validator<HFReferralBulkRequest, HFReferral> {

    private final HFReferralRepository hfReferralRepository;

    @Autowired
    public HfrRowVersionValidator(HFReferralRepository hfReferralRepository) {
        this.hfReferralRepository = hfReferralRepository;
    }


    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        log.info("validating row version");
        Map<HFReferral, List<Error>> errorDetailsMap = new HashMap<>();
        Method idMethod = getIdMethod(request.getHfReferrals());
        Map<String, HFReferral> iMap = getIdToObjMap(request.getHfReferrals().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!iMap.isEmpty()) {
            List<String> hfReferralIds = new ArrayList<>(iMap.keySet());
            List<HFReferral> existingHfReferrals = hfReferralRepository.findById(hfReferralIds,
                    false, getIdFieldName(idMethod));
            List<HFReferral> entitiesWithMismatchedRowVersion =
                    getEntitiesWithMismatchedRowVersion(iMap, existingHfReferrals, idMethod);
            entitiesWithMismatchedRowVersion.forEach(hfReferral -> {
                Error error = getErrorForRowVersionMismatch();
                populateErrorDetails(hfReferral, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
