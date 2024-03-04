package org.egov.referralmanagement.validator.hfreferral;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.HFReferralRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.referralmanagement.Constants.GET_ID;

/**
 * Validator for checking the existence of entities referred in HFReferral entities.
 *
 * Author: kanishq-egov
 */
@Component
@Order(value = 4)
@Slf4j
public class HfrNonExistentEntityValidator implements Validator<HFReferralBulkRequest, HFReferral> {

    private final HFReferralRepository hfReferralRepository;

    private final ObjectMapper objectMapper;

    @Autowired
    public HfrNonExistentEntityValidator(HFReferralRepository hfReferralRepository, ObjectMapper objectMapper) {
        this.hfReferralRepository = hfReferralRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates the existence of entities referred in HFReferral entities.
     *
     * @param request The HFReferralBulkRequest containing a list of HFReferral entities
     * @return A Map containing HFReferral entities as keys and lists of errors as values
     */
    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        log.info("validating for existence of entity");
        Map<HFReferral, List<Error>> errorDetailsMap = new HashMap<>();
        List<HFReferral> hfReferrals = request.getHfReferrals();
        Class<?> objClass = getObjClass(hfReferrals);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, HFReferral> iMap = getIdToObjMap(hfReferrals
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!iMap.isEmpty()) {
            List<String> referralIds = new ArrayList<>(iMap.keySet());
            List<HFReferral> existingReferrals = hfReferralRepository
                    .findById(referralIds, false, getIdFieldName(idMethod));
            List<HFReferral> nonExistentReferrals = checkNonExistentEntities(iMap,
                    existingReferrals, idMethod);
            nonExistentReferrals.forEach(sideEffect -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(sideEffect, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}
