package org.egov.inbox.web.model.dss;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AggregationRequest {

    @Valid
    @JsonProperty("headers")
    private Map<String, Object> headers;

    @Valid
    @JsonProperty("aggregationRequestDto")
    private AggregateRequestDto aggregationRequestDto;

}
