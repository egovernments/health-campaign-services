package org.egov.referralmanagement.validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BeneficiaryDownsyncController dereferences getDownsyncCriteria() with no null guard, so a request
 * omitting the object produced an NPE (HTTP 500) rather than a 400. These tests pin both halves of
 * the fix: @NotNull on the field, and @Valid so DownsyncCriteria's own constraints actually run.
 */
public class DownsyncRequestConstraintTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Set<String> violatedPaths(DownsyncRequest request) {
        Set<ConstraintViolation<DownsyncRequest>> violations = validator.validate(request);
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void shouldRejectOmittedDownsyncCriteria() {
        DownsyncRequest request = DownsyncRequest.builder().build();
        assertTrue(violatedPaths(request).contains("downsyncCriteria"));
    }

    @Test
    void shouldCascadeIntoCriteriaAndReportEachMissingRequiredField() {
        DownsyncRequest request = DownsyncRequest.builder()
                .downsyncCriteria(DownsyncCriteria.builder().build())
                .build();
        Set<String> paths = violatedPaths(request);
        assertTrue(paths.contains("downsyncCriteria.locality"));
        assertTrue(paths.contains("downsyncCriteria.projectId"));
        assertTrue(paths.contains("downsyncCriteria.tenantId"));
    }

    @Test
    void shouldAcceptFullyPopulatedCriteria() {
        DownsyncRequest request = DownsyncRequest.builder()
                .downsyncCriteria(DownsyncCriteria.builder()
                        .locality("some-locality")
                        .projectId("some-project")
                        .tenantId("mz")
                        .build())
                .build();
        assertFalse(violatedPaths(request).stream()
                .anyMatch(path -> path.startsWith("downsyncCriteria")));
    }
}
