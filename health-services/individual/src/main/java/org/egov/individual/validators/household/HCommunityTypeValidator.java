package org.egov.individual.validators.household;

import org.egov.common.models.Error;
import org.egov.common.models.household.HouseHoldType;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

public class HCommunityTypeValidator implements Validator<HouseholdBulkRequest, Household> {
    private final IndividualProperties configuration;

    @Autowired
    public HCommunityTypeValidator(IndividualProperties configuration) {
        this.configuration = configuration;
    }
    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        HashMap<Household, List<Error>> errorDetailsMap = new HashMap<>();
        if (configuration.isHouseholdTypeSameValidation()) {
            // validate if request contains households of different householdTypes
            List<Household> communityHouseholds = request.getHouseholds()
                    .stream()
                    .filter(household -> household.getHouseholdType() != null &&
                            household.getHouseholdType().equals(HouseHoldType.COMMUNITY))
                    .toList();

            if (!CollectionUtils.isEmpty(communityHouseholds) &&
                    request.getHouseholds().size() != communityHouseholds.size()) {
                communityHouseholds.forEach(household -> {
                    Error error = Error.builder()
                            .errorMessage("Community and Family household cannot be in same request")
                            .errorCode("COMMUNITY_AND_FAMILY_HOUSEHOLD_IN_SAME_REQUEST")
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException("COMMUNITY_AND_FAMILY_HOUSEHOLD_IN_SAME_REQUEST", "Community and Family household cannot be in same request"))
                            .build();
                    // Populate error details for the household
                    populateErrorDetails(household, error, errorDetailsMap);
                });
            }
        }
        return errorDetailsMap;
    }
}
