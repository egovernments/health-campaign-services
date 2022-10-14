package org.digit.health.sync.repository;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.repository.enums.RepositoryErrorCode;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository("defaultSyncLogRepository")
public class DefaultSyncLogRepository implements SyncLogRepository {

    public static final String SAVE_KAFKA_TOPIC = "health-sync-log";
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Producer producer;

    private final SyncLogQueryBuilder syncLogQueryBuilder;

    @Autowired
    public DefaultSyncLogRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            @Qualifier("defaultSyncLogQueryBuilder") SyncLogQueryBuilder defaultSyncLogQueryBuilder,
            Producer producer) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.syncLogQueryBuilder = defaultSyncLogQueryBuilder;
        this.producer = producer;
    }

    @Override
    public SyncLogData save(SyncLogData syncLogData) {
        if (syncLogData == null) {
            return null;
        }
        try {
            producer.send(SAVE_KAFKA_TOPIC, syncLogData);
        } catch (Exception exception) {
            log.error("Error during save", exception);
            throw new CustomException(RepositoryErrorCode.SAVE_ERROR.name(),
                    RepositoryErrorCode.SAVE_ERROR.message(exception.getMessage()));
        }
        return syncLogData;
    }

    @Override
    public List<SyncLogData> find(SyncLogData syncLogData) {
        final Map<String, Object> in = new HashMap<>();
        in.put("tenantId", syncLogData.getTenantId());
        in.put("id", syncLogData.getId());
        in.put("status", syncLogData.getStatus());
        if (syncLogData.getReferenceId() != null) {
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
