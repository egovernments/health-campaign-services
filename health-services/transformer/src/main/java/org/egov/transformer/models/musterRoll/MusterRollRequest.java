package org.egov.transformer.models.musterRoll;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.*;
import org.egov.transformer.models.pgr.Workflow;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

/**
 * MusterRollRequest
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-11-14T19:58:09.415+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusterRollRequest {
    @JsonProperty("RequestInfo")
    @NotNull(message = "Request info is mandatory")
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("musterRoll")
    @NotNull(message = "Muster Roll is mandatory")
    @Valid
    private MusterRoll musterRoll = null;

    @JsonProperty("workflow")
    private Workflow workflow = null;


}

