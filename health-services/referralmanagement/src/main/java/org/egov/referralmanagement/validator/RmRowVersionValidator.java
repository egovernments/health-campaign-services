package org.egov.referralmanagement.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.ReferralRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Order(value = 5)
@Slf4j
public class RmRowVersionValidator implements Validator<ReferralBulkRequest, Referral> {

    private ReferralRepository referralRepository;

    @Autowired
    public RmRowVersionValidator(ReferralRepository referralRepository) {
        this.referralRepository = referralRepository;
    }


    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest referralBulkRequest) {
        log.info("validating row version");
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        Method idMethod = getIdMethod(referralBulkRequest.getReferrals());
        Map<String, Referral> iMap = getIdToObjMap(referralBulkRequest.getReferrals()
                .stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()),idMethod);
        if(!iMap.isEmpty()){
            List<String> referralIds = new ArrayList<>(iMap.keySet());
            List<Referral> existingReferrals = referralRepository.findById(referralIds,false,getIdFieldName(idMethod));
            List<Referral> entitiesWithMismatchedVersion = getEntitiesWithMismatchedRowVersion(iMap,existingReferrals,idMethod);
            entitiesWithMismatchedVersion.forEach(referral -> {
                Error error = getErrorForRowVersionMismatch();
                populateErrorDetails(referral, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
