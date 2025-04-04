package org.egov.common.models.idgen;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


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

    private ResponseInfo responseInfo;

    private List<IdRecord> idResponses;

}