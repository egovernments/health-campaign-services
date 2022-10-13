package org.digit.health.sync.repository;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
import org.springframework.stereotype.Component;

@Slf4j
@Component("defaultSyncLogQueryBuilder")
public class DefaultSyncLogQueryBuilder implements SyncLogQueryBuilder {

    @Override
    public String getSQlBasedOn(SyncLogSearchDto syncLogSearchDto) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sync_log ");
        sql.append(" WHERE tenantId = :tenantId");

        if (syncLogSearchDto.getSyncId() != null) {
            sql.append(" AND id=:id ");
        }

        if (syncLogSearchDto.getStatus() != null) {
            sql.append(" AND status=:status ");
        }

        if (syncLogSearchDto.getReference() != null) {
            sql.append(" AND referenceId=:referenceId AND referenceIdType=:referenceIdType ");
        }

        if (syncLogSearchDto.getFileStoreId() != null) {
            sql.append(" AND fileStoreId=:fileStoreId ");
        }

        return sql.toString();
    }

}
