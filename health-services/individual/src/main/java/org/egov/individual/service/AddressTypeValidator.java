package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.utils.Validator;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.AddressType;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(value = 2)
@Slf4j
public class AddressTypeValidator implements Validator<IndividualBulkRequest, Individual> {

    private static final Error.ErrorType ERROR_TYPE = Error.ErrorType.RECOVERABLE;


    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> individuals = request.getIndividuals();
        if (!individuals.isEmpty()) {
            List<Individual> individualsWithInvalidAddress = validateAddressType(individuals);
            individualsWithInvalidAddress.forEach(individual -> {
                Error error = Error.builder().errorMessage("Invalid address").errorCode("INVALID_ADDRESS")
                        .type(ERROR_TYPE)
                        .exception(new CustomException("INVALID_ADDRESS", "Invalid address")).build();
                populateErrorDetails(individual, error, errorDetailsMap);
            });
        }
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
            addressTypeCountMap.entrySet().stream().filter(e -> e.getValue() > 1)
                    .forEach(e -> individuals.add(individual));
        }
        return individuals;
    }
}
