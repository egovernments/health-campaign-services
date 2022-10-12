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
        sql.append((String.format(" WHERE tenantId='%s'", syncLogSearchDto.getTenantId())));

        if (syncLogSearchDto.getSyncId() != null) {
            sql.append(String.format(" AND id='%s'", syncLogSearchDto.getSyncId()));
        }

        if (syncLogSearchDto.getStatus() != null) {
            sql.append(String.format(" AND status='%s'", syncLogSearchDto.getStatus()));
        }

        if (syncLogSearchDto.getReference() != null) {
            sql.append(String.format(" AND referenceId='%s' AND referenceIdType='%s'", syncLogSearchDto.getReference().getId(), syncLogSearchDto.getReference().getType()));
        }

        if (syncLogSearchDto.getFileStoreId() != null) {
            sql.append(String.format(" AND fileStoreId='%s'", syncLogSearchDto.getFileStoreId()));
        }

        return sql.toString();
    }

}
