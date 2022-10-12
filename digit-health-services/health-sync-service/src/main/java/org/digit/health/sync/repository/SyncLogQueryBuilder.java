package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.request.SyncLogSearchDto;

public interface SyncLogQueryBuilder {
    String getSQlBasedOn(SyncLogSearchDto syncLogSearchDto);
}
