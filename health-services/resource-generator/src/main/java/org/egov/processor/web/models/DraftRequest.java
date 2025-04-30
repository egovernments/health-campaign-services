package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.Data;
import org.egov.common.contract.request.RequestInfo;
import javax.validation.constraints.NotNull;

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
