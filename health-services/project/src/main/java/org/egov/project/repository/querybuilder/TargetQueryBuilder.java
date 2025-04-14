package org.egov.project.repository.querybuilder;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TargetQueryBuilder {

    private static final String FETCH_TARGET_QUERY = "select t.id as targetId, t.projectId as target_projectId, t.beneficiaryType as target_beneficiaryType, t.totalNo as target_totalNo, t.targetNo as target_targetNo, "
            +
            "t.isDeleted as target_isDeleted, t.createdBy as target_createdBy, t.createdTime as target_createdTime, t.lastModifiedBy as target_lastModifiedBy, t.lastModifiedTime as target_lastModifiedTime "
            +
            " from project_target t where ";

    /* Constructs target search query based on project Ids */
    public String getTargetSearchQuery(Set<String> projectIds, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = null;
        queryBuilder = new StringBuilder(FETCH_TARGET_QUERY);

        if (projectIds != null && !projectIds.isEmpty()) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" t.projectId IN (").append(createQuery(projectIds)).append(")");
            addToPreparedStatement(preparedStmtList, projectIds);
        }

        return queryBuilder.toString();
    }

    public String getTargetAggregationQuery(Set<String> projectIds, List<Object> stmtList) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_TARGET_QUERY);

        // Append the correct number of ? placeholders
        String placeholders = projectIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));
        queryBuilder.append("projectId IN (").append(placeholders).append(")");

        // Add each projectId to the stmtList
        stmtList.addAll(projectIds);

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
            if (i != length - 1)
                builder.append(",");
        }
        return builder.toString();
    }

    private void addToPreparedStatement(List<Object> preparedStmtList, Collection<String> ids) {
        ids.forEach(id -> {
            preparedStmtList.add(id);
        });
    }

}
