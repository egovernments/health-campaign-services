package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncLogData;

public interface SyncLogQueryBuilder {
    String getSQlBasedOn(SyncLogData syncLogSearchDto);
    String getUpdateSQlBasedOn(SyncLogData syncLogUpdateDto);

}
