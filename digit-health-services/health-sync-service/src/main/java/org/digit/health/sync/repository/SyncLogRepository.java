package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;

import java.util.List;

public interface SyncLogRepository {
    List<SyncLogData> findByCriteria(SyncLogSearchDto syncLogSearchDto);
}
