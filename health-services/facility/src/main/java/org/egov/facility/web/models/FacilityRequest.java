package org.egov.facility.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.istack.internal.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;

/**
 * FacilityRequest
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-21T14:37:54.683+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacilityRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.request.RequestInfo requestInfo = null;

    @JsonProperty("Facility")
    @NotNull
    @Valid
    private Facility facility = null;


}

