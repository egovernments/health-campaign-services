package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncErrorDetailsLogData;

public interface SyncErrorDetailsLogRepository {
    SyncErrorDetailsLogData save(SyncErrorDetailsLogData data);
}
