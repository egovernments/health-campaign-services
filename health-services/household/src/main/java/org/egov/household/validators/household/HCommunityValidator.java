package org.egov.household.validators.household;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Order(value = 1)
@Slf4j
public class HCommunityValidator implements Validator<HouseholdBulkRequest, Household> {

    private final HouseholdConfiguration configuration;

    public HCommunityValidator(HouseholdConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        HashMap<Household, List<Error>> errorDetailsMap = new HashMap<>();
        List<Household> communityHouseholds = request.getHouseholds()
                .stream()
                .filter(household -> household.getHouseholdType() != null &&
                        household.getHouseholdType().toString().equals(configuration.getCommunityHouseholdType()))
                .toList();

        if (!CollectionUtils.isEmpty(communityHouseholds) &&
                request.getRequestInfo().getUserInfo().getRoles()
                .stream()
                .noneMatch(role -> role.getCode().equals(configuration.getCommunityHouseholdCreatorRoleCode()))) {
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
        return errorDetailsMap;
    }


}
