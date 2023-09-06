package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.project.adverseevent.AdverseEvent;
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
            return AdverseEvent.builder()
                    .id(resultSet.getString("id"))
                    .clientReferenceId(resultSet.getString("clientreferenceid"))
                    .taskId(resultSet.getString("taskId"))
                    .taskClientReferenceId(resultSet.getString("taskClientreferenceid"))
                    .tenantId(resultSet.getString("tenantid"))
                    .symptoms(resultSet.getString("symptoms") == null ? null : objectMapper.readValue(resultSet.getString("symptoms"), ArrayList.class))
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdBy"))
                            .createdTime(resultSet.getLong("createdTime"))
                            .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                            .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                            .build())
                    .clientAuditDetails(AuditDetails.builder()
                            .createdTime(resultSet.getLong("clientCreatedTime"))
                            .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                            .build())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
