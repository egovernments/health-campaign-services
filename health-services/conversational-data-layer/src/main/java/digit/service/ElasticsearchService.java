package digit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class ElasticsearchService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ElasticsearchService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes a read-only search query against the specified index.
     * Endpoint is hardcoded to _search — never LLM-controlled.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeSearch(String indexName, String queryJson) throws Exception {
        // Hardcoded to _search only — defense against prompt injection
        String endpoint = "/" + indexName + "/_search";
        log.info("Executing ES search on endpoint: {}", endpoint);
        log.debug("Query body: {}", queryJson);

        Request request = new Request("POST", endpoint);
        request.setJsonEntity(queryJson);
        request.addParameter("ignore_unavailable", "true");

        Response response = restClient.performRequest(request);
        String responseBody = EntityUtils.toString(response.getEntity());

        log.debug("ES response status: {}", response.getStatusLine().getStatusCode());
        return objectMapper.readValue(responseBody, Map.class);
    }
}
