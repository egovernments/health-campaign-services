package org.egov.fileProcessor.apiClient;

import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

public class planAPIClient {
    @Value("${plan-search-api}")
    private static String planSearchAPI;
    public static  Map<String, Object> planSearchAPI(String id){
        //Make request to the EndPoint
        String completeURL = "http://localhost:5000/planSearch" +'/'+id;
        //TODO fix and get it from the properties file
        System.out.println(completeURL);

        RestTemplate restTemplate = new RestTemplate();

        // Set the request headers (if needed)
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        // Create the HTTP entity with the headers
        HttpEntity<?> entity = new HttpEntity<>(headers);

        // Make the HTTP request and get the response
        ResponseEntity<Map> response = restTemplate.exchange(
                completeURL,
                HttpMethod.GET,
                entity,
                Map.class
        );

        // Get the response body as a Map
        Map<String, Object> responseBody = response.getBody();

        return responseBody;

    }
}
