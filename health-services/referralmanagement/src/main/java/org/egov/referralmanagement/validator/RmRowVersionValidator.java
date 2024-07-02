package org.egov.referralmanagement.validator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.ReferralRepository;
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

/*
*
* Validator for checking row version consistency of Referral entities in a bulk request.
* It retrieves existing Referral entities from the repository, compares row versions,
* and populates error details for entities with mismatched row versions.
*
* @author syed-egov
* */

@Component
@Order(value = 5)
@Slf4j
public class RmRowVersionValidator implements Validator<ReferralBulkRequest, Referral> {

    private ReferralRepository referralRepository;

    @Autowired
    public RmRowVersionValidator(ReferralRepository referralRepository) {
        this.referralRepository = referralRepository;
    }

    /*
     *
     * @param referralBulkRequest The bulk request containing Referral entities to be validated.
     * @return A map containing Referral entities with associated error details
     * for entities with mismatched row versions.
     */

    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest referralBulkRequest) {
        log.info("validating row version");
        // Map to store Referral entities with associated error details
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the method used for obtaining entity IDs
        Method idMethod = getIdMethod(referralBulkRequest.getReferrals());
        // Create a map of entity IDs to Referral entities for entities without errors
        Map<String, Referral> iMap = getIdToObjMap(referralBulkRequest.getReferrals()
                .stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()),idMethod);
        // Check if the map of IDs to Referral entities is not empty
        if(!iMap.isEmpty()){
            List<String> referralIds = new ArrayList<>(iMap.keySet());
            // Retrieve existing Referral entities from the repository
            List<Referral> existingReferrals = referralRepository.findById(referralIds,false,getIdFieldName(idMethod));
            // Identify entities with mismatched row versions
            List<Referral> entitiesWithMismatchedVersion = getEntitiesWithMismatchedRowVersion(iMap,existingReferrals,idMethod);
            // Populate error details for entities with mismatched row versions
            entitiesWithMismatchedVersion.forEach(referral -> {
                Error error = getErrorForRowVersionMismatch();
                populateErrorDetails(referral, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
