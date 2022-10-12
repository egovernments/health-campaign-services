package org.digit.health.sync.repository;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.web.models.request.SyncSearchDto;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SyncQueryBuilder {

    public String getSQlBasedOn(SyncSearchDto syncSearchDto) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sync_log ");
        sql.append((String.format(" WHERE tenantId='%s'", syncSearchDto.getTenantId())));

        if (syncSearchDto.getSyncId() != null) {
            sql.append(String.format(" AND id='%s'", syncSearchDto.getSyncId()));
        }

        if (syncSearchDto.getStatus() != null) {
            sql.append(String.format(" AND status='%s'", syncSearchDto.getStatus()));
        }

        if (syncSearchDto.getReference() != null) {
            sql.append(String.format(" AND referenceId='%s' AND referenceIdType='%s'", syncSearchDto.getReference().getId(), syncSearchDto.getReference().getType()));
        }

        if (syncSearchDto.getFileStoreId() != null) {
            sql.append(String.format(" AND fileStoreId='%s'", syncSearchDto.getFileStoreId()));
        }

        return sql.toString();
    }

}
