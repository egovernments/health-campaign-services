package org.egov.excelingestion.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.egov.excelingestion.web.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SearchValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldFailValidationForMissingTenantIdInProcessSearch() {
        // Given
        ProcessingSearchCriteria criteria = ProcessingSearchCriteria.builder()
                // tenantId is missing - should trigger validation error
                .limit(10)
                .offset(0)
                .build();

        // When
        Set<ConstraintViolation<ProcessingSearchCriteria>> violations = validator.validate(criteria);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> 
            v.getPropertyPath().toString().equals("tenantId") &&
            v.getMessage().contains("INGEST_MISSING_TENANT_ID")
        ));
    }

    @Test
    void shouldFailValidationForNegativeLimitInProcessSearch() {
        // Given
        ProcessingSearchCriteria criteria = ProcessingSearchCriteria.builder()
                .tenantId("dev")
                .limit(-5) // Negative limit should trigger validation error
                .offset(0)
                .build();

        // When
        Set<ConstraintViolation<ProcessingSearchCriteria>> violations = validator.validate(criteria);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> 
            v.getPropertyPath().toString().equals("limit") &&
            v.getMessage().contains("INGEST_INVALID_LIMIT")
        ));
    }

    @Test
    void shouldFailValidationForNegativeOffsetInProcessSearch() {
        // Given
        ProcessingSearchCriteria criteria = ProcessingSearchCriteria.builder()
                .tenantId("dev")
                .limit(10)
                .offset(-10) // Negative offset should trigger validation error
                .build();

        // When
        Set<ConstraintViolation<ProcessingSearchCriteria>> violations = validator.validate(criteria);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> 
            v.getPropertyPath().toString().equals("offset") &&
            v.getMessage().contains("INGEST_INVALID_OFFSET")
        ));
    }

    @Test
    void shouldFailValidationForMissingTenantIdInGenerationSearch() {
        // Given
        GenerationSearchCriteria criteria = GenerationSearchCriteria.builder()
                // tenantId is missing - should trigger validation error
                .limit(10)
                .offset(0)
                .build();

        // When
        Set<ConstraintViolation<GenerationSearchCriteria>> violations = validator.validate(criteria);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> 
            v.getPropertyPath().toString().equals("tenantId") &&
            v.getMessage().contains("INGEST_MISSING_TENANT_ID")
        ));
    }

    @Test
    void shouldFailValidationForNegativeLimitInGenerationSearch() {
        // Given
        GenerationSearchCriteria criteria = GenerationSearchCriteria.builder()
                .tenantId("dev")
                .limit(-3) // Negative limit should trigger validation error
                .offset(0)
                .build();

        // When
        Set<ConstraintViolation<GenerationSearchCriteria>> violations = validator.validate(criteria);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> 
            v.getPropertyPath().toString().equals("limit") &&
            v.getMessage().contains("INGEST_INVALID_LIMIT")
        ));
    }

    @Test
    void shouldPassValidationForZeroLimitAndOffset() {
        // Given
        ProcessingSearchCriteria criteria = ProcessingSearchCriteria.builder()
                .tenantId("dev")
                .limit(0) // Zero should be valid
                .offset(0) // Zero should be valid
                .build();

        // When
        Set<ConstraintViolation<ProcessingSearchCriteria>> violations = validator.validate(criteria);

        // Then
        assertTrue(violations.isEmpty(), "Zero values should be valid");
    }

    @Test
    void shouldPassValidationForValidProcessSearchCriteria() {
        // Given
        ProcessingSearchCriteria criteria = ProcessingSearchCriteria.builder()
                .tenantId("dev")
                .limit(50)
                .offset(10)
                .build();

        // When
        Set<ConstraintViolation<ProcessingSearchCriteria>> violations = validator.validate(criteria);

        // Then
        assertTrue(violations.isEmpty(), "Valid criteria should pass validation");
    }

    @Test
    void shouldFailValidationForMissingRequiredFieldsInProcessResource() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                // Missing required fields: tenantId, type, hierarchyType, referenceId, fileStoreId
                .build();

        // When
        Set<ConstraintViolation<ProcessResource>> violations = validator.validate(resource);

        // Then
        assertFalse(violations.isEmpty());
        
        // Check for specific required field violations
        assertTrue(violations.stream().anyMatch(v -> 
            v.getPropertyPath().toString().equals("tenantId")));
        assertTrue(violations.stream().anyMatch(v -> 
            v.getPropertyPath().toString().equals("type")));
        assertTrue(violations.stream().anyMatch(v -> 
            v.getPropertyPath().toString().equals("hierarchyType")));
        assertTrue(violations.stream().anyMatch(v -> 
            v.getPropertyPath().toString().equals("referenceId")));
        assertTrue(violations.stream().anyMatch(v -> 
            v.getPropertyPath().toString().equals("fileStoreId")));
    }

    @Test
    void shouldPassValidationForCompleteProcessResource() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .tenantId("dev")
                .type("EXCEL_IMPORT")
                .hierarchyType("ADMIN")
                .referenceId("ref-123")
                .fileStoreId("file-456")
                .build();

        // When
        Set<ConstraintViolation<ProcessResource>> violations = validator.validate(resource);

        // Then
        assertTrue(violations.isEmpty(), "Complete resource should pass validation");
    }
}