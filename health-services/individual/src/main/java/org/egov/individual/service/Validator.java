package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.getMethod;

public interface Validator<R, T> {
    Map<T, List<Error>> validate(R r);

    default <T> void populateErrorDetails(T payload, Error error,
                                      Map<T, List<Error>> errorDetailsMap,
                                      ObjectMapper objectMapper) {
        ReflectionUtils.invokeMethod(getMethod("setHasErrors", payload.getClass()), payload, Boolean.TRUE);
        if (errorDetailsMap.containsKey(payload)) {
            errorDetailsMap.get(payload).add(error);
        } else {
            List<Error> errors = new ArrayList<>();
            errors.add(error);
            errorDetailsMap.put(payload, errors);
        }
    }


}
