package org.egov.stock.web.controllers;

import java.util.Collections;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.Error;
import org.egov.common.utils.ResponseInfoFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CustomExceptionHandler {

    // Exception handler for HttpMessageNotReadableException
    @ExceptionHandler(HttpMessageNotReadableException.class)
    // Method to handle HttpMessageNotReadableException, takes the exception and request info as parameters
    public ResponseEntity<Object> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, RequestInfo requestInfo) {
        // Creating a response info object using ResponseInfoFactory
        ResponseInfo responseInfo = ResponseInfoFactory.createResponseInfo(requestInfo, false);
        // Creating an error object with a custom error code, message, and type
        Error error = new Error(ex, "YOUR_ERROR_CODE", "Failed to deserialize certain JSON fields", Error.ErrorType.NON_RECOVERABLE, null);
        // Creating a map containing response info and a list of errors
        // This map will be sent in the response body
        return new ResponseEntity<>(Collections.singletonMap(responseInfo, Collections.singletonList(error)), HttpStatus.BAD_REQUEST);
    }
}

