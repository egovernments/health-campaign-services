package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.project.AdditionalFields;
import org.egov.common.models.project.ProjectBeneficiary;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ProjectBeneficiaryRowMapper implements RowMapper<ProjectBeneficiary> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ProjectBeneficiary mapRow(ResultSet resultSet, int i) throws SQLException {
            try {
                AuditDetails auditDetails = AuditDetails.builder()
                        .createdBy(resultSet.getString("createdby"))
                        .createdTime(resultSet.getLong("createdtime"))
                        .lastModifiedBy(resultSet.getString("lastmodifiedby"))
                        .lastModifiedTime(resultSet.getLong("lastmodifiedtime"))
                        .build();
                AuditDetails clientAuditDetails = AuditDetails.builder()
                        .createdTime(resultSet.getLong("clientCreatedTime"))
                        .createdBy(resultSet.getString("clientCreatedBy"))
                        .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                        .lastModifiedBy(resultSet.getString("clientLastModifiedBy"))
                        .build();
            return ProjectBeneficiary.builder()
                    .id(resultSet.getString("id"))
                    .tenantId(resultSet.getString("tenantid"))
                    .projectId(resultSet.getString("projectId"))
                    .dateOfRegistration(resultSet.getLong("dateOfRegistration"))
                    .beneficiaryId(resultSet.getString("beneficiaryid"))
                    .clientReferenceId(resultSet.getString("clientreferenceid"))
                    .beneficiaryClientReferenceId(resultSet.getString("beneficiaryClientReferenceId"))
                    .additionalFields(resultSet.getString("additionalDetails") == null
                            ? null : objectMapper.readValue(resultSet.getString("additionalDetails"),
                            AdditionalFields.class)
                    )
                    .auditDetails(auditDetails)
                    .clientAuditDetails(clientAuditDetails)
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .tag(resultSet.getString("tag"))
                    .build();
            } catch (JsonProcessingException e) {
                throw new SQLException(e);
            }
    }

}