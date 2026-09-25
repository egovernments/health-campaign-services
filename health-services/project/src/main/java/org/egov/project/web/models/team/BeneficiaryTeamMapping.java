package org.egov.project.web.models.team;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;

/**
 * One row of the append only PROJECT_BENEFICIARY_TEAM_MAPPING history. Published to
 * save-project-beneficiary-team-mapping-topic and inserted by egov-persister - nothing in this
 * service writes the table directly, and no row is ever updated.
 *
 * relocationReason is null for an assignment and carries the operator's reason for a relocation.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BeneficiaryTeamMapping {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("projectBeneficiaryId")
    private String projectBeneficiaryId;

    @JsonProperty("projectBeneficiaryClientReferenceId")
    private String projectBeneficiaryClientReferenceId;

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("teamCode")
    private String teamCode;

    @JsonProperty("previousTeamCode")
    private String previousTeamCode;

    @JsonProperty("relocationReason")
    private String relocationReason;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;

    @JsonProperty("rowVersion")
    private Integer rowVersion;

    @JsonProperty("isDeleted")
    private Boolean isDeleted;

}
