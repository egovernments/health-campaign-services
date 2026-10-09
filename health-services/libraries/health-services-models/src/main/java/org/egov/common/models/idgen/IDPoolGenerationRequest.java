package org.egov.common.models.idgen;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.egov.common.contract.request.RequestInfo;


/**
 * <h1>IDPoolGenerationRequest</h1>
 *
 * Request model for generating ID pools in batches.
 * Contains request metadata and a list of batch generation requests.
 *
 * @author Sreejith K
 *
 */


@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class IDPoolGenerationRequest {

    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("BatchRequestList")
    @NotNull
    @Valid
    @Size(min = 1)
    private List<BatchRequest> batchRequestList;

}
