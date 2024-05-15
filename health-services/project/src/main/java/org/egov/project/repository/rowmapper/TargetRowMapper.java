package org.egov.project.repository.rowmapper;

import digit.models.coremodels.AuditDetails;
import org.egov.common.models.project.Target;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TargetRowMapper  implements ResultSetExtractor<List<Target>> {

    @Override
    public List<Target> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, Target> targetMap = new LinkedHashMap<>();

        while (rs.next()) {

            String target_id = rs.getString("targetId");
            String target_projectId = rs.getString("target_projectId");
            String target_beneficiaryType = rs.getString("target_beneficiaryType");
            Integer target_totalNo = rs.getInt("target_totalNo");
            Integer target_targetNo = rs.getInt("target_targetNo");
            Boolean target_isDeleted = rs.getBoolean("target_isDeleted");
            String target_createdBy = rs.getString("target_createdBy");
            String target_lastModifiedBy = rs.getString("target_lastModifiedBy");
            Long target_createdTime = rs.getLong("target_createdTime");
            Long target_lastModifiedTime = rs.getLong("target_lastModifiedTime");

            AuditDetails targetAuditDetails = AuditDetails.builder().createdBy(target_createdBy).createdTime(target_createdTime)
                    .lastModifiedBy(target_lastModifiedBy).lastModifiedTime(target_lastModifiedTime)
                    .build();

            Target target = Target.builder()
                    .id(target_id)
                    .projectid(target_projectId)
                    .beneficiaryType(target_beneficiaryType)
                    .totalNo(target_totalNo)
                    .targetNo(target_targetNo)
                    .isDeleted(target_isDeleted)
                    .auditDetails(targetAuditDetails)
                    .build();

            if (!targetMap.containsKey(target_id)) {
                targetMap.put(target_id, target);
            }
        }

        return new ArrayList<>(targetMap.values());
    }
}
