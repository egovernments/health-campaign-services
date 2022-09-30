package org.digit.health.sync.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.service.SyncLogService;
import org.digit.health.sync.utils.ModelMapper;
import org.digit.health.sync.web.models.request.SyncUpRequest;
import org.digit.health.sync.web.models.response.SyncUpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.validation.Valid;

@Controller
@Slf4j
@RequestMapping("/sync/v1")
public class SyncController {

    private final SyncLogService syncLogService;

    @Autowired
    public SyncController(SyncLogService syncLogService) {
        this.syncLogService = syncLogService;
    }

    @PostMapping("/up")
    public ResponseEntity<SyncUpResponse> syncUp(@RequestBody @Valid SyncUpRequest syncUpRequest) {
        log.info("Sync up request {}", syncUpRequest.toString());
        syncLogService.persistSyncLog();
        syncLogService.persistSyncErrorDetailsLog();
        return ResponseEntity.accepted().body(SyncUpResponse.builder()
                .responseInfo(ModelMapper.createResponseInfoFromRequestInfo(syncUpRequest
                        .getRequestInfo(), true))
                .syncId("dummy-sync-id")
                .build());
    }
}
