package org.egov.servicerequest.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * ServiceDefinitionSearchRequest
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceDefinitionSearchRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("ServiceDefinitionCriteria")
    @Valid
    private ServiceDefinitionCriteria serviceDefinitionCriteria = null;

    @JsonProperty("Pagination")
    @Valid
    private Pagination pagination = null;

    @JsonProperty("includeDeleted")
    private boolean includeDeleted = false;
}
