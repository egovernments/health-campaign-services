package digit.service;

import digit.web.models.IndexSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PromptBuilderService {

    private final SchemaService schemaService;

    public PromptBuilderService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * Builds the system prompt that instructs the LLM how to generate ES queries.
     */
    public String buildSystemPrompt(IndexSchema schema) {
        String schemaText = schemaService.formatSchemaForPrompt(schema);

        return """
                You are an Elasticsearch query generator for the DIGIT urban governance platform.

                RULES:
                1. Output ONLY valid Elasticsearch Query DSL as JSON. No explanations, no markdown, no code fences.
                2. NEVER generate write operations (no _update, _delete, _index, _bulk).
                3. NEVER use script queries, regexp, wildcard, or fuzzy queries.
                4. Always include "size": 10 unless the user specifies a different count.
                5. Use only the fields listed in the schema below.
                6. For date ranges, use "range" with "gte"/"lte" in epoch_millis or ISO format.
                7. Always wrap in a "bool" query for composability.
                8. For keyword fields, use "term" or "terms" queries (exact match).
                9. For text fields, use "match" or "match_phrase" queries.
                10. When a user mentions a location like a state, LGA, ward, or community, search in the boundaryHierarchy fields.
                11. For status queries, use the exact enum values: ADMINISTRATION_FAILED, ADMINISTRATION_SUCCESS, BENEFICIARY_REFUSED, CLOSED_HOUSEHOLD, DELIVERED, NOT_ADMINISTERED, INELIGIBLE.
                12. Output raw JSON only — no wrapping text, no markdown code blocks.

                SCHEMA:
                """ + schemaText;
    }

    /**
     * Builds the user message containing the sanitized query.
     */
    public String buildUserMessage(String sanitizedQuery) {
        return "USER QUERY: " + sanitizedQuery;
    }
}
