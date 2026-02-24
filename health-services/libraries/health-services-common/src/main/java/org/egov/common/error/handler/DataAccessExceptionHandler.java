package org.egov.common.error.handler;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.tracer.model.Error;
import org.egov.tracer.model.ErrorRes;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Collections;

/**
 * Global exception handler for DataAccessException.
 *
 * <p>
 * Catches any unhandled DataAccessException that propagates to the controller
 * level (e.g., from search operations or validators) and returns a standardized
 * DIGIT error response with the {@code QUERY_EXECUTION_ERROR} error code.
 *
 * <p>
 * This ensures a consistent error format across all operations (create, update,
 * delete, search) without requiring individual service code changes.
 */
@ControllerAdvice
@Slf4j
public class DataAccessExceptionHandler {

    private static final String ERROR_CODE = "QUERY_EXECUTION_ERROR";

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorRes> handleDataAccessException(DataAccessException ex) {
        log.error("DataAccessException caught at controller level", ex);
        Throwable rootCause = ex.getMostSpecificCause();
        String errorMessage = "Database query failed: "
                + (rootCause != null ? rootCause.getMessage() : ex.getMessage());

        Error error = new Error();
        error.setCode(ERROR_CODE);
        error.setMessage(errorMessage);
        error.setDescription(errorMessage);

        ErrorRes errorRes = new ErrorRes();
        errorRes.setResponseInfo(ResponseInfo.builder()
                .status("failed")
                .build());
        errorRes.setErrors(Collections.singletonList(error));

        return new ResponseEntity<>(errorRes, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
