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
public class ProcessResourceRequest {
    
    @JsonProperty("RequestInfo")
    @NotNull(message = "INGESTION_REQUEST_INFO_MANDATORY")
    @Valid
    private RequestInfo requestInfo;
    
    @JsonProperty("ResourceDetails")
    @NotNull(message = "INGESTION_RESOURCE_DETAILS_MANDATORY")
    @Valid
    private ProcessResource resourceDetails;
}