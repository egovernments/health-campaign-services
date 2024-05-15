package org.egov.project.repository.querybuilder;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Component
public class DocumentQueryBuilder {

    private static final String FETCH_DOCUMENT_QUERY = "select d.id as documentId, d.projectId as document_projectId, d.documentType as document_documentType, " +
            " d.fileStoreId as document_fileStoreId, d.documentUid as document_documentUid, d.additionalDetails as document_additionalDetails, d.status as document_status, " +
            "d.createdBy as document_createdBy, d.createdTime as document_createdTime, d.lastModifiedBy as document_lastModifiedBy, d.lastModifiedTime as document_lastModifiedTime " +
            " from project_document d ";

    /* Constructs document search query based on project Ids */
    public String getDocumentSearchQuery(Set<String> projectIds, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = null;
        queryBuilder = new StringBuilder(FETCH_DOCUMENT_QUERY);

        if (projectIds != null && !projectIds.isEmpty()) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" d.projectId IN (").append(createQuery(projectIds)).append(")");
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
