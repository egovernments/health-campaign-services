package org.egov.processor.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.ServiceCallException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.egov.processor.config.ServiceConstants.EXTERNAL_SERVICE_EXCEPTION;
import static org.egov.processor.config.ServiceConstants.SEARCHER_SERVICE_EXCEPTION;

@Repository
@Slf4j
public class ServiceRequestRepository {

    private ObjectMapper mapper;

    private RestTemplate restTemplate;


    @Autowired
    public ServiceRequestRepository(ObjectMapper mapper, RestTemplate restTemplate) {
        this.mapper = mapper;
        this.restTemplate = restTemplate;
    }


    public Object fetchResult(StringBuilder uri, Object request) {
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        Object response = null;
        try {
            response = restTemplate.postForObject(uri.toString(), request, Map.class);
        }catch(HttpClientErrorException e) {
        	log.error(SEARCHER_SERVICE_EXCEPTION, "Error occurred while fetching data: {}", e.getMessage());
            throw new ServiceCallException(e.getResponseBodyAsString());
        }catch(Exception e) {
        	log.error(SEARCHER_SERVICE_EXCEPTION, "Error occurred while fetching data: {}", e.getMessage());
            throw new ServiceCallException(e.getMessage());
        }

        return response;
    }

    public Object fetchResultWithGET(StringBuilder uri) {
        Object response = null;
        try {
            response = restTemplate.getForObject(uri.toString(), byte[].class);
        } catch (HttpClientErrorException e) {
        	log.error(SEARCHER_SERVICE_EXCEPTION, "Error occurred while fetching data: {}", e.getMessage());
            throw new ServiceCallException(e.getResponseBodyAsString());
        } catch (Exception e) {
        	log.error(SEARCHER_SERVICE_EXCEPTION, "Error occurred while fetching data: {}", e.getMessage());
            throw new ServiceCallException(e.getMessage());
        }
        return response;
    }

    public ResponseEntity<String> sendHttpRequest(String url, HttpEntity<MultiValueMap<String, Object>> requestEntity) {
        return restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
    }
}