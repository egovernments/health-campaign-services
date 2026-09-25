package org.egov.project.web.models.team;

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
 * Moves beneficiaries onto toTeamCode, recording why.
 *
 * Two ways to choose what moves, mutually exclusive:
 * - fromTeamCode moves every beneficiary currently attributed to that team.
 * - clientReferenceIds moves exactly those beneficiaries, whatever team they are on now.
 *
 * toTeamCode, relocationReason and projectId are all required. projectId must name a real
 * project; it bounds the move so a relocation cannot reach another campaign's records.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BeneficiaryTeamRelocation {

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 1, max = 1000)
    private String tenantId;

    @JsonProperty("fromTeamCode")
    @Size(min = 1, max = 64)
    private String fromTeamCode;

    @JsonProperty("toTeamCode")
    @NotNull
    @Size(min = 1, max = 64)
    private String toTeamCode;

    @JsonProperty("clientReferenceIds")
    private List<String> clientReferenceIds;

    @JsonProperty("relocationReason")
    @NotNull
    @Size(min = 1, max = 1000)
    private String relocationReason;

    @JsonProperty("projectId")
    @NotNull
    @Size(min = 1, max = 64)
    private String projectId;

}
