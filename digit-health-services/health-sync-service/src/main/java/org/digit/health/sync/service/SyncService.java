package org.digit.health.sync.service;

import org.digit.health.sync.web.models.SyncId;
import org.digit.health.sync.web.models.request.SyncUpDto;

public interface SyncService {
    SyncId syncUp(SyncUpDto syncUpDto);
}
