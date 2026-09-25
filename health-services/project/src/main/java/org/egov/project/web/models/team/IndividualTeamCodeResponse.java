package org.egov.project.web.models.team;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local view of the individual service's POST /individual/team/v1/_search response.
 *
 * The individual service keeps its team code models in its own package rather than in
 * health-services-models, so this is a deliberately minimal copy carrying only the fields this
 * service reads. Unknown properties are ignored so the individual service can add fields without
 * breaking project.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndividualTeamCodeResponse {

    @JsonProperty("TeamCodes")
    private List<IndividualTeamCode> teamCodes;

    @JsonProperty("TotalCount")
    private Long totalCount;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndividualTeamCode {

        @JsonProperty("teamCode")
        private String teamCode;

        @JsonProperty("status")
        private String status;

        @JsonProperty("assignedCount")
        private Integer assignedCount;

        @JsonProperty("members")
        private List<IndividualTeamMember> members;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndividualTeamMember {

        @JsonProperty("individualId")
        private String individualId;

        @JsonProperty("userUuid")
        private String userUuid;

        @JsonProperty("status")
        private String status;
    }
}
