package org.egov.referralmanagement.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
<<<<<<<< HEAD:health-services/referralmanagement/src/main/java/org/egov/referralmanagement/repository/rowmapper/SideEffectRowMapper.java
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
========
import org.egov.common.models.referralmanagement.adverseevent.AdverseEvent;
>>>>>>>> 51cd6f6468 (HLM-3069: changed module name to referral management):health-services/referralmanagement/src/main/java/org/egov/referralmanagement/repository/rowmapper/AdverseEventRowMapper.java
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

    @Override
    public SideEffect mapRow(ResultSet resultSet, int i) throws SQLException {
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
            return SideEffect.builder()
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
