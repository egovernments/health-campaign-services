package org.egov.referralmanagement.service.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Runs at bean-init time and fails the pod's startup if the selected storage
 * backend is missing any required config value. This is the "start only when
 * satisfied" guarantee — the app never accepts a downsync request that it
 * couldn't upload the output for.
 *
 * <p>Runs immediately after the Spring context wires up
 * {@link DownsyncStorageBackend}; if {@code egov.downsync.storage.backend}
 * doesn't match any known value, no backend bean is created and Spring itself
 * fails the injection here with a clear message.
 */
@Component
@Slf4j
public class StorageBackendValidator {

    @Autowired private DownsyncStorageBackend backend;
    @Autowired private ReferralManagementConfiguration config;

    @PostConstruct
    void validate() {
        String requested = config.getStorageBackend();
        if (!backend.backendName().equalsIgnoreCase(requested)) {
            // Sanity: shouldn't happen — @ConditionalOnProperty already narrowed
            // the bean, so if backend is bound, it matched. Guard anyway.
            throw new IllegalStateException(
                "egov.downsync.storage.backend=" + requested +
                " but resolved backend bean reports name '" + backend.backendName() + "'.");
        }
        log.info("Storage backend selected: {}. Validating required config…", backend.backendName());
        backend.validateStartupConfig();
        log.info("Storage backend '{}' ready. Downsync uploads + presigned URLs will use this backend.",
                backend.backendName());
    }
}
