package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;

@Component
@Order(value = 5)
public class UniqueEntityValidator implements Validator<IndividualBulkRequest, Individual> {

    private final ObjectMapper objectMapper;

    public UniqueEntityValidator(@Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest individualBulkRequest) {
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        Map<String, Individual> iMap = getIdToObjMap(individualBulkRequest.getIndividuals());
        if (iMap.keySet().size() != individualBulkRequest.getIndividuals().size()) {
            List<String> duplicates = iMap.keySet().stream().filter(id ->
                    individualBulkRequest.getIndividuals().stream()
                            .filter(individual -> individual.getId().equals(id)).count() > 1
            ).collect(Collectors.toList());
            for (String key : duplicates) {
                Error error = Error.builder().errorMessage("Duplicate individual").errorCode("DUPLICATE_INDIVIDUAL")
                        .exception(new CustomException("DUPLICATE_INDIVIDUAL", "Duplicate individual")).build();
                populateErrorDetails(iMap.get(key), error, errorDetailsMap, objectMapper);
            }
        }

        for (Individual individual : individualBulkRequest.getIndividuals()) {
            List<Address> address = individual.getAddress().stream().filter(ad -> ad.getId() != null)
                    .collect(Collectors.toList());
            Map<String, Address> aMap = getIdToObjMap(address);

            if (aMap.keySet().size() != address.size()) {
                List<String> duplicates = aMap.keySet().stream().filter(id ->
                        address.stream()
                                .filter(ad -> ad.getId().equals(id)).count() > 1
                ).collect(Collectors.toList());
                for (String key : duplicates) {
                    Error error = Error.builder().errorMessage("Duplicate address").errorCode("DUPLICATE_ADDRESS")
                            .exception(new CustomException("DUPLICATE_ADDRESS", "Duplicate address")).build();
                    populateErrorDetails(iMap.get(key), error, errorDetailsMap, objectMapper);
                }
            }

            List<Identifier> identifiers = individual.getIdentifiers().stream().filter(id -> id.getId() != null)
                    .collect(Collectors.toList());
            Map<String, Identifier> identifierMap = getIdToObjMap(identifiers);
            if (identifierMap.keySet().size() != identifiers.size()) {
                List<String> duplicates = identifierMap.keySet().stream().filter(id ->
                        identifiers.stream()
                                .filter(idt -> idt.getId().equals(id)).count() > 1
                ).collect(Collectors.toList());
                for (String key : duplicates) {
                    Error error = Error.builder().errorMessage("Duplicate identifier").errorCode("DUPLICATE_IDENTIFIER")
                            .exception(new CustomException("DUPLICATE_IDENTIFIER", "Duplicate identifier")).build();
                    populateErrorDetails(iMap.get(key), error, errorDetailsMap, objectMapper);
                }
            }
        }
        return errorDetailsMap;
    }
}
