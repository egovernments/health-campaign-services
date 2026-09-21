package org.egov.excelingestion.exception;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.ErrorDetails;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Custom exception handler for excel-ingestion service following health services pattern
 */
@Component
@Slf4j
public class CustomExceptionHandler {

    /**
     * Generic method to create Error object
     */
    public Error createError(String errorCode, String errorMessage, Error.ErrorType errorType) {
        // Use the overloaded method with a generic RuntimeException for consistency
        return createError(errorCode, errorMessage, errorType, new RuntimeException("Error object creation: " + errorMessage));
    }
    
    /**
     * Create Error object with original exception
     */
    public Error createError(String errorCode, String errorMessage, Error.ErrorType errorType, Exception originalException) {
        // Create custom exception using 3-parameter pattern
        String detailedMessage = errorMessage;
        if (originalException != null && originalException.getMessage() != null) {
            detailedMessage = errorMessage + "::: " + originalException.getMessage();
        }
        CustomException exception = new CustomException(errorCode, detailedMessage);
        return Error.builder()
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .type(errorType)
                .exception(exception)
                .build();
    }

    /**
     * Create Error with formatted message
     */
    public Error createError(String errorCode, String errorMessageTemplate, String param, Error.ErrorType errorType) {
        String errorMessage = errorMessageTemplate.replace("{0}", param);
        return createError(errorCode, errorMessage, errorType);
    }

    /**
     * Create ErrorDetails from Error
     */
    public ErrorDetails createErrorDetails(Error error) {
        return ErrorDetails.builder()
                .errors(Collections.singletonList(error))
                .build();
    }

    /**
     * Handle errors and throw CustomException if needed
     * Following the same pattern as other health services
     */
    public void handleErrors(Map<Object, ErrorDetails> errorDetailsMap, boolean isBulk, String errorCode) {
        if (!errorDetailsMap.isEmpty()) {
            if (isBulk) {
                // For bulk operations, log but continue processing
                log.warn("Errors found in bulk operation: {}", errorDetailsMap.size());
            } else {
                // For single operations, throw exception immediately
                ErrorDetails errorDetails = errorDetailsMap.values().iterator().next();
                List<Error> errors = errorDetails.getErrors();
                if (!errors.isEmpty()) {
                    Error error = errors.get(0);
                    throw (CustomException) error.getException();
                }
            }
        }
    }

    /**
     * Throw CustomException directly - used when immediate failure is needed
     */
    public void throwCustomException(String errorCode, String errorMessage) {
        // Use 3-parameter version for consistency - create generic RuntimeException
        throwCustomException(errorCode, errorMessage, new RuntimeException("Generic error: " + errorMessage));
    }

    /**
     * Throw CustomException with formatted message
     */
    public void throwCustomException(String errorCode, String errorMessageTemplate, String param) {
        String errorMessage = errorMessageTemplate.replace("{0}", param);
        // Use 3-parameter version for consistency - create generic RuntimeException
        throwCustomException(errorCode, errorMessage, new RuntimeException("Formatted error: " + errorMessage));
    }
    
    /**
     * Throw CustomException with error details from original exception
     * Combines error message with exception details for description
     */
    public void throwCustomException(String errorCode, String errorMessage, Exception originalException) {
        String detailedMessage = errorMessage;
        if (originalException != null && originalException.getMessage() != null) {
            detailedMessage = errorMessage + "::: " + originalException.getMessage();
        }
        throw new CustomException(errorCode, detailedMessage);
    }
}