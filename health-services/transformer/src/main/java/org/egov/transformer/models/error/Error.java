package org.egov.transformer.models.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


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
                .errorType(ErrorType.NON_RECOVERABLE.valueOf(type.name()))
                .additionalDetails(additionalDetails)
                .build();
    }

}