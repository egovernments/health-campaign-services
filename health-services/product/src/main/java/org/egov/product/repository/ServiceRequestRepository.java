package org.egov.product.repository;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.ServiceCallException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Repository
@Slf4j
public class ServiceRequestRepository {

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;


    @Autowired
    public ServiceRequestRepository(ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }


    public <T> T fetchResult(StringBuilder uri, Object request, Class
            <T> clazz) {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        T response = null;
        try {
            response = restTemplate.postForObject(uri.toString(), request, clazz);
        } catch (HttpClientErrorException e) {
            log.error("External service threw an exception", e);
            throw new ServiceCallException(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unknown error during network call", e);
            throw new ServiceCallException(e.getMessage());
        }
        return response;
    }
}