package org.egov.individual.web.models;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.springframework.validation.annotation.Validated;

/**
 * A single row of INDIVIDUAL_TEAM_CODE.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeamCode {

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

    /**
     * Derived from the live membership rows when read; never persisted, and omitted from the
     * message published to save-individual-team-code-topic, which does not map it.
     */
    @JsonProperty("status")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private TeamCodeStatus status;

    /**
     * Derived alongside {@link #status}, and likewise not persisted.
     */
    @JsonProperty("assignedCount")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer assignedCount;

    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;

    @JsonProperty("rowVersion")
    private Integer rowVersion;

    @JsonProperty("isDeleted")
    private Boolean isDeleted;

    /**
     * Populated by /team/v1/_search only when includeMembers=true, otherwise null. Never persisted.
     */
    @JsonProperty("members")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Valid
    private List<TeamCodeMapping> members;

}
