package org.egov.project.web.models.team;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

/**
 * matchedCount is every beneficiary the selector resolved to. updatedCount is the ones actually
 * republished; skippedCount is the ones that already carried the requested value.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BeneficiaryTeamAssignmentResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("teamCode")
    private String teamCode;

    @JsonProperty("matchedCount")
    private Integer matchedCount;

    @JsonProperty("updatedCount")
    private Integer updatedCount;

    @JsonProperty("skippedCount")
    private Integer skippedCount;

}
