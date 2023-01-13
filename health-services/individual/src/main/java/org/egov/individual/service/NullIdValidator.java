package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.identifyObjectsWithNullIds;

@Component
@Order(value = 1)
public class NullIdValidator implements Validator<IndividualRequest> {

    private final ObjectMapper objectMapper;

    @Autowired
    public NullIdValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ErrorDetails> validate(IndividualRequest request) {
        List<ErrorDetails> errorDetailsList = new ArrayList<>();
        Method idMethod = getIdMethod(request.getIndividual());
        List<Individual> indWithNullIds = identifyObjectsWithNullIds(request.getIndividual(), idMethod);
        indWithNullIds.forEach(individual -> populateErrorDetails(individual, "NULL_ID",
                "Id cannot be null", request, errorDetailsList, objectMapper));
        return errorDetailsList;
    }
}
