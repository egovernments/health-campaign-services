package org.egov.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.tracer.model.CustomException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Applies persistence-facing validation that must also run for Kafka-originated service calls.
 *
 * <p>HTTP controllers invoke Jakarta validation through {@code @Valid}, but the asynchronous
 * consumers construct the same request DTOs with Jackson and therefore bypass controller
 * validation. This guardrail deliberately validates each payload independently so one malformed
 * record cannot reject its valid siblings.</p>
 */
@Slf4j
final class PayloadGuardrail {

    static final String FIELD_VALIDATION_ERROR = "FIELD_VALIDATION_ERROR";
    static final String INVALID_DATABASE_CHARACTER = "INVALID_DATABASE_CHARACTER";
    static final String PAYLOAD_INSPECTION_ERROR = "PAYLOAD_INSPECTION_ERROR";
    static final String NULL_PAYLOAD = "NULL_PAYLOAD";

    /** Deployment gate for DTO constraint enforcement: {@code off}, {@code log} or {@code enforce}. */
    static final String MODE_ENV = "HCM_GUARDRAIL_DTO_CONSTRAINTS";
    static final String MODE_PROPERTY = "hcm.guardrail.dto-constraints";
    private static final String MODE_OFF = "off";
    private static final String MODE_LOG = "log";
    private static final String MODE_ENFORCE = "enforce";

    private static final jakarta.validation.Validator BEAN_VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODE = resolveMode();

    private PayloadGuardrail() {
    }

    /**
     * Whether bean-validation failures reject a record, defaulting to NO.
     *
     * <p>Nothing ran these DTO constraints on the consumer path before, so enforcing them as part
     * of the image would start rejecting field payloads the pipeline accepted yesterday — across
     * every service that calls {@code CommonUtils.validate}, not just the one being fixed. The
     * default therefore reports what it would have rejected and rejects nothing; flip the
     * environment variable to {@code enforce} once those counts are known to be zero.</p>
     *
     * <p>Read from the environment rather than a Spring {@code @Value} because the call site is
     * static, and as an environment variable rather than a system property because the distroless
     * images ignore {@code JAVA_OPTS}, so a {@code -D} flag never reaches the process.</p>
     */
    static boolean enforceDtoConstraintsByDefault() {
        return MODE_ENFORCE.equals(MODE);
    }

    private static String resolveMode() {
        String value = System.getenv(MODE_ENV);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(MODE_PROPERTY);
        }
        if (value == null || value.trim().isEmpty()) {
            return MODE_LOG;
        }
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        if (!MODE_OFF.equals(normalised) && !MODE_LOG.equals(normalised)
                && !MODE_ENFORCE.equals(normalised)) {
            log.warn("Unrecognised {}='{}'; using '{}'", MODE_ENV, value, MODE_LOG);
            return MODE_LOG;
        }
        return normalised;
    }

    static <T> Map<T, List<Error>> validate(List<T> payloads, boolean enforceDtoConstraints) {
        Map<T, List<Error>> errors = new IdentityHashMap<>();
        int observedRecords = 0;
        int observedViolations = 0;
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
            } else if (MODE_LOG.equals(MODE)) {
                // Report-only: measure what enforcement would cost before anyone turns it on.
                // Counted per batch rather than logged per violation, so a 20k-record bulk request
                // cannot turn this into 20k log lines.
                int violations = BEAN_VALIDATOR.validate(payload).size();
                if (violations > 0) {
                    observedRecords++;
                    observedViolations += violations;
                }
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
        if (observedRecords > 0) {
            log.warn("DTO constraint guardrail is NOT enforcing: {} of {} record(s) carry {} "
                            + "constraint violation(s) and would be rejected if {}=enforce",
                    observedRecords, payloads.size(), observedViolations, MODE_ENV);
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
