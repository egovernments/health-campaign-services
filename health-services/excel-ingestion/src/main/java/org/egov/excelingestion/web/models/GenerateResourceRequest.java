package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GenerateResourceRequest {

    @JsonProperty("RequestInfo")
    @NotNull(message = "RequestInfo is mandatory")
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("GenerateResource")
    @NotNull(message = "GenerateResource is mandatory")
    @Valid
    private GenerateResource generateResource;
}
