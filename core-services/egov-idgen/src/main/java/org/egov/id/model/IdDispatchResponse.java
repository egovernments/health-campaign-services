package org.egov.id.model;

import lombok.*;

import java.util.List;


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
public class IdDispatchResponse {

    private ResponseInfo responseInfo;

    private List<DispatchedId> idResponses;

}