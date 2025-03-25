package org.egov.common.models.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.core.Pagination;
import org.springframework.validation.annotation.Validated;



/**
 * ServiceSearchRequest
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceSearchRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("ServiceCriteria")
    @NotNull
    @Valid
    private ServiceCriteria serviceCriteria = null;

    @JsonProperty("Pagination")
    @Valid
    private Pagination pagination = null;


}
