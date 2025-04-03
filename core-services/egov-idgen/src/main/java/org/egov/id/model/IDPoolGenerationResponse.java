package org.egov.id.model;

import lombok.*;

import java.util.List;


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
