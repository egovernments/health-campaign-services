package org.egov.common.models.project.irs;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class LocationCaptureSearchRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.request.RequestInfo requestInfo;

    @JsonProperty("LocationCapture")
    @NotNull
    @Valid
    private LocationCaptureSearch locationCapture;
}
