package org.egov.individual.validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.egov.common.models.individual.Individual;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary tests for EgovModel.id, driving the real Jakarta validator rather than a service
 * validator. @Size alone accepted a whitespace-only id; @Pattern rejects it while still skipping
 * nulls, so creates that legitimately carry no id yet are unaffected.
 */
public class ModelConstraintTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Set<String> violatedPaths(String id) {
        Individual individual = Individual.builder().id(id).tenantId("mz").build();
        Set<ConstraintViolation<Individual>> violations = validator.validate(individual);
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void shouldRejectWhitespaceOnlyId() {
        assertTrue(violatedPaths("   ").contains("id"));
    }

    @Test
    void shouldAcceptNullIdSoCreatesAreUnaffected() {
        assertFalse(violatedPaths(null).contains("id"));
    }

    @Test
    void shouldAcceptOrdinaryId() {
        assertFalse(violatedPaths("some-id").contains("id"));
    }
}
