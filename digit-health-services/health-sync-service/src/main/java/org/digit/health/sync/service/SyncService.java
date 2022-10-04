package org.digit.health.sync.service;

import org.digit.health.sync.web.models.request.SyncUpDto;

public interface SyncService {
    String sync(SyncUpDto syncUpDto);
}
