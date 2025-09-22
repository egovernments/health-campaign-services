package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;

import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.localization.LocalisationRequest;
import org.egov.excelingestion.web.models.localization.LocalisationResponse;
import org.egov.excelingestion.web.models.localization.LocalisationSearchCriteria;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class LocalizationService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 20000; // 20 second delay between retries

    private final ServiceRequestRepository serviceRequestRepository;
    private final CustomExceptionHandler exceptionHandler;

    @Value("${egov.localization.host}")
    private String localizationHost;

    @Value("${egov.localization.search.path}")
    private String localizationSearchPath;

    public LocalizationService(ServiceRequestRepository serviceRequestRepository, 
                              CustomExceptionHandler exceptionHandler) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.exceptionHandler = exceptionHandler;
    }

    @Cacheable(value = "localizationMessages", key = "#tenantId + #module + #locale")
    public Map<String, String> getLocalizedMessages(String tenantId, String module, String locale, RequestInfo requestInfo) {
        return executeWithRetry("Localization service call for tenantId: " + tenantId + ", module: " + module + ", locale: " + locale, () -> {
            // Build URL with query params
            String url = UriComponentsBuilder.fromHttpUrl(localizationHost + localizationSearchPath)
                    .queryParam("tenantId", tenantId)
                    .queryParam("module", module)
                    .queryParam("locale", locale)
                    .toUriString();

            // Post only RequestInfo in body (assuming API accepts it)
            StringBuilder uri = new StringBuilder(url);
            log.info("Fetching localized messages from: {}", uri);
            LocalisationResponse response = serviceRequestRepository.fetchResult(uri, requestInfo, LocalisationResponse.class);

            if (response != null && response.getMessages() != null) {
                Map<String, String> localizedMessages = new HashMap<>();
                response.getMessages().forEach(message -> localizedMessages.put(message.getCode(), message.getMessage()));
                log.info("Fetched {} localized messages for tenantId: {}, module: {}, locale: {}",
                        localizedMessages.size(), tenantId, module, locale);
                return localizedMessages;
            }
            
            return Collections.emptyMap();
        });
    }

    /**
     * Generic retry mechanism for Localization service calls
     */
    private <T> T executeWithRetry(String operationName, RetryableOperation<T> operation) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                log.info("Attempting {} (attempt {} of {})", operationName, attempt, MAX_RETRY_ATTEMPTS);
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                log.warn("Failed {} on attempt {} of {}: {}", operationName, attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry sleep interrupted for {}", operationName);
                        break;
                    }
                }
            }
        }
        
        log.error("All {} attempts failed for {}", MAX_RETRY_ATTEMPTS, operationName);
        
        // Throw custom exception for Localization service failures
        exceptionHandler.throwCustomException(ErrorConstants.LOCALIZATION_SERVICE_ERROR, 
                ErrorConstants.LOCALIZATION_SERVICE_ERROR_MESSAGE + " after " + MAX_RETRY_ATTEMPTS + " attempts", 
                lastException);
        return null; // This should never be reached due to exception above
    }

    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute() throws Exception;
    }
}
