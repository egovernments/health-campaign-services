package org.digit.health.sync.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.utils.ModelMapper;
import org.digit.health.sync.web.models.request.SyncUpRequest;
import org.digit.health.sync.web.models.response.SyncUpResponse;
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

    @PostMapping("/up")
    public ResponseEntity<SyncUpResponse> syncUp(@RequestBody @Valid SyncUpRequest syncUpRequest) {
        log.info("Sync up request {}", syncUpRequest.toString());
        return ResponseEntity.accepted().body(SyncUpResponse.builder()
                .responseInfo(ModelMapper.createResponseInfoFromRequestInfo(syncUpRequest
                        .getRequestInfo(), true))
                .syncId("dummy-id")
                .build());
    }
}
