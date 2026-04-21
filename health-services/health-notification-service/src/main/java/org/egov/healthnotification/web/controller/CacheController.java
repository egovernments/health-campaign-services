package org.egov.healthnotification.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.service.LocalizationService;
import org.egov.healthnotification.service.MdmsService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/notification/v1/_cache")
@Slf4j
public class CacheController {

    private final MdmsService mdmsService;
    private final LocalizationService localizationService;
    private final HealthNotificationProperties properties;

    @Autowired
    public CacheController(MdmsService mdmsService,
                           LocalizationService localizationService,
                           HealthNotificationProperties properties) {
        this.mdmsService = mdmsService;
        this.localizationService = localizationService;
        this.properties = properties;
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshCache() {
        log.info("Cache refresh triggered via API");

        try {
            String tenantId = properties.getStateLevelTenantId();

            mdmsService.loadAndCacheMdmsNotificationConfigs(tenantId);

            localizationService.loadAndCacheLocalizationMessages(
                    properties.getSupportedLocales(),
                    properties.getLocalizationNotificationModule(),
                    tenantId);

            log.info("Cache refresh completed successfully");
            return ResponseEntity.ok(Map.of("status", "Cache refreshed successfully"));
        } catch (Exception e) {
            log.error("Cache refresh failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "Cache refresh failed",
                            "message", e.getMessage() != null ? e.getMessage() : "Unexpected error"));
        }
    }
}
