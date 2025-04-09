package org.egov.common.models.idgen;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
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

    private List<IdRecord> idResponses;

}