package org.egov.referralmanagement.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * RowMapper implementation for mapping ResultSet rows to HFReferral objects.
 * This class is responsible for converting database query results into Java objects.
 *
 * @author kanishq-egov
 */
@Component
public class HFReferralRowMapper implements RowMapper<HFReferral> {

    @Autowired
    ObjectMapper objectMapper;

    /**
     * Maps a ResultSet row to an HFReferral object.
     *
     * @param resultSet The result set containing the queried data.
     * @param i         The current row number.
     * @return          An HFReferral object mapped from the ResultSet row.
     * @throws SQLException If there's an issue accessing ResultSet data.
     */
    @Override
    public HFReferral mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            // Create AuditDetails object from the ResultSet data.
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("createdBy"))
                    .createdTime(resultSet.getLong("createdTime"))
                    .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                    .build();

            // Create clientAuditDetails object from the ResultSet data.
            AuditDetails clientAuditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("clientCreatedBy"))
                    .createdTime(resultSet.getLong("clientCreatedTime"))
                    .lastModifiedBy(resultSet.getString("clientLastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                    .build();

            // Build and return HFReferral object using ResultSet data and ObjectMapper for additionalFields.
            return HFReferral.builder()
                    .id(resultSet.getString("id"))
                    .clientReferenceId(resultSet.getString("clientreferenceid"))
                    .tenantId(resultSet.getString("tenantid"))
                    .projectId(resultSet.getString("projectid"))
                    .projectFacilityId(resultSet.getString("projectfacilityid"))
                    .symptom(resultSet.getString("symptom"))
                    .symptomSurveyId(resultSet.getString("symptomsurveyid"))
                    .beneficiaryId(resultSet.getString("beneficiaryid"))
                    .referralCode(resultSet.getString("referralcode"))
                    .nationalLevelId(resultSet.getString("nationallevelid"))
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper
                            .readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .auditDetails(auditDetails)
                    .clientAuditDetails(clientAuditDetails)
                    .build();
        } catch (JsonProcessingException e) {
            // Wrap JsonProcessingException as a RuntimeException for simplicity.
            throw new RuntimeException(e);
        }
    }
}
