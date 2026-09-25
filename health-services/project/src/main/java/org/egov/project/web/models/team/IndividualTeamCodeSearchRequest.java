package org.egov.project.web.models.team;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

/**
 * Request body for the individual service's POST /individual/team/v1/_search. Mirrors that
 * service's TeamCodeSearchRequest - see IndividualTeamCodeResponse for why it is duplicated here.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IndividualTeamCodeSearchRequest {

    @JsonProperty("RequestInfo")
    private RequestInfo requestInfo;

    @JsonProperty("TeamCodeSearch")
    private TeamCodeSearch teamCodeSearch;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TeamCodeSearch {

        @JsonProperty("teamCode")
        private List<String> teamCode;
    }
}
