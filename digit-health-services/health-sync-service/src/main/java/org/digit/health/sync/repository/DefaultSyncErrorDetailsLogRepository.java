package org.digit.health.sync.repository;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.repository.enums.RepositoryErrorCode;
import org.digit.health.sync.web.models.dao.SyncErrorDetailsLogData;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Slf4j
public class DefaultSyncErrorDetailsLogRepository implements SyncErrorDetailsLogRepository {

    public static final String SAVE_KAFKA_TOPIC = "health-sync-error-details-log";
    private final Producer producer;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    private final SyncErrorDetailsLogQueryBuilder queryBuilder;

    @Autowired
    public DefaultSyncErrorDetailsLogRepository(Producer producer,
                                                NamedParameterJdbcTemplate jdbcTemplate,
                                                SyncErrorDetailsLogQueryBuilder queryBuilder) {
        this.producer = producer;
        this.jdbcTemplate = jdbcTemplate;
        this.queryBuilder = queryBuilder;
    }

    @Override
    public SyncErrorDetailsLogData save(SyncErrorDetailsLogData data) {
        if (data == null) {
            return null;
        }
        try {
            producer.send(SAVE_KAFKA_TOPIC, data);
        } catch (Exception exception) {
            log.error("Error during save", exception);
            throw new CustomException(RepositoryErrorCode.SAVE_ERROR.name(),
                    RepositoryErrorCode.SAVE_ERROR.message(exception.getMessage()));
        }
        return data;
    }

    @Override
    public List<SyncErrorDetailsLogData> find(SyncErrorDetailsLogData data) {
        final Map<String, Object> in = new HashMap<>();
        in.put("tenantId", data.getTenantId());
        in.put("syncId", data.getSyncId());
        in.put("recordId", data.getRecordId());
        in.put("recordIdType", data.getRecordIdType());
        return jdbcTemplate.query(
                queryBuilder.createSelectQuery(data),
                in,
                new BeanPropertyRowMapper<>(SyncErrorDetailsLogData.class)
        );
    }
}
