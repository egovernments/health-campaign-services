package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.egov.common.contract.request.RequestInfo;


@Data
public class DraftRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("DraftDetails")
    @NotNull
    @Valid
    private DraftDetails draftDetails;
}
