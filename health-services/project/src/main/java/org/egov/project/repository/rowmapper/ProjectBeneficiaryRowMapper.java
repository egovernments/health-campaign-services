package org.egov.project.repository.rowmapper;

import digit.models.coremodels.AuditDetails;
import org.egov.project.web.models.ProjectBeneficiary;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ProjectBeneficiaryRowMapper implements RowMapper<ProjectBeneficiary> {

    @Override
    public ProjectBeneficiary mapRow(ResultSet resultSet, int i) throws SQLException {

            return ProjectBeneficiary.builder()
                    .id(resultSet.getString("id"))
                    .tenantId(resultSet.getString("tenantid"))
                    .projectId(resultSet.getString("projectId"))
                    .dateOfRegistration(resultSet.getInt("dateOfRegistration"))
                    .beneficiaryId(resultSet.getString("beneficiaryid"))
                    .clientReferenceId(resultSet.getString("clientreferenceid"))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdby"))
                            .createdTime(resultSet.getLong("createdtime"))
                            .lastModifiedBy(resultSet.getString("lastmodifiedby"))
                            .lastModifiedTime(resultSet.getLong("lastmodifiedtime"))
                            .build())
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .build();
    }

}