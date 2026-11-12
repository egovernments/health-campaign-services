package org.egov.project.config;

import jakarta.validation.ConstraintViolation;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskResourceValidationConfigTest {

    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new TaskResourceValidationConfig().defaultValidator();
        validator.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        validator.destroy();
    }

    private Set<String> violationPaths(TaskResource resource) {
        Task task = Task.builder().tenantId("ch").resources(Collections.singletonList(resource)).build();
        TaskBulkRequest request = TaskBulkRequest.builder().tasks(Collections.singletonList(task)).build();
        Set<ConstraintViolation<TaskBulkRequest>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void allowsNullProductVariantIdAndQuantity() {
        TaskResource resource = TaskResource.builder().tenantId("ch").isDelivered(true)
                .clientReferenceId("0ea56851-992b-11f0-a4ec-4dca1edb80b6").deliveryComment("REDOSE").build();
        Set<String> paths = violationPaths(resource);
        assertFalse(paths.contains("tasks[0].resources[0].productVariantId"), paths.toString());
        assertFalse(paths.contains("tasks[0].resources[0].quantity"), paths.toString());
    }

    @Test
    void stillEnforcesSizeOnProductVariantId() {
        TaskResource resource = TaskResource.builder().tenantId("ch").isDelivered(true)
                .productVariantId("x").quantity(1.0).build();
        assertTrue(violationPaths(resource).contains("tasks[0].resources[0].productVariantId"));
    }

    @Test
    void stillEnforcesOtherNotNullFields() {
        TaskResource resource = TaskResource.builder().isDelivered(true).build();
        assertTrue(violationPaths(resource).contains("tasks[0].resources[0].tenantId"));
    }
}
