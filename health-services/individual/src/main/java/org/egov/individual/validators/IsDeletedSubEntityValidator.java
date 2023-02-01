package org.egov.individual.validators;

import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.individual.web.models.Skill;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDeleteSubEntity;

@Component
@Order(2)
public class IsDeletedSubEntityValidator  implements Validator<IndividualBulkRequest, Individual> {

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        HashMap<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> validIndividuals = request.getIndividuals();
        for (Individual individual : validIndividuals) {
            List<Identifier> identifiers = individual.getIdentifiers();
            if (identifiers != null) {
                identifiers.stream().filter(Identifier::getIsDeleted)
                        .forEach(identifier -> {
                            Error error = getErrorForIsDeleteSubEntity();
                            populateErrorDetails(individual, error, errorDetailsMap);
                        });
            }

            List<Address> addresses = individual.getAddress();
            if (addresses != null) {
                addresses.stream().filter(Address::getIsDeleted)
                        .forEach(address -> {
                            Error error = getErrorForIsDeleteSubEntity();
                            populateErrorDetails(individual, error, errorDetailsMap);
                        });
            }

            List<Skill> skills = individual.getSkills();
            if (skills != null) {
                skills.stream().filter(Skill::getIsDeleted)
                        .forEach(skill -> {
                            Error error = getErrorForIsDeleteSubEntity();
                            populateErrorDetails(individual, error, errorDetailsMap);
                        });
            }
        }
        return errorDetailsMap;
    }
}
