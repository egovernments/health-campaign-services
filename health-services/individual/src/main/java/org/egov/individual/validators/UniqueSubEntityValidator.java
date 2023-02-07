package org.egov.individual.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.individual.web.models.Skill;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueSubEntity;
import static org.egov.individual.Constants.GET_ID;
import static org.egov.individual.Constants.GET_IDENTIFIER_TYPE;

@Component
@Order(value = 3)
@Slf4j
public class UniqueSubEntityValidator implements Validator<IndividualBulkRequest, Individual> {

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest individualBulkRequest) {
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> validIndividuals = individualBulkRequest.getIndividuals()
                        .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validIndividuals.isEmpty()) {
            for (Individual individual : validIndividuals) {
                if (individual.getAddress() != null) {
                    List<Address> address = individual.getAddress().stream().filter(ad -> ad.getId() != null)
                            .collect(Collectors.toList());
                    if (!address.isEmpty()) {
                        Map<String, Address> aMap = getIdToObjMap(address);
                        if (aMap.keySet().size() != address.size()) {
                            List<String> duplicates = aMap.keySet().stream().filter(id ->
                                    address.stream()
                                            .filter(ad -> ad.getId().equals(id)).count() > 1
                            ).collect(Collectors.toList());
                            duplicates.forEach( duplicate -> {
                                Error error = getErrorForUniqueSubEntity();
                                populateErrorDetails(individual, error, errorDetailsMap);
                            });
                        }
                    }
                }

                if (individual.getIdentifiers() != null) {
                    List<Identifier> identifiers = individual.getIdentifiers();
                    if (!identifiers.isEmpty()) {
                        Method idMethod = getMethod(GET_IDENTIFIER_TYPE, Identifier.class);
                        Map<String, Identifier> identifierMap = getIdToObjMap(identifiers, idMethod);
                        if (identifierMap.keySet().size() != identifiers.size()) {
                            List<String> duplicates = identifierMap.keySet().stream().filter(id ->
                                    identifiers.stream()
                                            .filter(idt -> idt.getIdentifierType().equals(id)).count() > 1
                            ).collect(Collectors.toList());
                            duplicates.forEach( duplicate -> {
                                Error error = getErrorForUniqueSubEntity();
                                populateErrorDetails(individual, error, errorDetailsMap);
                            });
                        }
                    }
                }

                List<Skill> skills = individual.getSkills();
                if (skills != null && !skills.isEmpty()) {
                    Method idMethod = getMethod(GET_ID, Skill.class);
                    Map<String, Skill> skillMap = getIdToObjMap(skills, idMethod);
                    if (skillMap.keySet().size() != skills.size()) {
                        List<String> duplicates = skillMap.keySet().stream().filter(id ->
                                skills.stream()
                                        .filter(idt -> idt.getId().equals(id)).count() > 1
                        ).collect(Collectors.toList());
                        duplicates.forEach(duplicate -> {
                            Error error = getErrorForUniqueSubEntity();
                            populateErrorDetails(individual, error, errorDetailsMap);
                        });
                    }
                }
            }
        }
        return errorDetailsMap;
    }
}
