package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncErrorDetailsLogData;
import org.springframework.stereotype.Component;

@Component
public class DefaultSyncErrorDetailsLogQueryBuilder implements SyncErrorDetailsLogQueryBuilder {
    @Override
    public String createSelectQuery(SyncErrorDetailsLogData data) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sync_error_details_log");
        sql.append(" WHERE tenantId=:tenantId");

        if (data.getSyncId() != null) {
            sql.append(" AND syncId=:syncId");
        }

        if (data.getRecordId() != null) {
            sql.append(" AND recordId=:recordId");
        }

        if (data.getRecordIdType() != null) {
            sql.append(" AND recordIdType=:recordIdType");
        }

        return sql.toString();
    }
}
