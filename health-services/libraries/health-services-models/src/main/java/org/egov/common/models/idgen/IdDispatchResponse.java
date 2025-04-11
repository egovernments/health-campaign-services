package org.egov.common.models.idgen;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.egov.common.contract.response.ResponseInfo;


/**
 * <h1>IdGenerationResponse</h1>
 *
 * @author Narendra
 *
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IdDispatchResponse {

    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo;

    @JsonProperty("idResponses")
    private List<IdRecord> idResponses;

    @JsonProperty("TotalCount")
    @Getter(AccessLevel.NONE)
    private Long totalCount;

    @JsonProperty("FetchLimit")
    private Long fetchLimit;

    public Long getTotalCount() {
        if(totalCount == null)
            totalCount = (long) idResponses.size();
        return totalCount;
    }

}