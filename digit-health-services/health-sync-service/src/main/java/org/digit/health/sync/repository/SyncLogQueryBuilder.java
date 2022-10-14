package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncLogData;

public interface SyncLogQueryBuilder {
    String createSelectQuery(SyncLogData syncLogData);
    String createUpdateQuery(SyncLogData syncLogData);

}
