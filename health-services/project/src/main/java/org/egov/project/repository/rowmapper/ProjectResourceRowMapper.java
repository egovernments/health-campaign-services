package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.project.ProjectProductVariant;
import org.egov.common.models.project.ProjectResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;


@Component
public class ProjectResourceRowMapper implements RowMapper<ProjectResource> {

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public ProjectResource mapRow(ResultSet resultSet, int i) throws SQLException {
        return ProjectResource.builder()
                .id(resultSet.getString("id"))
                .projectId(resultSet.getString("projectId"))
                .tenantId(resultSet.getString("tenantId"))
                .startDate(resultSet.getLong("startDate"))
                .endDate(resultSet.getLong("endDate"))
                .resource(ProjectProductVariant.builder()
                        .type(resultSet.getString("type"))
                        .productVariantId(resultSet.getString("productVariantId"))
                        .isBaseUnitVariant(resultSet.getBoolean("isBaseUnitVariant"))
                        .build())
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
}
