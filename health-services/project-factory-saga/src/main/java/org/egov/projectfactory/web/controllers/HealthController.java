package org.egov.projectfactory.web.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight liveness endpoint for the skeleton. Real health is served by the
 * actuator {@code /actuator/health}; this confirms the app + context-path are wired.
 */
@RestController
@RequestMapping("")
@Slf4j
public class HealthController {

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(Map.of("service", "project-factory-saga", "status", "UP"));
    }
}
