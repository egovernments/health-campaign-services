package org.egov.common.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.tracer.model.ErrorEntity;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Error {
    @JsonProperty("exception")
    private Exception exception;
    @JsonProperty("errorCode")
    private String errorCode;
    @JsonProperty("errorMessage")
    private String errorMessage;
    @JsonProperty("type")
    private ErrorType type;
    @JsonProperty("additionalDetails")
    private Object additionalDetails;

    public ErrorEntity getTracerModel() {
        return ErrorEntity.builder()
                .exception(exception)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .errorType(org.egov.tracer.model.ErrorType.NON_RECOVERABLE.valueOf(type.name()))
                .additionalDetails(additionalDetails)
                .build();
    }

    public enum ErrorType {

        RECOVERABLE,
        NON_RECOVERABLE
    }
}