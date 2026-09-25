package org.egov.individual.web.models;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * What a team code assignment actually did. Internal to the service layer - the controller turns
 * this into a TeamAssignmentResponse.
 */
@Data
@Builder
public class TeamAssignmentResult {

    private String tenantId;

    private List<TeamAssignmentEntry> assignments;

    private String teamCode;

    private int updatedCount;

    /**
     * Individuals that already carried the requested value, so nothing was written for them.
     */
    private int skippedCount;

}
