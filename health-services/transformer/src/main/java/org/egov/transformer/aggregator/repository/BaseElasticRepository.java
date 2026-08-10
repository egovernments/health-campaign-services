package org.egov.transformer.aggregator.repository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.aggregator.config.ServiceConfiguration;
import org.egov.transformer.aggregator.models.AggregatedHousehold;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class BaseElasticRepository {

  private static final String PATH_SEPARATOR = "/";

  private final ServiceConfiguration config;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Autowired
  public BaseElasticRepository(
      @Qualifier("customRestTemplate") RestTemplate restTemplate, ServiceConfiguration config,
      @Qualifier("customObjectMapper") ObjectMapper objectMapper) {
    this.restTemplate = restTemplate;
    this.config = config;
    this.objectMapper = objectMapper;
  }

  public String fetchDocuments(String index, String request) {
    try {
      log.debug("Query: {}", request);
      return restTemplate.postForEntity(getSearchUri(index),
              new HttpEntity<>(request, getHeaders()),
              String.class)
          .getBody();
    } catch (ResourceAccessException e) {
      log.error("ES is DOWN: {}", e.getMessage(), e);
    } catch (HttpClientErrorException e) {
      log.error("Client error occurred while querying the ES documents: {}", e.getMessage(), e);
    } catch (HttpServerErrorException e) {
      log.error("Server error occurred while querying the ES documents: {}", e.getMessage(), e);
    } catch (Exception e) {
      log.error("Unexpected exception occurred while querying the ES documents: {}", e.getMessage(),
          e);
      throw e;
    }
    return null;
  }

  public void createOrUpdateDocument(Object payload, String index, String documentId, long seqNo,
      long primaryTerm) {
    try {
      String uri = getUpdateUri(index, documentId, seqNo, primaryTerm);
      restTemplate.exchange(uri,
          HttpMethod.PUT, new HttpEntity<>(payload, getHeaders()), Void.class);
    } catch (ResourceAccessException e) {
      log.error("ES is DOWN: {}", e.getMessage(), e);
    } catch (HttpClientErrorException e) {
      log.error("Client error occurred while querying the ES documents: {}", e.getMessage());
      throw e;
    } catch (HttpServerErrorException e) {
      log.error("Server error occurred while querying the ES documents: {}", e.getMessage(), e);
    } catch (Exception e) {
      log.error("Unexpected exception occurred while querying the ES documents: {}", e.getMessage(),
          e);
    }
  }

  public void upsertAggregatedHousehold(String index, String householdId,
      AggregatedHousehold household) {
    try {

      Map<String, Object> script = new HashMap<>();
      script.put("source", """
              if (ctx._source.householdMembers == null) {
                  ctx._source.householdMembers = [];
              }
              if (ctx._source.individuals == null) {
                  ctx._source.individuals = [];
              }

              for (newMember in params.newHousehold.householdMembers) {
                  boolean memberExists = false;
                  for (int i = 0; i < ctx._source.householdMembers.size(); i++) {
                      if (ctx._source.householdMembers[i].id == newMember.id) {
                          ctx._source.householdMembers[i] = newMember;
                          memberExists = true;
                          break;
                      }
                  }
                  if (!memberExists) {
                      ctx._source.householdMembers.add(newMember);
                  }
              }

              for (newIndividual in params.newHousehold.individuals) {
                  boolean individualExists = false;
                  for (int i = 0; i < ctx._source.individuals.size(); i++) {
                      if (ctx._source.individuals[i].id == newIndividual.id) {
                          ctx._source.individuals[i] = newIndividual;
                          individualExists = true;
                          break;
                      }
                  }
                  if (!individualExists) {
                      ctx._source.individuals.add(newIndividual);
                  }
              }
          """);
      script.put("params", Map.of("newHousehold", household));

      Map<String, Object> upsert = new HashMap<>(objectMapper.convertValue(household, Map.class));

      Map<String, Object> payload = new HashMap<>();
      payload.put("script", script);
      payload.put("upsert", upsert);

      HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(payload),
          getHeaders());

      ResponseEntity<String> response = restTemplate.exchange(
          getUpdateWithQueryUri(index, householdId), HttpMethod.POST, request,
          String.class);

      if (response.getStatusCode().is2xxSuccessful()) {
        JsonNode jsonResponse = objectMapper.readTree(response.getBody());
        log.debug("Upsert operation result ::: {}", jsonResponse);
      } else {
        log.error("Failed to upsert document ::: {}", response.getStatusCode());
      }

    } catch (Exception e) {
      log.error("Failed to upsert document ::: {}", e.getMessage(), e);
      throw new CustomException("AGGREGATE_FAILED", e.getMessage());
    }
  }

  private String getSearchUri(String index) {
    return config.getEsHostUrl() + index + config.getSearchPath() + "?"
        + config.getSearchParameter();
  }

  private String getUpdateWithQueryUri(String index, String documentId) {
    return config.getEsHostUrl() + index + config.getUpdatePath() + PATH_SEPARATOR + documentId;
  }

  private String getUpdateUri(String index, String documentId, long seqNo, long primaryTerm) {
    String basePath = config.getEsHostUrl() + index
        + config.getDocPath() + PATH_SEPARATOR + documentId;
    return primaryTerm == 0L
        ? basePath
        : basePath + "?if_seq_no=" + seqNo + "&if_primary_term=" + primaryTerm
            + "&refresh=wait_for";
  }

  private HttpHeaders getHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add("Authorization", getEsEncodedCredentials());
    return headers;
  }

  private String getEsEncodedCredentials() {
    String credentials = config.getEsUsername() + ":" + config.getEsPassword();
    return "Basic " + Base64.getEncoder()
        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
  }
}
