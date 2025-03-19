package org.egov.transformer.models.musterRoll;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.egov.common.contract.request.RequestInfo;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusterRollRequest {

    @JsonProperty("RequestInfo")
    @NotNull(message = "Request info is mandatory")
    private RequestInfo requestInfo = null;

    @JsonProperty("musterRoll")
    @NotNull(message = "Muster Roll is mandatory")
    private MusterRoll musterRoll = null;

    @JsonProperty("workflow")
    private Workflow workflow = null;
}
