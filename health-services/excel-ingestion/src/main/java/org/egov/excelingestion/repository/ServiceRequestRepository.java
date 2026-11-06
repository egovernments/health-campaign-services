package org.egov.excelingestion.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Custom ServiceRequestRepository that provides better error handling
 * for network connectivity issues across all service calls.
 */
@Repository
@Slf4j
public class ServiceRequestRepository {

    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final CustomExceptionHandler exceptionHandler;

    public ServiceRequestRepository(ObjectMapper mapper, RestTemplate restTemplate, 
                                   CustomExceptionHandler exceptionHandler) {
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Fetch result with enhanced error handling that shows actual error messages.
     *
     * @param uri URI to call
     * @param request Request payload
     * @return Response object
     * @throws RuntimeException with actual error message from the service
     */
    public Object fetchResult(StringBuilder uri, Object request) {
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        Object response = null;
        try {
            response = restTemplate.postForObject(uri.toString(), request, Map.class);
        } catch (HttpClientErrorException e) {
            log.error("HTTP client error calling external service at {}: {}", uri.toString(), e.getMessage(), e);
            // Throw the actual error response from the service
            exceptionHandler.throwCustomException(ErrorConstants.EXTERNAL_SERVICE_ERROR, 
                    e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error calling external service at {}: {}", uri.toString(), e.getMessage(), e);
            // Throw the actual error message
            exceptionHandler.throwCustomException(ErrorConstants.EXTERNAL_SERVICE_ERROR, 
                    e.getMessage(), e);
        }

        return response;
    }

    /**
     * Generic typed fetch result method.
     *
     * @param uri URI to call
     * @param request Request payload
     * @param responseClass Response class type
     * @param <T> Response type
     * @return Response object
     */
    @SuppressWarnings("unchecked")
    public <T> T fetchResult(StringBuilder uri, Object request, Class<T> responseClass) {
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        T response = null;
        try {
            response = restTemplate.postForObject(uri.toString(), request, responseClass);
        } catch (HttpClientErrorException e) {
            log.error("HTTP client error calling external service at {}: {}", uri.toString(), e.getMessage(), e);
            // Throw the actual error response from the service
            exceptionHandler.throwCustomException(ErrorConstants.EXTERNAL_SERVICE_ERROR, 
                    e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error calling external service at {}: {}", uri.toString(), e.getMessage(), e);
            // Throw the actual error message
            exceptionHandler.throwCustomException(ErrorConstants.EXTERNAL_SERVICE_ERROR, 
                    e.getMessage(), e);
        }

        return response;
    }
}