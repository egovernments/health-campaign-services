package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.project.Project;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ProjectRowMapper implements RowMapper<Project> {

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public Project mapRow(ResultSet resultSet, int i) throws SQLException {
    return Project.builder()
      .id(resultSet.getString("id"))
      .tenantId(resultSet.getString("tenantid"))
      .startDate(resultSet.getLong("startDate"))
      .endDate(resultSet.getLong("endDate"))
      .projectTypeId(resultSet.getString("projectTypeId"))
      .additionalDetails(getAdditionalDetail("additionalDetails",resultSet))
      .auditDetails(AuditDetails.builder()
        .createdBy(resultSet.getString("createdby"))
        .createdTime(resultSet.getLong("createdtime"))
        .lastModifiedBy(resultSet.getString("lastmodifiedby"))
        .lastModifiedTime(resultSet.getLong("lastmodifiedtime"))
        .build())
      .rowVersion(resultSet.getInt("rowversion"))
      .isDeleted(resultSet.getBoolean("isdeleted"))
      .build();
  }

  private JsonNode getAdditionalDetail(String columnName, ResultSet rs) throws SQLException {
    JsonNode additionalDetails = null;
    try {
      PGobject obj = (PGobject) rs.getObject(columnName);
      if (obj != null) {
        additionalDetails = objectMapper.readTree(obj.getValue());
      }
    } catch (IOException e) {
      throw new CustomException("PARSING ERROR", "Failed to parse additionalDetail object");
    }
    if (additionalDetails != null && additionalDetails.isEmpty())
      additionalDetails = null;
    return additionalDetails;
  }
}