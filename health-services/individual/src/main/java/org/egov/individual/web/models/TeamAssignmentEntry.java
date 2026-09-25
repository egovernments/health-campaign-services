package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * What happened to one individual in a team assignment.
 *
 * All three identifiers are echoed regardless of which selector the caller used, so the value
 * they sent is always present to match on. A bare list of resolved ids could not serve that: the
 * individuals are loaded by id, so their order is the database's rather than the request's.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeamAssignmentEntry {

    @JsonProperty("individualId")
    private String individualId;

    @JsonProperty("userUuid")
    private String userUuid;

    @JsonProperty("username")
    private String username;

    /**
     * UPDATED when the individual was republished, SKIPPED when it already carried the requested
     * team code and nothing was written for it.
     */
    @JsonProperty("status")
    private String status;

}
