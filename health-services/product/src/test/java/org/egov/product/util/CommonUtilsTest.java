package org.egov.product.util;

import lombok.Builder;
import lombok.Data;
import org.egov.product.web.models.ApiOperation;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonUtilsTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("should check if the request is for update")
    void shouldCheckIfTheRequestIsForUpdate() {
        SomeRequest someRequest = SomeRequest.builder().apiOperation(ApiOperation.UPDATE).build();

        assertTrue(CommonUtils.isForUpdate(someRequest));
    }

    @Test
    @DisplayName("should handle for null api operation")
    void shouldHandleForNullForNullApiOperation() {
        SomeRequest someRequest = SomeRequest.builder().apiOperation(null).build();

        assertFalse(CommonUtils.isForUpdate(someRequest));
    }

    @Test
    @DisplayName("should throw custom exception if invalid object is passed for update")
    void shouldThrowCustomExceptionIfInvalidObjectIsPassedForUpdate() {
        List<String> list = new ArrayList<>();

        assertThrows(CustomException.class, () -> CommonUtils.isForUpdate(list));
    }

    @Test
    @DisplayName("should check if the request is for Delete")
    void shouldCheckIfTheRequestIsForDelete() {
        SomeRequest someRequest = SomeRequest.builder().apiOperation(ApiOperation.DELETE).build();

        assertTrue(CommonUtils.isForDelete(someRequest));
    }

    @Test
    @DisplayName("should check if the request is for create")
    void shouldCheckIfTheRequestIsForCreate() {
        SomeRequest someRequest = SomeRequest.builder().apiOperation(ApiOperation.CREATE).build();

        assertTrue(CommonUtils.isForCreate(someRequest));
    }

    @Test
    @DisplayName("should check if the request is for create when api operation is null")
    void shouldCheckIfTheRequestIsForCreateWhenApiOperationIsNull() {
        SomeRequest someRequest = SomeRequest.builder().apiOperation(null).build();

        assertTrue(CommonUtils.isForCreate(someRequest));
    }

    @Data
    @Builder
    public static class SomeRequest {
        private ApiOperation apiOperation;
    }
}