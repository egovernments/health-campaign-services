package digit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.CdlConfiguration;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static digit.config.ErrorConstants.*;

@Service
@Slf4j
public class QueryOrchestrationService {

    private final PiiSanitizerService piiSanitizerService;
    private final SchemaService schemaService;
    private final PromptBuilderService promptBuilderService;
    private final LlmTranslationService llmTranslationService;
    private final QueryValidatorService queryValidatorService;
    private final ElasticsearchService elasticsearchService;
    private final CdlConfiguration config;
    private final ObjectMapper objectMapper;

    public QueryOrchestrationService(PiiSanitizerService piiSanitizerService,
                                     SchemaService schemaService,
                                     PromptBuilderService promptBuilderService,
                                     LlmTranslationService llmTranslationService,
                                     QueryValidatorService queryValidatorService,
                                     ElasticsearchService elasticsearchService,
                                     CdlConfiguration config,
                                     ObjectMapper objectMapper) {
        this.piiSanitizerService = piiSanitizerService;
        this.schemaService = schemaService;
        this.promptBuilderService = promptBuilderService;
        this.llmTranslationService = llmTranslationService;
        this.queryValidatorService = queryValidatorService;
        this.elasticsearchService = elasticsearchService;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Full pipeline:
     * 1. Validate input
     * 2. Sanitize PII from query text
     * 3. Resolve index schema
     * 4. Build LLM prompt
     * 5. Call LLM for translation
     * 6. Extract JSON from LLM response
     * 7. Post-LLM PII check
     * 8. Validate generated query
     * 9. Execute against ES
     * 10. Build response
     */
    @SuppressWarnings("unchecked")
    public CdlQueryResponse processQuery(CdlQuery cdlQuery) {
        long startTime = System.currentTimeMillis();

        // 1. Validate input
        if (cdlQuery.getQueryText() == null || cdlQuery.getQueryText().isBlank()) {
            throw new CustomException(EMPTY_QUERY_CODE, EMPTY_QUERY_MSG);
        }

        // 2. Sanitize PII
        String sanitizedQuery = piiSanitizerService.sanitize(cdlQuery.getQueryText());
        log.info("Sanitized query: {}", sanitizedQuery);

        // 3. Resolve index
        String indexName = cdlQuery.getIndexName();
        if (indexName == null || indexName.isBlank()) {
            indexName = config.getDefaultIndexName();
        }
        if (!schemaService.hasSchema(indexName)) {
            throw new CustomException(UNKNOWN_INDEX_CODE, UNKNOWN_INDEX_MSG + indexName);
        }
        IndexSchema schema = schemaService.getSchema(indexName);

        // 4. Build prompt
        String systemPrompt = promptBuilderService.buildSystemPrompt(schema);
        String userMessage = promptBuilderService.buildUserMessage(sanitizedQuery);

        // 5. Call LLM
        String llmResponse;
        try {
            llmResponse = llmTranslationService.translate(systemPrompt, userMessage);
        } catch (Exception e) {
            log.error("LLM translation failed", e);
            throw new CustomException(LLM_ERROR_CODE, LLM_ERROR_MSG + e.getMessage());
        }

        // 6. Extract JSON
        String extractedJson = llmTranslationService.extractJson(llmResponse);
        if (extractedJson == null || extractedJson.isBlank()) {
            throw new CustomException(PARSE_ERROR_CODE, PARSE_ERROR_MSG);
        }

        // 7. Post-LLM PII check
        if (piiSanitizerService.containsPii(extractedJson)) {
            log.warn("PII detected in LLM response, rejecting query");
            throw new CustomException(PII_IN_RESPONSE_CODE, PII_IN_RESPONSE_MSG);
        }

        // 8. Validate query
        QueryValidationResult validationResult = queryValidatorService.validate(extractedJson);
        if (!validationResult.isValid()) {
            log.warn("Query validation failed: {}", validationResult.getReason());
            throw new CustomException(INVALID_QUERY_CODE, INVALID_QUERY_MSG + validationResult.getReason());
        }

        String validatedQuery = validationResult.getSanitizedQuery();
        log.info("Validated query: {}", validatedQuery);

        // 9. Execute search
        Map<String, Object> esResponse;
        try {
            esResponse = elasticsearchService.executeSearch(indexName, validatedQuery);
        } catch (Exception e) {
            log.error("ES search failed", e);
            throw new CustomException(ES_ERROR_CODE, ES_ERROR_MSG + e.getMessage());
        }

        // 10. Build response
        long queryTimeMs = System.currentTimeMillis() - startTime;

        // Extract total hits
        Long totalHits = null;
        Map<String, Object> hits = (Map<String, Object>) esResponse.get("hits");
        if (hits != null) {
            Object total = hits.get("total");
            if (total instanceof Map) {
                totalHits = ((Number) ((Map<String, Object>) total).get("value")).longValue();
            } else if (total instanceof Number) {
                totalHits = ((Number) total).longValue();
            }
        }

        // Parse the validated query back to a Map for the response
        Map<String, Object> generatedQueryMap;
        try {
            generatedQueryMap = objectMapper.readValue(validatedQuery, Map.class);
        } catch (Exception e) {
            generatedQueryMap = Map.of("raw", validatedQuery);
        }

        return CdlQueryResponse.builder()
                .data(esResponse)
                .generatedQuery(generatedQueryMap)
                .totalHits(totalHits)
                .queryTimeMs(queryTimeMs)
                .sanitizedQueryText(sanitizedQuery)
                .build();
    }
}
