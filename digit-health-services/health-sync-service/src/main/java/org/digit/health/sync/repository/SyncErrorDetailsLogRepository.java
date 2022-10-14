package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncErrorDetailsLogData;

import java.util.List;

public interface SyncErrorDetailsLogRepository {
    SyncErrorDetailsLogData save(SyncErrorDetailsLogData data);

    List<SyncErrorDetailsLogData> find(SyncErrorDetailsLogData data);
}
