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
 * Attributes project beneficiaries to a team code, addressed one of two ways.
 *
 * Mode A - clientReferenceIds names the beneficiaries directly.
 * Mode B - usernames and/or userUuids name the field staff, and every beneficiary those users
 *          registered is attributed. Each named user must already be a member of teamCode.
 *
 * The two modes are mutually exclusive. projectId is required and must name a real project - it
 * bounds the write, so a mode B assignment cannot reach records the user registered under a
 * different campaign. A null or blank teamCode removes the attribution instead of setting it.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BeneficiaryTeamAssignment {

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 1, max = 1000)
    private String tenantId;

    @JsonProperty("teamCode")
    @Size(min = 1, max = 64)
    private String teamCode;

    /**
     * Mode A. ProjectBeneficiary.clientReferenceId values, not ids.
     */
    @JsonProperty("clientReferenceIds")
    private List<String> clientReferenceIds;

    @JsonProperty("usernames")
    private List<String> usernames;

    /**
     * Mode B. Matched against project_beneficiary.createdBy, which holds the egov-user uuid.
     */
    @JsonProperty("userUuids")
    private List<String> userUuids;

    @JsonProperty("projectId")
    @NotNull
    @Size(min = 1, max = 64)
    private String projectId;

}
