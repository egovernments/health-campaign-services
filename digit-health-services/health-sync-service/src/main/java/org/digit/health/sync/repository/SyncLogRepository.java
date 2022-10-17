package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncLogData;

import java.util.List;

public interface SyncLogRepository {
    List<SyncLogData> find(SyncLogData syncLogSearchDto);

    int update(SyncLogData syncLogData);

    SyncLogData save(SyncLogData syncLogData);
}
