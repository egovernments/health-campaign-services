package org.egov.project.web.models.team;

import lombok.Builder;
import lombok.Data;

/**
 * What an assignment actually did. Internal to the service layer - the controller turns this into
 * a BeneficiaryTeamAssignmentResponse.
 */
@Data
@Builder
public class BeneficiaryTeamAssignmentResult {

    private String teamCode;

    private int matchedCount;

    private int updatedCount;

    private int skippedCount;

}
