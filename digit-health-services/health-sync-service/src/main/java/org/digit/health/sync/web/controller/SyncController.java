package org.digit.health.sync.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.web.models.SyncUpRequest;
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
    public ResponseEntity<String> syncUp(@RequestBody @Valid SyncUpRequest syncUpRequest) {
        log.info("Logged Sync up Request {}", syncUpRequest.toString());
        return ResponseEntity.ok("Sync up request submitted");
    }
}
