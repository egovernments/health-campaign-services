package org.egov.stock.web.controllers;

import org.egov.common.models.core.validator.QuantityValidationException;
import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.Error;
import org.egov.common.utils.ResponseInfoFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Collections;

@RestControllerAdvice
public class CustomExceptionHandler {

    @ExceptionHandler(QuantityValidationException.class)
    public ResponseEntity<Object> handleQuantityValidationException(QuantityValidationException ex, RequestInfo requestInfo) {
        ResponseInfo responseInfo = ResponseInfoFactory.createResponseInfo(requestInfo, false);
        Error error = new Error(ex, "YOUR_ERROR_CODE", ex.getMessage(), Error.ErrorType.NON_RECOVERABLE, null);
        return new ResponseEntity<>(Collections.singletonMap(responseInfo,  Collections.singletonList(error)), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, RequestInfo requestInfo) {
        ResponseInfo responseInfo = ResponseInfoFactory.createResponseInfo(requestInfo, false);
        Error error = new Error(ex, "YOUR_ERROR_CODE", "Failed to deserialize certain JSON fields", Error.ErrorType.NON_RECOVERABLE, null);
        return new ResponseEntity<>(Collections.singletonMap(responseInfo, Collections.singletonList(error)), HttpStatus.BAD_REQUEST);
    }

    // Add other exception handlers as needed
}

