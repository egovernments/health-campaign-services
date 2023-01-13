package org.egov.individual.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.List;

public interface Validator<T> {
    List<ErrorDetails> validate(T t);

    default void populateErrorDetails(Individual individual, String errorCode, String errorMessage,
                                      IndividualRequest request, List<ErrorDetails> errorDetailsList,
                                      ObjectMapper objectMapper) {
        try {
            individual.setHasErrors(Boolean.TRUE);
            ErrorDetails errorDetails = ErrorDetails.builder()
                    .id(MDC.get("CORRELATION_ID"))
                    .status("")
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .apiDetails(ApiDetails.builder()
                            .url("/individual/v1/_update")
                            .requestBody(objectMapper.writeValueAsString(IndividualRequest.builder()
                                    .requestInfo(request.getRequestInfo())
                                    .individual(Collections.singletonList(individual))
                                    .build()))
                            .build())
                    .build();
            errorDetailsList.add(errorDetails);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }


}
