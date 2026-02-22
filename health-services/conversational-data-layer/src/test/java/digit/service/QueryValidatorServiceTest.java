package digit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.CdlConfiguration;
import digit.web.models.QueryValidationResult;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class QueryValidatorServiceTest {

    private QueryValidatorService queryValidatorService;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        CdlConfiguration config = CdlConfiguration.builder()
                .queryMaxSize(100)
                .queryDefaultSize(10)
                .queryTimeout("5s")
                .queryMaxAggDepth(2)
                .build();
        queryValidatorService = new QueryValidatorService(objectMapper, config);
    }

    // --- Valid queries ---

    @Test
    public void testValidate_simpleTermQuery() {
        String query = """
                {"query":{"bool":{"must":[{"term":{"status":"DELIVERED"}}]}},"size":10}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertTrue(result.isValid());
        assertNotNull(result.getSanitizedQuery());
        // Should have timeout injected
        assertTrue(result.getSanitizedQuery().contains("\"timeout\":\"5s\""));
    }

    @Test
    public void testValidate_matchQuery() {
        String query = """
                {"query":{"bool":{"must":[{"match":{"nameOfUser":"John"}}]}},"size":5}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertTrue(result.isValid());
    }

    @Test
    public void testValidate_rangeQuery() {
        String query = """
                {"query":{"bool":{"filter":[{"range":{"createdTime":{"gte":1700000000,"lte":1710000000}}}]}}}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertTrue(result.isValid());
        // Default size should be injected
        assertTrue(result.getSanitizedQuery().contains("\"size\":10"));
    }

    @Test
    public void testValidate_withAggregation() {
        String query = """
                {"query":{"match_all":{}},"aggs":{"status_count":{"terms":{"field":"status"}}},"size":0}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertTrue(result.isValid());
    }

    // --- Invalid queries ---

    @Test
    public void testValidate_invalidJson() {
        String query = "not json at all";
        QueryValidationResult result = queryValidatorService.validate(query);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("Invalid JSON"));
    }

    @Test
    public void testValidate_notJsonObject() {
        String query = "[1, 2, 3]";
        QueryValidationResult result = queryValidatorService.validate(query);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("JSON object"));
    }

    @Test
    public void testValidate_blockedScriptQuery() {
        String query = """
                {"query":{"bool":{"must":[{"script":{"script":"doc['status'].value == 'DELIVERED'"}}]}}}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("script"));
    }

    @Test
    public void testValidate_blockedRegexpQuery() {
        String query = """
                {"query":{"regexp":{"nameOfUser":".*admin.*"}}}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("regexp"));
    }

    @Test
    public void testValidate_blockedWildcardQuery() {
        String query = """
                {"query":{"wildcard":{"nameOfUser":"*admin*"}}}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("wildcard"));
    }

    @Test
    public void testValidate_blockedFuzzyQuery() {
        String query = """
                {"query":{"fuzzy":{"nameOfUser":{"value":"admin"}}}}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("fuzzy"));
    }

    @Test
    public void testValidate_disallowedTopLevelKey() {
        String query = """
                {"query":{"match_all":{}},"script_fields":{"test":{}}}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("Disallowed top-level key"));
    }

    // --- Size enforcement ---

    @Test
    public void testValidate_sizeExceedsMax() {
        String query = """
                {"query":{"match_all":{}},"size":500}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertTrue(result.isValid());
        // Size should be capped at 100
        assertTrue(result.getSanitizedQuery().contains("\"size\":100"));
    }

    @Test
    public void testValidate_defaultSizeInjected() {
        String query = """
                {"query":{"match_all":{}}}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertTrue(result.isValid());
        assertTrue(result.getSanitizedQuery().contains("\"size\":10"));
    }

    // --- Timeout injection ---

    @Test
    public void testValidate_timeoutInjected() {
        String query = """
                {"query":{"match_all":{}},"size":10}
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertTrue(result.isValid());
        assertTrue(result.getSanitizedQuery().contains("\"timeout\":\"5s\""));
    }

    // --- Aggregation depth ---

    @Test
    public void testValidate_aggDepthExceeded() {
        // 3 levels: level1 -> level2 -> level3 — exceeds max of 2
        String query = """
                {
                  "query":{"match_all":{}},
                  "aggs":{
                    "level1":{
                      "terms":{"field":"status"},
                      "aggs":{
                        "level2":{
                          "terms":{"field":"gender"},
                          "aggs":{
                            "level3":{
                              "terms":{"field":"role"}
                            }
                          }
                        }
                      }
                    }
                  },
                  "size":0
                }
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("Aggregation depth"));
    }

    @Test
    public void testValidate_aggDepthWithinLimit() {
        // 2 levels: level1 -> level2 — within max of 2
        String query = """
                {
                  "query":{"match_all":{}},
                  "aggs":{
                    "level1":{
                      "terms":{"field":"status"},
                      "aggs":{
                        "level2":{
                          "terms":{"field":"gender"}
                        }
                      }
                    }
                  },
                  "size":0
                }
                """;
        QueryValidationResult result = queryValidatorService.validate(query);
        assertTrue(result.isValid());
    }
}
