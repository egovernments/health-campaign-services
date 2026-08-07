package org.egov.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import org.egov.common.models.Error;
import org.egov.tracer.model.CustomException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies persistence-facing validation that must also run for Kafka-originated service calls.
 *
 * <p>HTTP controllers invoke Jakarta validation through {@code @Valid}, but the asynchronous
 * consumers construct the same request DTOs with Jackson and therefore bypass controller
 * validation. This guardrail deliberately validates each payload independently so one malformed
 * record cannot reject its valid siblings.</p>
 */
final class PayloadGuardrail {

    static final String FIELD_VALIDATION_ERROR = "FIELD_VALIDATION_ERROR";
    static final String INVALID_DATABASE_CHARACTER = "INVALID_DATABASE_CHARACTER";
    static final String PAYLOAD_INSPECTION_ERROR = "PAYLOAD_INSPECTION_ERROR";
    static final String NULL_PAYLOAD = "NULL_PAYLOAD";

    private static final jakarta.validation.Validator BEAN_VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PayloadGuardrail() {
    }

    static <T> Map<T, List<Error>> validate(List<T> payloads, boolean enforceDtoConstraints) {
        Map<T, List<Error>> errors = new IdentityHashMap<>();
        for (T payload : payloads) {
            if (payload == null) {
                throw new CustomException(NULL_PAYLOAD, "Request contains a null record");
            }

            List<Error> payloadErrors = new ArrayList<>();
            if (enforceDtoConstraints) {
                BEAN_VALIDATOR.validate(payload).stream()
                        .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                        .map(PayloadGuardrail::beanValidationError)
                        .forEach(payloadErrors::add);
            }

            try {
                for (String path : postgresNulPaths(payload)) {
                    String message = path + " contains the NUL character, which PostgreSQL text and jsonb cannot store";
                    payloadErrors.add(error(INVALID_DATABASE_CHARACTER, message));
                }
            } catch (IllegalArgumentException exception) {
                payloadErrors.add(error(PAYLOAD_INSPECTION_ERROR,
                        "Payload could not be inspected before persistence: "
                                + exception.getClass().getSimpleName()));
            }

            if (!payloadErrors.isEmpty()) {
                errors.put(payload, payloadErrors);
            }
        }
        return errors;
    }

    private static Error beanValidationError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String message = (path.isEmpty() ? "payload" : path) + ": " + violation.getMessage();
        return error(FIELD_VALIDATION_ERROR, message);
    }

    private static Error error(String code, String message) {
        return Error.builder()
                .errorCode(code)
                .errorMessage(message)
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException(code, message))
                .build();
    }

    private static List<String> postgresNulPaths(Object payload) {
        JsonNode root = OBJECT_MAPPER.valueToTree(payload);
        List<String> paths = new ArrayList<>();
        findNul(root, "$", paths);
        return paths;
    }

    private static void findNul(JsonNode node, String path, List<String> paths) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            if (node.textValue().indexOf('\0') >= 0) {
                paths.add(path);
            }
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                findNul(node.get(index), path + "[" + index + "]", paths);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldPath = path + "." + field.getKey();
                if (field.getKey().indexOf('\0') >= 0) {
                    paths.add(fieldPath + " (field name)");
                }
                findNul(field.getValue(), fieldPath, paths);
            }
        }
    }
}
