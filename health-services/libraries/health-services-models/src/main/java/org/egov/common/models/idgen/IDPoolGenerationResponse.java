package org.egov.common.models.idgen;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


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
public class IDPoolGenerationResponse {

    private ResponseInfo responseInfo;

    private List<IdResponse> idResponses;

}
