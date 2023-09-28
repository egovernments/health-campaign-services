package org.egov.referralmanagement.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEvent;
import org.egov.common.models.referralmanagement.Referral;
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
            AdverseEvent adverseEvent = null;
            String adverseEventClientReferenceId = resultSet.getString("adverseEventClientReferenceId");
            if(adverseEventClientReferenceId != null) {
                AuditDetails adverseEventAuditDetails = AuditDetails.builder()
                        .createdBy(resultSet.getString("ae.createdBy"))
                        .createdTime(resultSet.getLong("ae.createdTime"))
                        .lastModifiedBy(resultSet.getString("ae.lastModifiedBy"))
                        .lastModifiedTime(resultSet.getLong("ae.lastModifiedTime"))
                        .build();
                AuditDetails adverseEventClientAuditDetails = AuditDetails.builder()
                        .createdTime(resultSet.getLong("ae.clientCreatedTime"))
                        .lastModifiedTime(resultSet.getLong("ae.clientLastModifiedTime"))
                        .build();
                adverseEvent = AdverseEvent.builder()
                        .id(resultSet.getString("ae.id"))
                        .clientReferenceId(resultSet.getString("ae.clientreferenceid"))
                        .taskId(resultSet.getString("ae.taskId"))
                        .taskClientReferenceId(resultSet.getString("ae.taskClientreferenceid"))
                        .tenantId(resultSet.getString("ae.tenantid"))
                        .symptoms(resultSet.getString("ae.symptoms") == null ? null : objectMapper.readValue(resultSet.getString("ae.symptoms"), ArrayList.class))
                        .rowVersion(resultSet.getInt("ae.rowversion"))
                        .isDeleted(resultSet.getBoolean("ae.isdeleted"))
                        .auditDetails(adverseEventAuditDetails)
                        .clientAuditDetails(adverseEventClientAuditDetails)
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
                    .adverseEvent(adverseEvent)
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
