package org.egov.individual.web.models;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;

/**
 * A single row of INDIVIDUAL_TEAM_CODE_MAPPING - which individual carries which team code, when it
 * was assigned and by whom, and the same for the unassignment that ended it.
 *
 * There is at most one row per (tenantId, teamCode, individualId); re-assigning a code the
 * individual previously held flips the same row back to ASSIGNED rather than inserting a second.
 * Only ASSIGNED and UNASSIGNED are used here - INACTIVE is a team code lifecycle state, not a
 * membership state.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeamCodeMapping {

    @JsonProperty("id")
    @Size(min = 1, max = 64)
    private String id;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 1, max = 1000)
    private String tenantId;

    @JsonProperty("teamCode")
    @NotNull
    @Size(min = 1, max = 64)
    private String teamCode;

    @JsonProperty("individualId")
    @NotNull
    @Size(min = 1, max = 64)
    private String individualId;

    @JsonProperty("userUuid")
    @Size(min = 1, max = 64)
    private String userUuid;

    @JsonProperty("status")
    private TeamCodeStatus status;

    @JsonProperty("assignedBy")
    @Size(min = 1, max = 64)
    private String assignedBy;

    @JsonProperty("assignedTime")
    private Long assignedTime;

    @JsonProperty("unassignedBy")
    @Size(min = 1, max = 64)
    private String unassignedBy;

    @JsonProperty("unassignedTime")
    private Long unassignedTime;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;

    @JsonProperty("rowVersion")
    private Integer rowVersion;

    @JsonProperty("isDeleted")
    private Boolean isDeleted;

}
