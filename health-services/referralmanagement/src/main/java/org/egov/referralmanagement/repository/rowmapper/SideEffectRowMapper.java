package org.egov.referralmanagement.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

@Component
public class SideEffectRowMapper implements RowMapper<SideEffect> {

    @Autowired
    ObjectMapper objectMapper;

    /**
     * Maps a row of the ResultSet to a SideEffect object.
     *
     * @param resultSet the ResultSet containing the data from the database
     * @param i         the current row number
     * @return a SideEffect object mapped from the ResultSet
     * @throws SQLException if an SQL exception occurs
     */
    @Override
    public SideEffect mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            // Mapping AuditDetails
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("createdBy"))
                    .createdTime(resultSet.getLong("createdTime"))
                    .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                    .build();

            // Mapping client AuditDetails
            AuditDetails clientAuditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("clientCreatedBy"))
                    .createdTime(resultSet.getLong("clientCreatedTime"))
                    .lastModifiedBy(resultSet.getString("clientLastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                    .build();

            // Building SideEffect object
            return SideEffect.builder()
                    .id(resultSet.getString("id"))
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .auditDetails(auditDetails)
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper
                            .readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .clientAuditDetails(clientAuditDetails)
                    .clientReferenceId(resultSet.getString("clientreferenceid"))
                    .taskId(resultSet.getString("taskId"))
                    .taskClientReferenceId(resultSet.getString("taskClientreferenceid"))
                    .projectBeneficiaryId(resultSet.getString("projectBeneficiaryId"))
                    .projectBeneficiaryClientReferenceId(resultSet.getString("projectBeneficiaryClientReferenceId"))
                    .tenantId(resultSet.getString("tenantid"))
                    // Deserializing JSON array stored in the 'symptoms' column to ArrayList<String>
                    .symptoms(resultSet.getString("symptoms") == null ? null :
                            objectMapper.readValue(resultSet.getString("symptoms"), ArrayList.class))
                    .build();
        } catch (JsonProcessingException e) {
            // Wrapping JsonProcessingException into SQLException
            throw new SQLException(e);
        }
    }
}
