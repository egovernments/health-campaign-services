package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;

import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.localization.LocalisationRequest;
import org.egov.excelingestion.web.models.localization.LocalisationResponse;
import org.egov.excelingestion.web.models.localization.LocalisationSearchCriteria;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class LocalizationService {

    private final RestTemplate restTemplate;

    @Value("${egov.localization.host}")
    private String localizationHost;

    @Value("${egov.localization.search.path}")
    private String localizationSearchPath;

    public LocalizationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "localizationMessages", key = "#tenantId + #module + #locale")
    public Map<String, String> getLocalizedMessages(String tenantId, String module, String locale, RequestInfo requestInfo) {
        // Build URL with query params
        String url = UriComponentsBuilder.fromHttpUrl(localizationHost + localizationSearchPath)
                .queryParam("tenantId", tenantId)
                .queryParam("module", module)
                .queryParam("locale", locale)
                .toUriString();

        try {
            // Post only RequestInfo in body (assuming API accepts it)
            LocalisationResponse response = restTemplate.postForObject(url, requestInfo, LocalisationResponse.class);

            if (response != null && response.getMessages() != null) {
                Map<String, String> localizedMessages = new HashMap<>();
                response.getMessages().forEach(message -> localizedMessages.put(message.getCode(), message.getMessage()));
                log.info("Fetched {} localized messages for tenantId: {}, module: {}, locale: {}",
                        localizedMessages.size(), tenantId, module, locale);
                return localizedMessages;
            }
        } catch (Exception e) {
            log.error("Error fetching localized messages from {}: {}", url, e.getMessage());
        }
        return Collections.emptyMap();
    }
}
