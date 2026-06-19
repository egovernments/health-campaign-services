package org.egov.common.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Client for making service requests via HTTP.
 * This class provides methods to fetch results from a service using HTTP POST requests.
 */
@Repository
@Slf4j
public class ServiceRequestClient {

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    // Constructor injection of ObjectMapper and RestTemplate
    @Autowired
    public ServiceRequestClient(@Qualifier("objectMapper") ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * Fetches result from a service using HTTP POST.
     *
     * @param uri     The URI to send the request to.
     * @param request The request object to send.
     * @param clazz   The class of the response object.
     * @param <T>     The type of the response object.
     * @return The response object received from the service.
     * @throws CustomException If an error occurs during the service request.
     */
    public <T> T fetchResult(StringBuilder uri, Object request, Class<T> clazz) {
        // Configure the ObjectMapper to ignore empty beans during serialization
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        T response;
        try {
            // Perform HTTP POST request and receive the response
            response = restTemplate.postForObject(uri.toString(), request, clazz);
        } catch (HttpClientErrorException e) {
            // Handle HTTP client errors
            throw new CustomException("HTTP_CLIENT_ERROR",
                    String.format("%s - %s", e.getMessage(), e.getResponseBodyAsString()));
        } catch (Exception exception) {
            // Handle other exceptions
            throw new CustomException("SERVICE_REQUEST_CLIENT_ERROR",
                    exception.getMessage());
        }
        return response;
    }
}
