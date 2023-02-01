package org.egov.common.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Error {
    private Exception exception;
    private String errorCode;
    private String errorMessage;
    private ErrorType type;
    private String additionalDetails;

    public enum ErrorType {
        RECOVERABLE,
        NON_RECOVERABLE
    }
}