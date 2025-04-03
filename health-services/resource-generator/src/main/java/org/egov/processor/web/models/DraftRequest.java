package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.Data;
import org.egov.common.contract.request.RequestInfo;

@Data
public class DraftRequest {
    @JsonProperty("RequestInfo")
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("DraftDetails")
    @Valid
    private DraftDetails draftDetails;
}
