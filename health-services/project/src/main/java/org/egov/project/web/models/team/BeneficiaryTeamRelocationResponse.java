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
 * relocatedCount is the number of beneficiaries published, not the number the persister has
 * already applied. Confirm completion via consumer lag rather than by re-querying the team code.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BeneficiaryTeamRelocationResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("fromTeamCode")
    private String fromTeamCode;

    @JsonProperty("toTeamCode")
    private String toTeamCode;

    @JsonProperty("matchedCount")
    private Integer matchedCount;

    @JsonProperty("relocatedCount")
    private Integer relocatedCount;

    @JsonProperty("skippedCount")
    private Integer skippedCount;

}
