package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.project.web.models.AdditionalFields;
import org.egov.project.web.models.Project;
import org.egov.project.web.models.ProjectStaff;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ProjectRowMapper implements RowMapper<Project> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Project mapRow(ResultSet resultSet, int i) throws SQLException {
 //       try {
            return Project.builder()
                    .id(resultSet.getString("id"))
                    .tenantId(resultSet.getString("tenantid"))
                    .startDate(resultSet.getLong("startDate"))
                    .endDate(resultSet.getLong("endDate"))
//                    .additionalFields(
//                            resultSet.getString("additionalDetails") == null
//                                    ? null
//                                    : objectMapper.readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdby"))
                            .createdTime(resultSet.getLong("createdtime"))
                            .lastModifiedBy(resultSet.getString("lastmodifiedby"))
                            .lastModifiedTime(resultSet.getLong("lastmodifiedtime"))
                            .build())
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .build();
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }
    }
}