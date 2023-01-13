package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.AddressType;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@Order(value = 2)
public class AddressTypeValidator implements Validator<IndividualRequest> {

    private final ObjectMapper objectMapper;

    @Autowired
    public AddressTypeValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Override
    public List<ErrorDetails> validate(IndividualRequest request) {
        List<ErrorDetails> errorDetailsList = new ArrayList<>();
        List<Individual> individualsWithInvalidAddress = validateAddressType(request.getIndividual());
        individualsWithInvalidAddress.forEach(individual -> populateErrorDetails(individual, "INVALID_ADDRESS",
                "Invalid address", request, errorDetailsList, objectMapper));
        return errorDetailsList;
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
