package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
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
    public List<SyncLogData> findByCriteria(SyncLogSearchDto syncLogSearchDto) {
        final Map<String, Object> in = new HashMap<>();
        in.put("tenantId", syncLogSearchDto.getTenantId());
        in.put("id", syncLogSearchDto.getSyncId());
        in.put("status", syncLogSearchDto.getStatus());
        if( syncLogSearchDto.getReference() != null){
            in.put("referenceId", syncLogSearchDto.getReference().getId());
            in.put("referenceIdType", syncLogSearchDto.getReference().getType());
        }
        in.put("fileStoreId", syncLogSearchDto.getFileStoreId());
        return namedParameterJdbcTemplate.query(syncLogQueryBuilder.getSQlBasedOn(syncLogSearchDto),in, new BeanPropertyRowMapper<>(SyncLogData.class));
    }

}
