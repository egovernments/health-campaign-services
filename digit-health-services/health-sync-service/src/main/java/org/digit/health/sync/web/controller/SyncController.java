package org.digit.health.sync.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
@RequestMapping("/sync/v1")
public class SyncController {

    @GetMapping("/up")
    public ResponseEntity<String> syncUp() {
        return ResponseEntity.ok("Sync request submitted");
    }
}
