package org.digit.health.sync.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.web.models.SyncRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.validation.Valid;

@Controller
@Slf4j
@RequestMapping("/sync/v1")
public class SyncController {

    @PostMapping("/up")
    public ResponseEntity<String> syncUp(@RequestBody @Valid SyncRequest syncRequest) {
        log.info("Logged Sync Request",syncRequest.getRequestInfo().toString());
        return ResponseEntity.ok("Sync request submitted");
    }
}
