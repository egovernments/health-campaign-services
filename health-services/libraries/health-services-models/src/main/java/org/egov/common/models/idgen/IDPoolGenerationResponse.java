package org.egov.common.models.idgen;


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

    private ResponseInfo responseInfo;

    private List<Map<String,String>> idCreationResponse;

}
