package org.egov.individual.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.individual.web.models.TeamCode;
import org.egov.individual.web.models.TeamCodeStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class TeamCodeRowMapper implements RowMapper<TeamCode> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TeamCode mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("createdby"))
                    .lastModifiedBy(resultSet.getString("lastmodifiedby"))
                    .createdTime(resultSet.getLong("createdtime"))
                    .lastModifiedTime(resultSet.getLong("lastmodifiedtime"))
                    .build();
            return TeamCode.builder()
                    .id(resultSet.getString("id"))
                    .teamCode(resultSet.getString("teamcode"))
                    .tenantId(resultSet.getString("tenantid"))
                    // both derived: individual_team_code no longer stores them, because state
                    // kept in step with an asynchronously written table drifts
                    .status(deriveStatus(resultSet.getBoolean("isdeleted"), resultSet.getInt("assignedcount")))
                    .assignedCount(resultSet.getInt("assignedcount"))
                    .additionalFields(resultSet.getString("additionaldetails") == null ? null :
                            objectMapper.readValue(resultSet.getString("additionaldetails"),
                                    AdditionalFields.class)
                    )
                    .auditDetails(auditDetails)
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }

    /**
     * A retired code reads as INACTIVE; otherwise it is ASSIGNED while anyone is on it.
     */
    private TeamCodeStatus deriveStatus(boolean isDeleted, int assignedCount) {
        if (isDeleted) {
            return TeamCodeStatus.INACTIVE;
        }
        return assignedCount > 0 ? TeamCodeStatus.ASSIGNED : TeamCodeStatus.UNASSIGNED;
    }
}
