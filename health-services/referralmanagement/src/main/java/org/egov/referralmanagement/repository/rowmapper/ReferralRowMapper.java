package org.egov.referralmanagement.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

@Component
public class ReferralRowMapper implements RowMapper<Referral> {

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public Referral mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            SideEffect sideEffect = null;
            String sideEffectClientReferenceId = resultSet.getString("sideEffectClientReferenceId");
            if(sideEffectClientReferenceId != null) {
                AuditDetails sideEffectAuditDetails = AuditDetails.builder()
                        .createdBy(resultSet.getString("sCreatedBy"))
                        .createdTime(resultSet.getLong("sCreatedTime"))
                        .lastModifiedBy(resultSet.getString("sLastModifiedBy"))
                        .lastModifiedTime(resultSet.getLong("sLastModifiedTime"))
                        .build();
                AuditDetails sideEffectClientAuditDetails = AuditDetails.builder()
                        .createdBy(resultSet.getString("sClientCreatedBy"))
                        .createdTime(resultSet.getLong("sClientCreatedTime"))
                        .lastModifiedBy(resultSet.getString("sClientLastModifiedBy"))
                        .lastModifiedTime(resultSet.getLong("sClientLastModifiedTime"))
                        .build();
                sideEffect = SideEffect.builder()
                        .id(resultSet.getString("sId"))
                        .clientReferenceId(resultSet.getString("sClientReferenceId"))
                        .taskId(resultSet.getString("sTaskId"))
                        .taskClientReferenceId(resultSet.getString("sTaskClientReferenceId"))
                        .projectBeneficiaryId(resultSet.getString("sProjectBeneficiaryId"))
                        .projectBeneficiaryClientReferenceId(resultSet.getString("sProjectBeneficiaryClientReferenceId"))
                        .tenantId(resultSet.getString("sTenantId"))
                        .symptoms(resultSet.getString("sSymptoms") == null ? null : objectMapper
                                .readValue(resultSet.getString("sSymptoms"), ArrayList.class))
                        .additionalFields(resultSet.getString("sAdditionalDetails") == null ? null : objectMapper
                                .readValue(resultSet.getString("sAdditionalDetails"), AdditionalFields.class))
                        .rowVersion(resultSet.getInt("sRowVersion"))
                        .isDeleted(resultSet.getBoolean("sIsDeleted"))
                        .auditDetails(sideEffectAuditDetails)
                        .clientAuditDetails(sideEffectClientAuditDetails)
                        .build();
            }
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("createdBy"))
                    .createdTime(resultSet.getLong("createdTime"))
                    .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                    .build();
            AuditDetails clientAuditDetails= AuditDetails.builder()
                    .createdBy(resultSet.getString("clientCreatedBy"))
                    .createdTime(resultSet.getLong("clientCreatedTime"))
                    .lastModifiedBy(resultSet.getString("clientLastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                    .build();
            return Referral.builder()
                    .id(resultSet.getString("id"))
                    .clientReferenceId(resultSet.getString("clientreferenceid"))
                    .projectBeneficiaryId(resultSet.getString("projectBeneficiaryId"))
                    .projectBeneficiaryClientReferenceId(resultSet.getString("projectbeneficiaryclientreferenceid"))
                    .referrerId(resultSet.getString("referrerId"))
                    .recipientId(resultSet.getString("recipientId"))
                    .recipientType(resultSet.getString("recipientType"))
                    .sideEffect(sideEffect)
                    .referralCode(resultSet.getString("referralCode"))
                    .tenantId(resultSet.getString("tenantid"))
                    .reasons(resultSet.getString("reasons") == null ? null : objectMapper.readValue(resultSet.getString("reasons"), ArrayList.class))
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper
                            .readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .auditDetails(auditDetails)
                    .clientAuditDetails(clientAuditDetails)
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
