package org.egov.common.models.project.irs;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.egov.common.models.project.ProjectResourceSearch;

public class UserActionSearchRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.request.RequestInfo requestInfo;

    @JsonProperty("UserAction")
    @NotNull
    @Valid
    private UserActionSearch userAction;
}
