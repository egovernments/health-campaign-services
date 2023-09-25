package org.egov.household.validators.household;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;
import static org.egov.household.Constants.CLIENT_REFERENCE_ID;

@Component
@Order(value = 6)
@Slf4j
public class HUniqueClientReferenceIdValidator implements Validator<HouseholdBulkRequest, Household> {

    private final HouseholdRepository householdRepository;

    public HUniqueClientReferenceIdValidator(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        Map<Household, List<Error>> errorDetailsMap = new HashMap<>();
        List<Household> validEntities = request.getHouseholds()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());
        Map<String, Household> eMap = new HashMap<>();
        if (!validEntities.isEmpty()) {
            for (Household household : validEntities) {
                eMap.put(household.getClientReferenceId(), household);
            }
            // get already existing clientrefids from db even deleted ones as uniqueness is just on clientref column
            List<Household> existingEntities = householdRepository.findByIdFromDB(new ArrayList<>(eMap.keySet()),
                    CLIENT_REFERENCE_ID, true);
            List<String> existingClientReferenceIds = existingEntities.stream()
                    .map(Household::getClientReferenceId).collect(Collectors.toList());
            List<String> duplicates = eMap.keySet().stream().filter(id ->
                    existingClientReferenceIds.contains(id) || validEntities.stream()
                            .filter(entity -> entity.getClientReferenceId().equals(id)).count() > 1
            ).collect(Collectors.toList());
            for (String key : duplicates) {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(eMap.get(key), error, errorDetailsMap);
            }
        }
        return errorDetailsMap;
    }
}
