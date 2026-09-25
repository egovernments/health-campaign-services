package org.egov.project.web.models.team;

import lombok.Builder;
import lombok.Data;

/**
 * What a relocation actually did. Internal to the service layer.
 */
@Data
@Builder
public class BeneficiaryTeamRelocationResult {

    private String fromTeamCode;

    private String toTeamCode;

    private int matchedCount;

    private int relocatedCount;

    /**
     * Beneficiaries that already carried toTeamCode, so nothing was written for them.
     */
    private int skippedCount;

}
