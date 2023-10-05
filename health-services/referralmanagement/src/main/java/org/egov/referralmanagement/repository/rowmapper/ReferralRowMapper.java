package org.egov.referralmanagement.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
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
                        .createdBy(resultSet.getString("se.createdBy"))
                        .createdTime(resultSet.getLong("se.createdTime"))
                        .lastModifiedBy(resultSet.getString("se.lastModifiedBy"))
                        .lastModifiedTime(resultSet.getLong("se.lastModifiedTime"))
                        .build();
                AuditDetails sideEffectClientAuditDetails = AuditDetails.builder()
                        .createdTime(resultSet.getLong("se.clientCreatedTime"))
                        .lastModifiedTime(resultSet.getLong("se.clientLastModifiedTime"))
                        .build();
                sideEffect = SideEffect.builder()
                        .id(resultSet.getString("se.id"))
                        .clientReferenceId(resultSet.getString("se.clientreferenceid"))
                        .taskId(resultSet.getString("se.taskId"))
                        .taskClientReferenceId(resultSet.getString("se.taskClientreferenceid"))
                        .tenantId(resultSet.getString("se.tenantid"))
                        .symptoms(resultSet.getString("se.symptoms") == null ? null : objectMapper.readValue(resultSet.getString("se.symptoms"), ArrayList.class))
                        .rowVersion(resultSet.getInt("se.rowversion"))
                        .isDeleted(resultSet.getBoolean("se.isdeleted"))
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
                    .referredById(resultSet.getString("referredById"))
                    .referredToId(resultSet.getString("referredToId"))
                    .referredToType(resultSet.getString("referredToType"))
                    .sideEffect(sideEffect)
                    .tenantId(resultSet.getString("tenantid"))
                    .reasons(resultSet.getString("reasons") == null ? null : objectMapper.readValue(resultSet.getString("reasons"), ArrayList.class))
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
