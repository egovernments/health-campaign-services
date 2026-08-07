package org.egov.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.ErrorDetails;
import org.egov.common.validator.Validator;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Executes validators in bulk first and falls back to one record at a time when a validator throws.
 *
 * <p>The bulk pass preserves validators that intentionally compare records with each other. The
 * fallback prevents one malformed record from aborting valid siblings. If the exception cannot be
 * reproduced for any individual record, every record is failed with the original batch exception;
 * accepting the batch in that case would hide a cross-record validation defect.</p>
 */
@Slf4j
final class ValidatorIsolation {

    private ValidatorIsolation() {
    }

    @SuppressWarnings("unchecked")
    static <T, R> Map<T, ErrorDetails> validate(List<Validator<R, T>> validators,
                                                Predicate<Validator<R, T>> applicableValidators,
                                                R request,
                                                String setPayloadMethodName,
                                                boolean enforceDtoConstraints) {
        if (setPayloadMethodName == null || !setPayloadMethodName.startsWith("set")) {
            throw new IllegalArgumentException("Payload setter must start with 'set'");
        }

        String getPayloadMethodName = "get" + setPayloadMethodName.substring(3);
        List<T> requestPayloads = (List<T>) ReflectionUtils.invokeMethod(
                CommonUtils.getMethod(getPayloadMethodName, request.getClass()), request);
        List<T> originalPayloads = requestPayloads == null
                ? new ArrayList<>() : new ArrayList<>(requestPayloads);
        Method setPayloadMethod = CommonUtils.getMethod(setPayloadMethodName, request.getClass());
        Map<T, ErrorDetails> errorDetailsMap = new IdentityHashMap<>();

        // Controller @Valid does not run when Kafka consumers create DTOs with ObjectMapper.
        // Apply the same DTO contract, plus database-character checks, once per record before any
        // custom validator or enrichment can send malformed data towards the persister.
        CommonUtils.populateErrorDetails(request, errorDetailsMap,
                PayloadGuardrail.validate(originalPayloads, enforceDtoConstraints), setPayloadMethodName);

        for (Validator<R, T> validator : validators) {
            if (!applicableValidators.test(validator)) {
                continue;
            }
            try {
                Map<T, List<Error>> errors = validator.validate(request);
                CommonUtils.populateErrorDetails(request, errorDetailsMap,
                        errors == null ? Collections.emptyMap() : errors, setPayloadMethodName);
            } catch (Exception batchException) {
                log.warn("Validator {} failed for a {}-record batch; isolating per record",
                        validator.getClass().getSimpleName(), originalPayloads.size(), batchException);
                isolate(validator, request, setPayloadMethod, setPayloadMethodName, originalPayloads,
                        errorDetailsMap, batchException);
            }
        }
        return errorDetailsMap;
    }

    private static <T, R> void isolate(Validator<R, T> validator,
                                       R request,
                                       Method setPayloadMethod,
                                       String setPayloadMethodName,
                                       List<T> originalPayloads,
                                       Map<T, ErrorDetails> errorDetailsMap,
                                       Exception batchException) {
        originalPayloads.forEach(payload -> markHasErrors(payload, errorDetailsMap.containsKey(payload)));
        boolean isolatedFailure = false;
        try {
            for (T payload : originalPayloads) {
                ReflectionUtils.invokeMethod(setPayloadMethod, request, Collections.singletonList(payload));
                try {
                    Map<T, List<Error>> errors = validator.validate(request);
                    if (errors != null && !errors.isEmpty()) {
                        isolatedFailure = true;
                    }
                    CommonUtils.populateErrorDetails(request, errorDetailsMap,
                            errors == null ? Collections.emptyMap() : errors, setPayloadMethodName);
                } catch (Exception recordException) {
                    isolatedFailure = true;
                    markHasErrors(payload, true);
                    CommonUtils.populateErrorDetails(request, errorDetailsMap,
                            Collections.singletonList(payload), recordException, setPayloadMethodName);
                }
            }

            if (!isolatedFailure && !originalPayloads.isEmpty()) {
                originalPayloads.forEach(payload -> markHasErrors(payload, true));
                CommonUtils.populateErrorDetails(request, errorDetailsMap, originalPayloads,
                        batchException, setPayloadMethodName);
            }
        } finally {
            ReflectionUtils.invokeMethod(setPayloadMethod, request, originalPayloads);
        }
    }

    private static <T> void markHasErrors(T payload, boolean hasErrors) {
        ReflectionUtils.invokeMethod(CommonUtils.getMethod("setHasErrors", payload.getClass()),
                payload, hasErrors);
    }
}
