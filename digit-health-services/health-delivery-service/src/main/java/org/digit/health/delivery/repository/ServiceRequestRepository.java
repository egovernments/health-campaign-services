package org.digit.health.delivery.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class ServiceRequestRepository {
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public ServiceRequestRepository(ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }


    public Object fetchResult(StringBuilder uri, Object request) {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return restTemplate.postForObject(uri.toString(), request, Map.class);
    }

    public Object fetchResult(StringBuilder uri, Object request, Class clazz) {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return restTemplate.postForObject(uri.toString(), request, clazz);
    }

    public Object fetchResult(StringBuilder uri, Class clazz) {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return restTemplate.getForEntity(uri.toString(), clazz);
    }
}
