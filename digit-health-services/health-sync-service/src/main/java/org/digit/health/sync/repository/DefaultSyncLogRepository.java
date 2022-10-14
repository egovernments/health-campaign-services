package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncLogData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository("defaultSyncLogRepository")
public class DefaultSyncLogRepository implements SyncLogRepository{

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final SyncLogQueryBuilder syncLogQueryBuilder;

    @Autowired
    public DefaultSyncLogRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            @Qualifier("defaultSyncLogQueryBuilder") SyncLogQueryBuilder defaultSyncLogQueryBuilder) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.syncLogQueryBuilder = defaultSyncLogQueryBuilder;
    }

    @Override
    public List<SyncLogData> find(SyncLogData syncLogData) {
        final Map<String, Object> in = new HashMap<>();
        in.put("tenantId", syncLogData.getTenantId());
        in.put("id", syncLogData.getId());
        in.put("status", syncLogData.getStatus());
        if(syncLogData.getReferenceId() != null) {
            in.put("referenceId", syncLogData.getReferenceId());
            in.put("referenceIdType", syncLogData.getReferenceIdType());
        }
        in.put("fileStoreId", syncLogData.getFileStoreId());
        return namedParameterJdbcTemplate.query(
                syncLogQueryBuilder.createSelectQuery(syncLogData),
                in,
                new BeanPropertyRowMapper<>(SyncLogData.class)
        );
    }

    @Override
    public int update(SyncLogData syncLogData) {
        final Map<String, Object> in = new HashMap<>();
        in.put("tenantId", syncLogData.getTenantId());
        in.put("id", syncLogData.getId());
        in.put("status", syncLogData.getStatus());
        return namedParameterJdbcTemplate.update(
                syncLogQueryBuilder.createUpdateQuery(syncLogData),
                in
        );
    }

}
