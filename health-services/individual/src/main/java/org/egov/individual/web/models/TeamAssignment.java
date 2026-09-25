package org.egov.individual.web.models;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * Team code assignment for one or more individuals - a whole team shares a code, so the selectors
 * are lists. Exactly one of individualId, username or userUuid may be populated, and every value
 * in it must resolve to exactly one system user. A null or blank teamCode removes the assignment
 * from all of them.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeamAssignment {

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 1, max = 1000)
    private String tenantId;

    @JsonProperty("individualId")
    private List<String> individualId;

    @JsonProperty("username")
    private List<String> username;

    @JsonProperty("userUuid")
    private List<String> userUuid;

    @JsonProperty("teamCode")
    @Size(min = 1, max = 64)
    private String teamCode;

}
