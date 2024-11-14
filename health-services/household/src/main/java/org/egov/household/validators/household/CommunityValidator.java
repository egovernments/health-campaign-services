package org.egov.household.validators.household;

import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

public class CommunityValidator implements Validator<HouseholdBulkRequest, Household> {

    private final HouseholdConfiguration configuration;

    public CommunityValidator(HouseholdConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        List<Household> communityHouseholds = request.getHouseholds()
                .stream()
                .filter(household -> household.getHouseHoldType().toString().equals(configuration.getCommunityHouseholdType()))
                .collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(communityHouseholds) &&
                request.getRequestInfo().getUserInfo().getRoles()
                .stream()
                .anyMatch(role -> role.getCode().equals(configuration.getCommunityHouseholdCreatorRoleCode()))) {
            HashMap<Household, List<Error>> errorDetailsMap = new HashMap<>();
            communityHouseholds.forEach(household -> {
                Error error = Error.builder()
                        .errorMessage("User doesn't have permission to create/update community household")
                        .errorCode("COMMUNITY_USER_ACCESS_DENIED")
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException("COMMUNITY_USER_ACCESS_DENIED", "User doesn't have permission to create/update community household"))
                        .build();
                // Populate error details for the household
                populateErrorDetails(household, error, errorDetailsMap);
            });
            return errorDetailsMap;

        }
        return null;
    }


}
