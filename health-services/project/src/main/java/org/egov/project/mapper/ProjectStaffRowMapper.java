package org.egov.project.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.project.web.models.AdditionalFields;
import org.egov.project.web.models.ProjectStaff;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProjectStaffRowMapper implements RowMapper<ProjectStaff> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ProjectStaff mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            return ProjectStaff.builder()
                    .id(resultSet.getString("id"))
                    .tenantId(resultSet.getString("tenantid"))
                    .projectId(resultSet.getString("projectId"))
                    .userId(resultSet.getString("staffId"))
                    .startDate(resultSet.getLong("startDate"))
                    .endDate(resultSet.getLong("endDate"))
                    .additionalFields(objectMapper.readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdby"))
                            .createdTime(resultSet.getLong("createdtime"))
                            .lastModifiedBy(resultSet.getString("lastmodifiedby"))
                            .lastModifiedTime(resultSet.getLong("lastmodifiedtime"))
                            .build())
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
