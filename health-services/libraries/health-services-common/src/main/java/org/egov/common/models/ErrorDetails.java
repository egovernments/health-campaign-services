package org.egov.common.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.tracer.model.ErrorDetail;

import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorDetails {
    private ApiDetails apiDetails;
    private List<Error> errors;

    public ErrorDetail getTracerModel() {
        return ErrorDetail.builder()
                .apiDetails(apiDetails.getTracerModel())
                .errors(errors.stream().map(Error::getTracerModel).collect(Collectors.toList()))
                .build();
    }
}


