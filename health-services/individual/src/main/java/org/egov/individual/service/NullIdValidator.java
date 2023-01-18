package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.identifyObjectsWithNullIds;

@Component
@Order(value = 1)
public class NullIdValidator implements Validator<IndividualRequest, Individual> {

    private final ObjectMapper objectMapper;

    @Autowired
    public NullIdValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<Individual, ErrorDetails> validate(IndividualRequest request) {
        HashMap<Individual, ErrorDetails> errorDetailsMap = new HashMap();
        Method idMethod = getIdMethod(request.getIndividuals());
        List<Individual> indWithNullIds = identifyObjectsWithNullIds(request.getIndividuals(), idMethod);
        indWithNullIds.forEach(individual -> {
            Error error = Error.builder().errorMessage("Id cannot be null").errorCode("NULL_ID")
                    .exception(new CustomException("NULL_ID", "Id cannot be null")).build();
            populateErrorDetails(individual, error, errorDetailsMap, objectMapper);
        });
        return errorDetailsMap;
    }
}
