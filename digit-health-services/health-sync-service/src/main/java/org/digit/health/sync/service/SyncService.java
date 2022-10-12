package org.digit.health.sync.service;

import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.SyncId;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
import org.digit.health.sync.web.models.request.SyncUpDto;
import java.util.List;

public interface SyncService {
    SyncId syncUp(SyncUpDto syncUpDto);
    List<SyncLogData> findByCriteria(SyncLogSearchDto syncSearchRequest);
}
