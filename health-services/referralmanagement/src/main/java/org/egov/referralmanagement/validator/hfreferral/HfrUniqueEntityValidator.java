package org.egov.referralmanagement.validator.hfreferral;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 *
 * Validator for checking uniqueness of HFReferral entities in a bulk request.
 * Ensures that there are no duplicate entities based on their IDs.
 *
 * Author: kanishq-egov
 */
@Component
@Order(value = 2)
@Slf4j
public class HfrUniqueEntityValidator implements Validator<HFReferralBulkRequest, HFReferral> {

    /**
     * Validates the uniqueness of HFReferral entities based on their IDs.
     *
     * @param request The HFReferralBulkRequest containing a list of HFReferral entities
     * @return A Map containing HFReferral entities as keys and lists of errors as values
     */
    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        log.info("Validating unique entity");
        Map<HFReferral, List<Error>> errorDetailsMap = new HashMap<>();
        List<HFReferral> validEntities = request.getHfReferrals()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            // Create a map of entity IDs to HFReferral objects
            Map<String, HFReferral> eMap = getIdToObjMap(validEntities);

            // Check for duplicate IDs
            if (eMap.keySet().size() != validEntities.size()) {
                List<String> duplicates = eMap.keySet().stream().filter(id ->
                        validEntities.stream()
                                .filter(entity -> entity.getId().equals(id)).count() > 1
                ).collect(Collectors.toList());

                // Populate errors for duplicate entities
                for (String key : duplicates) {
                    Error error = getErrorForUniqueEntity();
                    populateErrorDetails(eMap.get(key), error, errorDetailsMap);
                }
            }
        }
        return errorDetailsMap;
    }
}
