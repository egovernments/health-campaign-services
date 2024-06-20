package org.egov.referralmanagement.validator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.models.referralmanagement.ReferralSearch;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.ReferralRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.referralmanagement.Constants.GET_ID;

@Component
@Order(value = 4)
@Slf4j
public class RmNonExistentEntityValidator implements Validator<ReferralBulkRequest, Referral> {

    private final ReferralRepository referralRepository;

    private final ObjectMapper objectMapper;

    @Autowired
    public RmNonExistentEntityValidator(ReferralRepository referralRepository, ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.objectMapper = objectMapper;
    }


    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        log.info("validating for existence of entity");
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        List<Referral> referrals = request.getReferrals();
        Class<?> objClass = getObjClass(referrals);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, Referral> iMap = getIdToObjMap(referrals
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from referral entities
        referrals.forEach(referral -> {
            idList.add(referral.getId());
            clientReferenceIdList.add(referral.getClientReferenceId());
        });
        if (!iMap.isEmpty()) {

            // Create a search object for querying existing entities
            ReferralSearch referralSearch = ReferralSearch.builder()
                    .clientReferenceId(clientReferenceIdList)
                    .id(idList)
                    .build();

            List<Referral> existingReferrals;
            try {
                // Query the repository to find existing entities
                existingReferrals = referralRepository.find(referralSearch, referrals.size(), 0,
                        referrals.get(0).getTenantId(), null, false).getResponse();
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for Referral with error: {}", e.getMessage(), e);
                throw new CustomException("REFERRAL_SEARCH_FAILED", "Search Failed for Referral, " + e.getMessage()); 
            }
            List<Referral> nonExistentReferrals = checkNonExistentEntities(iMap,
                    existingReferrals, idMethod);
            nonExistentReferrals.forEach(sideEffect -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(sideEffect, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}

