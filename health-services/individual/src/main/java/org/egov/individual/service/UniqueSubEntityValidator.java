package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.utils.Validator;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Qualifier;
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

@Component
@Order(value = 3)
@Slf4j
public class UniqueSubEntityValidator implements Validator<IndividualBulkRequest, Individual> {

    private final ObjectMapper objectMapper;

    private static final Error.ErrorType ERROR_TYPE = Error.ErrorType.NON_RECOVERABLE;

    public UniqueSubEntityValidator(@Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest individualBulkRequest) {
        log.info("unique sub validation started");
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> validIndividuals = individualBulkRequest.getIndividuals()
                        .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validIndividuals.isEmpty()) {
            for (Individual individual : validIndividuals) {
                List<Address> address = individual.getAddress().stream().filter(ad -> ad.getId() != null)
                        .collect(Collectors.toList());
                if (!address.isEmpty()) {
                    Map<String, Address> aMap = getIdToObjMap(address);

                    if (aMap.keySet().size() != address.size()) {
                        List<String> duplicates = aMap.keySet().stream().filter(id ->
                                address.stream()
                                        .filter(ad -> ad.getId().equals(id)).count() > 1
                        ).collect(Collectors.toList());
                        for (String key : duplicates) {
                            Error error = Error.builder().errorMessage("Duplicate address")
                                    .errorCode("DUPLICATE_ADDRESS")
                                    .type(ERROR_TYPE)
                                    .exception(new CustomException("DUPLICATE_ADDRESS", "Duplicate address"))
                                    .build();
                            populateErrorDetails(individual, error, errorDetailsMap, objectMapper);
                        }
                    }
                }

                if (individual.getIdentifiers() != null) {
                    List<Identifier> identifiers = individual.getIdentifiers();
                    if (!identifiers.isEmpty()) {
                        Method idMethod = getMethod("getIdentifierType", Identifier.class);
                        Map<String, Identifier> identifierMap = getIdToObjMap(identifiers, idMethod);
                        if (identifierMap.keySet().size() != identifiers.size()) {
                            List<String> duplicates = identifierMap.keySet().stream().filter(id ->
                                    identifiers.stream()
                                            .filter(idt -> idt.getIdentifierType().equals(id)).count() > 1
                            ).collect(Collectors.toList());
                            for (String key : duplicates) {
                                Error error = Error.builder().errorMessage("Duplicate identifier")
                                        .errorCode("DUPLICATE_IDENTIFIER")
                                        .type(ERROR_TYPE)
                                        .exception(new CustomException("DUPLICATE_IDENTIFIER",
                                                "Duplicate identifier"))
                                        .build();
                                populateErrorDetails(individual, error, errorDetailsMap, objectMapper);
                            }
                        }
                    }
                }
            }
        }

        log.info("unique sub validation finished");
        return errorDetailsMap;
    }
}
