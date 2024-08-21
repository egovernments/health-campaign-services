package org.egov.transformer.aggregator.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.aggregator.config.ServiceConfiguration;
import org.egov.transformer.aggregator.models.AggregatedHousehold;
import org.egov.transformer.aggregator.models.UserActionLocationCaptureIndexRecord;
import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.json.JsonParseException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import static org.egov.transformer.aggregator.config.ServiceConstants.ES_HITS_PATH;
import static org.egov.transformer.aggregator.config.ServiceConstants.ES_TERM_QUERY;

@Component
@Slf4j
public class ElasticSearchRepository extends BaseElasticRepository {

  private final ObjectMapper mapper;

  @Autowired
  public ElasticSearchRepository(
      @Qualifier("customRestTemplate") RestTemplate restTemplate,
      ServiceConfiguration config, @Qualifier("customObjectMapper") ObjectMapper mapper) {
    super(restTemplate, config, mapper);
    this.mapper = mapper;
  }

  public <T> List<T> findBySearchKeyValue(String searchKeyValue, String index, String searchKeyPath,
      Class<T> type) {
    String response = this.fetchDocuments(index, getPayload(searchKeyPath, searchKeyValue));
    return mapResponseToList(response, type);
  }

  public <T> Optional<T> findBySearchValueAndWithSeqNo(String searchKeyValue, String index,
      String searchKeyPath, TypeReference<T> typeRef) {
    String response = this.fetchDocuments(index, getPayload(searchKeyPath, searchKeyValue));
    return parseElasticsearchHit(response, typeRef);
  }

  private String getPayload(String searchKeyPath, String searchKeyValue) {
    return String.format(
        ES_TERM_QUERY,
        searchKeyPath, searchKeyValue
    );
  }

  private <T> Optional<T> parseElasticsearchHit(String reponse, TypeReference<T> typeRef) {
    try {
      JsonNode root = mapper.readTree(reponse);
      JsonNode hits = root.path("hits").path("hits");
      if (hits.isArray() && !hits.isEmpty()) {
        return Optional.of(mapper.convertValue(hits.get(0), typeRef));
      } else if (hits.isArray() && hits.isEmpty()) {
        log.debug("Could not find any documents in elastic db for in {}",
            typeRef.getType().getTypeName());
        return Optional.empty();
      }
      throw new RuntimeException("Failed to parse Elasticsearch response ::: " + root);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse Elasticsearch response", e);
    }
  }

  private <T> List<T> mapResponseToList(String response, Class<T> type) {
    List<T> documentList = new ArrayList<>(Collections.emptyList());
    try {
      JSONArray jsonArray = constructJsonArray(response, ES_HITS_PATH);
      for (int i = 0; i < jsonArray.length(); i++) {
        if (jsonArray.get(i) != null) {
          T o = mapper.readValue(jsonArray.getJSONObject(i).toString(), type);
          documentList.add(o);
        }
      }
      return documentList;
    } catch (JsonProcessingException e) {
      log.error("Error parsing JSON", e);
      throw new JsonParseException(e);
    }
  }

  private JSONArray constructJsonArray(String json, String jsonPath) {
    try {
      return new JSONArray(JsonPath.read(json, jsonPath).toString());
    } catch (PathNotFoundException e) {
      log.error("JSON path not found: {}", jsonPath, e);
      return new JSONArray();
    } catch (JSONException e) {
      log.error("Error parsing JSON: {}", json, e);
      throw e;
    } catch (Exception e) {
      log.error("Exception while constructing JSON array: ", e);
      log.error("Object: {}", json);
      throw e;
    }
  }


  //TODO
  //FIXME

  public void upsertUserActionLocationCaptureEntry(String index, String locationCaptureId, UserActionLocationCaptureIndexRecord userActionLocationCaptureIndexRecord) {
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
}
