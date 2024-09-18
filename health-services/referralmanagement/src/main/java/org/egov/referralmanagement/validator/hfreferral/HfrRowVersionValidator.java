package org.egov.referralmanagement.validator.hfreferral;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.HFReferralRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

/**
 *
 * Validator for checking row version mismatch in HFReferral entities during bulk processing.
 * Ensures that the row version of existing entities matches the row version in the request.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 5)
@Slf4j
public class HfrRowVersionValidator implements Validator<HFReferralBulkRequest, HFReferral> {

    private final HFReferralRepository hfReferralRepository;

    @Autowired
    public HfrRowVersionValidator(HFReferralRepository hfReferralRepository) {
        this.hfReferralRepository = hfReferralRepository;
    }

    /**
     * Validates row version for HFReferral entities in a bulk request.
     *
     * @param request The HFReferralBulkRequest containing a list of HFReferral entities
     * @return A Map containing HFReferral entities as keys and lists of errors as values
     */
    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        log.info("Validating row version");
        Map<HFReferral, List<Error>> errorDetailsMap = new HashMap<>();
        Method idMethod = getIdMethod(request.getHfReferrals());
        Map<String, HFReferral> iMap = getIdToObjMap(request.getHfReferrals().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!iMap.isEmpty()) {
            List<String> hfReferralIds = new ArrayList<>(iMap.keySet());
            List<HFReferral> existingHfReferrals = hfReferralRepository.findById(hfReferralIds,
                    false, getIdFieldName(idMethod)).getResponse();
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
