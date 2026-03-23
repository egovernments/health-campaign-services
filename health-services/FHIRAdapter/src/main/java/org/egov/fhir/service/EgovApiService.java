package org.egov.fhir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.egov.fhir.config.MappingConfig.ApiMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EgovApiService {

    @Value("${egov.base.url:http:8080}")
    private String egovBaseUrl;

    @Autowired
    private final RestTemplate restTemplate;

    @Autowired
    private final ObjectMapper objectMapper;

    // TODO: tenantId should come from request context
    @Value("${egov.default.tenantId:mz}")
    private String defaultTenantId;

    public Map<String, Object> callApi(ApiMapping apiMapping, Map<String, Object> requestBody) {
        // Build URL with tenantId
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(egovBaseUrl)
                  .append(apiMapping.getEndpoint())
                  .append("?tenantId=").append(defaultTenantId);

        // Add pagination params (required by eGov API) - extract from body or use defaults
        int limit = 10;  // default
        int offset = 0;  // default

        if (requestBody.containsKey("limit")) {
            limit = ((Number) requestBody.get("limit")).intValue();
            requestBody.remove("limit");  // remove from body since it's a URL param
        }
        if (requestBody.containsKey("offset")) {
            offset = ((Number) requestBody.get("offset")).intValue();
            requestBody.remove("offset");  // remove from body since it's a URL param
        }

        urlBuilder.append("&limit=").append(limit);
        urlBuilder.append("&offset=").append(offset);

        String url = urlBuilder.toString();

        try {
            log.info("Calling eGov API: {} {}", apiMapping.getMethod(), url);
            log.info("Request body:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody));
        } catch (Exception e) {
            log.info("Request body: {}", requestBody);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiMapping.getHeaders() != null) {
            apiMapping.getHeaders().forEach(headers::set);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("Entity body:\n{}", objectMapper.writeValueAsString(entity.getBody()));
        } catch (Exception e) {
            log.info("Entity: {}", entity);
        }

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.valueOf(apiMapping.getMethod()),
                entity,
                Map.class
        );

        try {
            log.info("eGov Response:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.getBody()));
        } catch (Exception e) {
            log.info("eGov Response: {}", response.getBody());
        }

        return response.getBody();
    }
}
