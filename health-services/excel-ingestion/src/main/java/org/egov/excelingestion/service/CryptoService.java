package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.web.models.RequestInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for crypto operations - calls project-factory bulk decrypt API
 */
@Service
@Slf4j
public class CryptoService {

    private final ServiceRequestRepository serviceRequestRepository;
    private final CustomExceptionHandler exceptionHandler;

    @Value("${egov.campaign.host}")
    private String projectFactoryHost;

    @Value("${egov.campaign.crypto.bulk.decrypt.path}")
    private String bulkDecryptPath;

    public CryptoService(ServiceRequestRepository serviceRequestRepository,
                        CustomExceptionHandler exceptionHandler) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Decrypt list of encrypted strings using project-factory bulk decrypt API
     *
     * @param encryptedStrings List of encrypted strings (max 500)
     * @param requestInfo Request info for authentication
     * @return List of decrypted strings
     */
    public List<String> bulkDecrypt(List<String> encryptedStrings, RequestInfo requestInfo) {
        if (encryptedStrings == null || encryptedStrings.isEmpty()) {
            log.warn("Empty or null encrypted strings list provided");
            return encryptedStrings;
        }

        if (encryptedStrings.size() > 500) {
            exceptionHandler.throwCustomException(ErrorConstants.INVALID_REQUEST,
                    "Maximum 500 encrypted strings allowed per bulk decrypt request",
                    new RuntimeException("Bulk decrypt limit exceeded"));
        }

        try {
            log.info("Calling project-factory bulk decrypt API for {} strings", encryptedStrings.size());

            // Build request URL
            StringBuilder uriBuilder = new StringBuilder(projectFactoryHost);
            uriBuilder.append(bulkDecryptPath);

            // Build request payload
            Map<String, Object> request = new HashMap<>();
            request.put("RequestInfo", requestInfo);
            request.put("encryptedStrings", encryptedStrings);

            // Call project-factory API
            Object response = serviceRequestRepository.fetchResult(uriBuilder, request);

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                
                @SuppressWarnings("unchecked")
                List<String> decryptedStrings = (List<String>) responseMap.get("decryptedStrings");
                
                if (decryptedStrings != null && decryptedStrings.size() == encryptedStrings.size()) {
                    log.info("Successfully decrypted {} strings", decryptedStrings.size());
                    return decryptedStrings;
                } else {
                    log.error("Invalid response from bulk decrypt API - decrypted strings count mismatch");
                    exceptionHandler.throwCustomException(ErrorConstants.EXTERNAL_SERVICE_ERROR,
                            "Invalid response from project-factory bulk decrypt API",
                            new RuntimeException("Response count mismatch"));
                }
            }

            log.error("Invalid response format from bulk decrypt API");
            exceptionHandler.throwCustomException(ErrorConstants.EXTERNAL_SERVICE_ERROR,
                    "Invalid response format from project-factory bulk decrypt API",
                    new RuntimeException("Invalid response format"));

        } catch (Exception e) {
            log.error("Error calling project-factory bulk decrypt API: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.EXTERNAL_SERVICE_ERROR,
                    "Failed to decrypt strings via project-factory API: " + e.getMessage(), e);
        }

        return null; // This line will never be reached due to exception above
    }

    /**
     * Convenience method to decrypt a single string
     *
     * @param encryptedString Single encrypted string
     * @param requestInfo Request info for authentication
     * @return Decrypted string
     */
    public String decrypt(String encryptedString, RequestInfo requestInfo) {
        if (encryptedString == null || encryptedString.trim().isEmpty()) {
            return encryptedString;
        }

        List<String> result = bulkDecrypt(List.of(encryptedString), requestInfo);
        return result.isEmpty() ? encryptedString : result.get(0);
    }
}