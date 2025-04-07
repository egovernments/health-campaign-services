package org.egov.common.models.idgen;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.egov.common.contract.request.RequestInfo;


/**
 * <h1>IDPoolGenerationRequest</h1>
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
    private RequestInfo requestInfo;

    @JsonProperty("BatchRequestList")
    private List<BatchRequest> batchRequestList;

}
