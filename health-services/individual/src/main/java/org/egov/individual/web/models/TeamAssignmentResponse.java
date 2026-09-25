package org.egov.individual.web.models;

import java.util.List;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

/**
 * Result of a team code assignment. assignments lists every individual the selector resolved to,
 * each echoing all three identifiers so the caller can match on whichever one it sent, plus
 * whether that individual was written or skipped. updatedCount and skippedCount are the totals.
 */
@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeamAssignmentResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("assignments")
    private List<TeamAssignmentEntry> assignments;

    @JsonProperty("updatedCount")
    private Integer updatedCount;

    @JsonProperty("skippedCount")
    private Integer skippedCount;

    @JsonProperty("teamCode")
    private String teamCode;

}
