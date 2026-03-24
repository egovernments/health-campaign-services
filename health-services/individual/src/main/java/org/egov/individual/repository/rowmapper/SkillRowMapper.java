package org.egov.individual.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.individual.Skill;
import org.springframework.jdbc.core.RowMapper;

public class SkillRowMapper implements RowMapper<Skill> {
    @Override
    public Skill mapRow(ResultSet resultSet, int i) throws SQLException {
        return Skill.builder()
                .id(resultSet.getString("id"))
                .individualId(resultSet.getString("individualId"))
                .clientReferenceId(resultSet.getString("clientReferenceId"))
                .type(resultSet.getString("type"))
                .level(resultSet.getString("level"))
                .experience(resultSet.getString("experience"))
                .auditDetails(AuditDetails.builder().createdBy(resultSet.getString("createdBy"))
                        .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                        .createdTime(resultSet.getLong("createdTime"))
                        .lastModifiedTime(resultSet.getLong("lastModifiedTime")).build())
                .isDeleted(resultSet.getBoolean("isDeleted"))
                .build();
    }
}
