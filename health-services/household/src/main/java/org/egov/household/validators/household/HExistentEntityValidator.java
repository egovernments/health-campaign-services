package org.egov.household.validators.household;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdRepository;
import org.flywaydb.core.internal.util.CollectionsUtils;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;
import static org.egov.household.Constants.GET_CLIENT_REFERENCE_ID;
import static org.egov.household.Constants.GET_ID;

public class HExistentEntityValidator implements Validator<HouseholdBulkRequest, Household> {

    private final HouseholdRepository householdRepository;

    public HExistentEntityValidator(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    /**
     * @param request
     * @return
     */
    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        Map<Household, List<Error>> errorDetailsMap = new HashMap<>();
        List<Household> entities = request.getHouseholds();
        List<String> clientReferenceIdList = entities.stream().filter(notHavingErrors()).map(Household::getClientReferenceId).collect(Collectors.toList());
        HouseholdSearch householdSearch = HouseholdSearch.builder().clientReferenceId(clientReferenceIdList).build();
        if(!CollectionUtils.isEmpty(clientReferenceIdList)) {
            List<Household> existentEntities = householdRepository.findById(clientReferenceIdList, getIdFieldName(householdSearch), Boolean.FALSE).getY();
            existentEntities.forEach(entity -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
