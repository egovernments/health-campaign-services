package org.egov.individual.web.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * Criteria for /team/v1/_search. Every field is optional and they AND together; an empty criteria
 * object returns every team code in the tenant. Pass status=[UNASSIGNED] to list the codes that are
 * free to hand out.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeamCodeSearch {

    @JsonProperty("id")
    private List<String> id;

    @JsonProperty("teamCode")
    private List<String> teamCode;

    @JsonProperty("status")
    private List<TeamCodeStatus> status;

    /**
     * Narrows to the team codes these individuals currently hold, resolved against the mapping
     * table rather than the individual's additionalFields.
     */
    @JsonProperty("individualId")
    private List<String> individualId;

}
