package org.egov.referralmanagement.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

@Component
public class AdverseEventRowMapper implements RowMapper<AdverseEvent> {

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public AdverseEvent mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("createdBy"))
                    .createdTime(resultSet.getLong("createdTime"))
                    .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                    .build();
            AuditDetails clientAuditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("clientCreatedBy"))
                    .createdTime(resultSet.getLong("clientCreatedTime"))
                    .lastModifiedBy(resultSet.getString("clientLastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                    .build();
            return AdverseEvent.builder()
                    .id(resultSet.getString("id"))
                    .clientReferenceId(resultSet.getString("clientreferenceid"))
                    .taskId(resultSet.getString("taskId"))
                    .taskClientReferenceId(resultSet.getString("taskClientreferenceid"))
                    .projectBeneficiaryId(resultSet.getString("projectBeneficiaryId"))
                    .projectBeneficiaryClientReferenceId(resultSet.getString("projectBeneficiaryClientReferenceId"))
                    .tenantId(resultSet.getString("tenantid"))
                    .symptoms(resultSet.getString("symptoms") == null ? null : objectMapper.readValue(resultSet.getString("symptoms"), ArrayList.class))
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .auditDetails(auditDetails)
                    .clientAuditDetails(clientAuditDetails)
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
