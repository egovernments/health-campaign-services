package org.egov.referralmanagement.validator.hfreferral;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralSearch;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.HFReferralRepository;
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
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
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
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from HfReferral entities
        hfReferrals.forEach(entity -> {
            idList.add(entity.getId());
            clientReferenceIdList.add(entity.getClientReferenceId());
        });
        if (!iMap.isEmpty()) {
            HFReferralSearch hfReferralSearch = HFReferralSearch.builder()
                    .clientReferenceId(clientReferenceIdList)
                    .id(idList)
                    .build();

            List<HFReferral> existingReferrals;
            try {
                // Query the repository to find existing entities
                existingReferrals = hfReferralRepository.find(hfReferralSearch, hfReferrals.size(), 0,
                        hfReferrals.get(0).getTenantId(), null, false).getResponse()
                ;
                List<HFReferral> nonExistentReferrals = checkNonExistentEntities(iMap,
                        existingReferrals, idMethod);
                nonExistentReferrals.forEach(hfReferral -> {
                    log.error("Entity doesn't exist in the db {}", hfReferral);
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(hfReferral, error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                // Populating InvalidTenantIdException for all entities
                hfReferrals.forEach(hfReferral -> {
                    Error error = getErrorForInvalidTenantId(hfReferrals.get(0).getTenantId(), exception);
                    populateErrorDetails(hfReferral, error, errorDetailsMap);
                });
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for HFReferral with error: {}", e.getMessage(), e);
                throw new CustomException("HFREFERRAL_SEARCH_FAILED", "Search Failed for HFReferral, " + e.getMessage()); 
            }

        }

        return errorDetailsMap;
    }
}
