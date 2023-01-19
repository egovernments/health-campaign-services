package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.web.models.Individual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface Validator<T, K> {
    Map<K, List<Error>> validate(T t);

    default void populateErrorDetails(Individual individual, Error error,
                                      Map<Individual, List<Error>> errorDetailsMap,
                                      ObjectMapper objectMapper) {
        individual.setHasErrors(Boolean.TRUE);
        if (errorDetailsMap.containsKey(individual)) {
            errorDetailsMap.get(individual).add(error);
        } else {
            List<Error> errors = new ArrayList<>();
            errors.add(error);
            errorDetailsMap.put(individual, errors);
        }
    }


}
