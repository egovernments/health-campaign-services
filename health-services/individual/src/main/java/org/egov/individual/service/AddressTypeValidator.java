package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.AddressType;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(value = 2)
public class AddressTypeValidator implements Validator<IndividualRequest, Individual> {

    private final ObjectMapper objectMapper;

    @Autowired
    public AddressTypeValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Override
    public Map<Individual, ErrorDetails> validate(IndividualRequest request) {
        Map<Individual, ErrorDetails> errorDetailsMap = new HashMap<>();
        List<Individual> individualsWithInvalidAddress = validateAddressType(request.getIndividuals());
        individualsWithInvalidAddress.forEach(individual -> {
            Error error = Error.builder().errorMessage("Invalid address").errorCode("INVALID_ADDRESS")
                    .exception(new CustomException("INVALID_ADDRESS", "Invalid address")).build();
            populateErrorDetails(individual, error, errorDetailsMap, objectMapper);
        });
        return errorDetailsMap;
    }

    private List<Individual> validateAddressType(List<Individual> indInReq) {
        List<Individual> individuals = new ArrayList<>();
        for (Individual individual : indInReq) {
            Map<AddressType, Integer> addressTypeCountMap = new EnumMap<>(AddressType.class);
            if (individual.getAddress() == null) {
                continue;
            }
            for (Address address : individual.getAddress()) {
                addressTypeCountMap.merge(address.getType(), 1, Integer::sum);
            }
            addressTypeCountMap.entrySet().stream().filter(e -> e.getValue() > 1).forEach(e -> individuals.add(individual));
        }
        return individuals;
    }
}
