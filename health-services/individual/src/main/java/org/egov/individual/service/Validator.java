package org.egov.individual.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface Validator<T, K> {
    Map<K, ErrorDetails> validate(T t);

    default void populateErrorDetails(Individual individual, Error error,
                                      Map<Individual, ErrorDetails> errorDetailsMap,
                                      ObjectMapper objectMapper) {
        try {
            individual.setHasErrors(Boolean.TRUE);
            if (errorDetailsMap.containsKey(individual)) {
                errorDetailsMap.get(individual).getErrors().add(error);
            } else {
                List<Error> errors = new ArrayList<>();
                errors.add(error);
                ErrorDetails errorDetails = ErrorDetails.builder()
                        .errors(errors)
                        .apiDetails(ApiDetails.builder()
                                .methodType(MDC.get("methodType"))
                                .contentType(MDC.get("contentType"))
                                .url(MDC.get("url"))
                                .requestBody(IndividualRequest.builder()
                                        .requestInfo(objectMapper.readValue(MDC.get("requestInfo"), RequestInfo.class))
                                        .individuals(Collections.singletonList(individual))
                                        .build())
                                .build())
                        .build();
                errorDetailsMap.put(individual, errorDetails);
            }
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }


}
