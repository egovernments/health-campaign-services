package org.egov.individual.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.egov.common.contract.models.AuditDetails;
import org.egov.individual.web.models.TeamCodeMapping;
import org.egov.individual.web.models.TeamCodeStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class TeamCodeMappingRowMapper implements RowMapper<TeamCodeMapping> {

    @Override
    public TeamCodeMapping mapRow(ResultSet resultSet, int i) throws SQLException {
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(resultSet.getString("createdby"))
                .lastModifiedBy(resultSet.getString("lastmodifiedby"))
                .createdTime(resultSet.getLong("createdtime"))
                .lastModifiedTime(resultSet.getLong("lastmodifiedtime"))
                .build();
        return TeamCodeMapping.builder()
                .id(resultSet.getString("id"))
                .tenantId(resultSet.getString("tenantid"))
                .teamCode(resultSet.getString("teamcode"))
                .individualId(resultSet.getString("individualid"))
                .userUuid(resultSet.getString("useruuid"))
                .status(TeamCodeStatus.fromValue(resultSet.getString("status")))
                .assignedBy(resultSet.getString("assignedby"))
                // getObject so that a mapping that was never unassigned keeps null timestamps
                // instead of reporting an assignment at epoch 0
                .assignedTime(resultSet.getObject("assignedtime", Long.class))
                .unassignedBy(resultSet.getString("unassignedby"))
                .unassignedTime(resultSet.getObject("unassignedtime", Long.class))
                .auditDetails(auditDetails)
                .rowVersion(resultSet.getInt("rowversion"))
                .isDeleted(resultSet.getBoolean("isdeleted"))
                .build();
    }
}
