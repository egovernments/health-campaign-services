package org.digit.health.sync.repository;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.springframework.stereotype.Component;

@Slf4j
@Component("defaultSyncLogQueryBuilder")
public class DefaultSyncLogQueryBuilder implements SyncLogQueryBuilder {

    @Override
    public String createSelectQuery(SyncLogData syncLogData) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sync_log ");
        sql.append(" WHERE tenantId = :tenantId");

        if (syncLogData.getId() != null) {
            sql.append(" AND id=:id ");
        }

        if (syncLogData.getStatus() != null) {
            sql.append(" AND status=:status ");
        }

        if (syncLogData.getReferenceId() != null && syncLogData.getReferenceIdType() != null ) {
            sql.append(" AND referenceId=:referenceId AND referenceIdType=:referenceIdType ");
        }

        if (syncLogData.getFileStoreId() != null) {
            sql.append(" AND fileStoreId=:fileStoreId ");
        }

        return sql.toString();
    }

    @Override
    public String createUpdateQuery(SyncLogData syncLogData) {
        StringBuilder sql = new StringBuilder("UPDATE sync_log SET ");
        if(syncLogData.getStatus()!=null){
            sql.append(" status = :status");
        }

        sql.append(" WHERE tenantId = :tenantId AND id=:id");
        return sql.toString();
    }

}
