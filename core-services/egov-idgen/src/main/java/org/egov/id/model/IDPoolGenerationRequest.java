package org.egov.id.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;


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
