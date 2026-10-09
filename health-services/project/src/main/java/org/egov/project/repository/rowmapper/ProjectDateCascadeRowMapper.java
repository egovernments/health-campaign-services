package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.project.Project;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps the narrow projection used by the date cascade. The cascade rewrites only dates and the
 * cycles inside additionalDetails, so address, targets and documents are deliberately absent.
 * A plain RowMapper suffices here: with no address join there is exactly one row per project,
 * so none of ProjectAddressRowMapper's de-duplication is needed.
 */
@Repository
public class ProjectDateCascadeRowMapper implements RowMapper<Project> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Project mapRow(ResultSet rs, int rowNum) throws SQLException {
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("project_createdBy"))
                .createdTime(rs.getObject("project_createdTime", Long.class))
                .lastModifiedBy(rs.getString("project_lastModifiedBy"))
                .lastModifiedTime(rs.getObject("project_lastModifiedTime", Long.class))
                .build();

        // These bigint columns are nullable; getLong would flatten SQL NULL to 0 and the ancestor
        // min/max in ProjectEnrichment would then write epoch over a genuinely absent date.
        return Project.builder()
                .id(rs.getString("projectId"))
                .tenantId(rs.getString("project_tenantId"))
                .startDate(rs.getObject("project_startDate", Long.class))
                .endDate(rs.getObject("project_endDate", Long.class))
                .parent(rs.getString("project_parent"))
                .projectHierarchy(rs.getString("project_projectHierarchy"))
                .additionalDetails(getAdditionalDetail("project_additionalDetails", rs))
                .auditDetails(auditDetails)
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
        if (additionalDetails == null || additionalDetails.isEmpty())
            additionalDetails = null;
        return additionalDetails;
    }
}
