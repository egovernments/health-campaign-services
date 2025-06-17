package org.egov.common.models.idgen;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.egov.common.contract.response.ResponseInfo;
import java.util.*;

import lombok.*;


/**
 * <h1>IDPoolGenerationResponse</h1>
 *
 * @author Sreejith K
 *
 */


@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IDPoolGenerationResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    private ResponseInfo responseInfo;


    @JsonProperty("IdCreationResponse")
    @NotNull
    @Valid
    private List<IDPoolCreationResult> idPoolCreateResponses;

}
