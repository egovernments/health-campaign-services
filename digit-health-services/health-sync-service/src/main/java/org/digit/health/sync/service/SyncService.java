package org.digit.health.sync.service;

import org.digit.health.sync.web.models.dao.SyncData;
import org.digit.health.sync.web.models.SyncId;
import org.digit.health.sync.web.models.request.SyncSearchDto;
import org.digit.health.sync.web.models.request.SyncUpDto;
import java.util.List;

public interface SyncService {
    SyncId syncUp(SyncUpDto syncUpDto);
    List<SyncData> findByCriteria(SyncSearchDto syncSearchRequest);
}
