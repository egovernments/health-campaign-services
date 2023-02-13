package org.egov.project.repository.querybuilder;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Component
public class TargetQueryBuilder {

    private static final String FETCH_TARGET_QUERY = "select t.id as targetId, t.project_id as target_projectId, t.beneficiary_type as target_beneficiaryType, t.total_no as target_totalNo, t.target_no as target_targetNo, " +
            "t.is_deleted as target_isDeleted, t.created_by as target_createdBy, t.created_time as target_createdTime, t.last_modified_by as target_lastModifiedBy, t.last_modified_time as target_lastModifiedTime " +
            " from eg_pms_target t ";

    /* Constructs target search query based on project Ids */
    public String getTargetSearchQuery(Set<String> projectIds, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = null;
        queryBuilder = new StringBuilder(FETCH_TARGET_QUERY);

        if (projectIds != null && !projectIds.isEmpty()) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" t.project_id IN (").append(createQuery(projectIds)).append(")");
            addToPreparedStatement(preparedStmtList, projectIds);
        }

        return queryBuilder.toString();
    }

    private static void addClauseIfRequired(List<Object> values, StringBuilder queryString) {
        if (values.isEmpty())
            queryString.append(" WHERE ");
        else {
            queryString.append(" AND");
        }
    }

    private String createQuery(Collection<String> ids) {
        StringBuilder builder = new StringBuilder();
        int length = ids.size();
        for (int i = 0; i < length; i++) {
            builder.append(" ? ");
            if (i != length - 1) builder.append(",");
        }
        return builder.toString();
    }

    private void addToPreparedStatement(List<Object> preparedStmtList, Collection<String> ids) {
        ids.forEach(id -> {
            preparedStmtList.add(id);
        });
    }

}
