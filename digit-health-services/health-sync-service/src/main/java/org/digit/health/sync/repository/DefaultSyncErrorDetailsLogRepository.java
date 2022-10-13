package org.digit.health.sync.repository;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.repository.enums.RepositoryErrorCode;
import org.digit.health.sync.web.models.dao.SyncErrorDetailsLogData;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class DefaultSyncErrorDetailsLogRepository implements SyncErrorDetailsLogRepository {

    public static final String SAVE_KAFKA_TOPIC = "health-sync-error-details-log";
    private final Producer producer;

    public DefaultSyncErrorDetailsLogRepository(Producer producer) {
        this.producer = producer;
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
}
