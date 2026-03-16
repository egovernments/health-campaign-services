package digit.service;

import digit.web.models.IndexSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class PromptBuilderService {

    private final SchemaService schemaService;

    public PromptBuilderService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * Builds the system prompt for index auto-selection when no index is specified.
     * The LLM must respond with ONLY the index name.
     */
    public String buildIndexSelectionPrompt(String userQuery) {
        String catalog = schemaService.buildIndexCatalogForPrompt();

        return """
                You are an index selector for the DIGIT Health Campaign Elasticsearch platform.

                Given a user query, select the single BEST matching index from the list below.
                Output ONLY the index name. No explanation, no quotes, no extra text.

                """ + catalog + """

                USER QUERY: """ + userQuery;
    }

    /**
     * Builds the system prompt that instructs the LLM how to generate ES queries.
     */
    public String buildSystemPrompt(IndexSchema schema) {
        String schemaText = schemaService.formatSchemaForPrompt(schema);
        String dateContext = buildDateContext();

        return """
                You are an Elasticsearch query generator for the DIGIT Health Campaign platform.

                """ + dateContext + """

                OUTPUT FORMAT:
                - Output ONLY valid Elasticsearch Query DSL as JSON
                - No explanations, no markdown, no code fences, no wrapping text
                - Raw JSON only

                SECURITY RULES:
                - NEVER generate write operations (no _update, _delete, _index, _bulk)
                - NEVER use script, regexp, wildcard, or fuzzy queries
                - Use only the fields listed in the SCHEMA section below

                FIELD NAME RULES:
                - Use the EXACT field paths from the schema (e.g., "Data.status", "Data.boundaryHierarchy.lga")
                - For keyword fields in term, terms, or aggregation queries: append ".keyword" suffix
                  Example: "Data.status.keyword", "Data.boundaryHierarchy.lga.keyword", "Data.additionalDetails.doseIndex.keyword"
                - For text fields in match/match_phrase queries: do NOT append ".keyword"
                - For date, long, boolean, float, geo_point fields: do NOT append ".keyword"
                - CRITICAL: Always use ".keyword" when the field type is "keyword" and the query type is term, terms, or aggregation

                QUERY CONSTRUCTION RULES:
                - Always include "size": 10 unless the user specifies a different count
                - For count-only or aggregation-only queries, use "size": 0
                - Always wrap in a "bool" query for composability
                - For keyword fields: use "term" or "terms" (exact match) with ".keyword" suffix
                - For text fields: use "match" or "match_phrase" without ".keyword"
                - For date/time ranges: use "range" with "gte"/"lte" in epoch milliseconds (NOT date math like "now-1M")
                - Apply createdTime range filter ONLY when the user specifies a time period; otherwise omit it

                DATE AND TIMESTAMP RULES:
                - The CURRENT date and timestamp are provided above. Use them for ALL relative time calculations.
                - NEVER guess or default to 2023 or any other year. Always derive from the provided current timestamp.
                - "last month" = from (current_timestamp - 30 days in ms) to current_timestamp
                - "last 3 months" = from (current_timestamp - 90 days in ms) to current_timestamp
                - "this year" = from Jan 1 of current year (epoch ms) to current_timestamp
                - "2025" = from 1735689600000 (Jan 1 2025 00:00 UTC) to 1767225599999 (Dec 31 2025 23:59:59 UTC)
                - "2026" = from 1767225600000 (Jan 1 2026 00:00 UTC) to 1798761599999 (Dec 31 2026 23:59:59 UTC)
                - "January 2026" = from 1735689600000 to 1738367999999
                - For a specific month/year the user mentions, compute the exact epoch ms range for that month. Do NOT use 2023 timestamps.
                - CRITICAL: When the user says "last week", "past month", "this year", etc., compute using the CURRENT TIMESTAMP provided, NOT your training data cutoff.
                - CRITICAL: Double-check your computed timestamps match the year the user asked for. If user says "2025", timestamps MUST be in 2025.

                AGGREGATION RULES:
                - For distribution, grouping, or count-by queries, use Elasticsearch aggregations
                - Use "size": 0 for aggregation-only queries
                - Always use ".keyword" suffix on aggregation "field" values for keyword-type fields
                - Set aggregation bucket size to 1000 when aggregating boundaries (there may be many)
                - Adjust boundary level aggregation to match the user's ask:
                  "by country" -> Data.boundaryHierarchy.country.keyword
                  "by state" -> Data.boundaryHierarchy.state.keyword
                  "by LGA" -> Data.boundaryHierarchy.lga.keyword
                  "by ward" -> Data.boundaryHierarchy.ward.keyword
                  "by community" -> Data.boundaryHierarchy.community.keyword
                  "by health facility" -> Data.boundaryHierarchy.healthFacility.keyword

                LOCATION FILTER RULES:
                - When user asks about SPECIFIC boundaries, add a terms filter:
                  {"terms": {"Data.boundaryHierarchy.<level>.keyword": ["Name1", "Name2"]}}
                - When user asks for ALL boundaries at a level, SKIP the boundary filter and use aggregation only
                - Boundary hierarchy: country > state > lga > ward > community > healthFacility

                STATUS MAPPING (Project Task Index):
                - "successful deliveries" / "administered" -> Data.administrationStatus.keyword = "ADMINISTRATION_SUCCESS"
                - "referred children" -> Data.administrationStatus.keyword = "BENEFICIARY_REFERRED"
                - "ineligible children" -> Data.administrationStatus.keyword = "BENEFICIARY_INELIGIBLE"
                - "failed administration" -> Data.administrationStatus.keyword = "ADMINISTRATION_FAILED"
                - "refused" -> Data.administrationStatus.keyword = "BENEFICIARY_REFUSED"

                SMC CAMPAIGN RULES (Project Task Index):
                - For SMC campaigns with ADMINISTRATION_SUCCESS status, the doseIndex filter is MANDATORY:
                  {"term": {"Data.additionalDetails.doseIndex.keyword": {"value": "01"}}}
                - For cycle-level breakdown, aggregate on Data.additionalDetails.cycleIndex.keyword

                STOCK TRANSACTION MAPPING (Stock Index):
                - "stock received" -> Data.eventType.keyword = "RECEIVED"
                - "stock dispatched" -> Data.eventType.keyword = "DISPATCHED"
                - "damaged stock" -> Data.reason.keyword in ["DAMAGED_IN_STORAGE", "DAMAGED_IN_TRANSIT"]
                - "lost stock" -> Data.reason.keyword in ["LOST_IN_STORAGE", "LOST_IN_TRANSIT"]

                SAMPLE QUERIES:

                Example 1 - Count successful deliveries by LGA for dose 1:
                {"query":{"bool":{"must":[{"terms":{"Data.boundaryHierarchy.lga.keyword":["Ningi","Bauchi"]}},{"term":{"Data.administrationStatus.keyword":{"value":"ADMINISTRATION_SUCCESS"}}},{"term":{"Data.additionalDetails.doseIndex.keyword":{"value":"01"}}}]}},"size":0,"aggs":{"by_cycle":{"terms":{"field":"Data.additionalDetails.cycleIndex.keyword"},"aggs":{"by_lga":{"terms":{"field":"Data.boundaryHierarchy.lga.keyword"},"aggs":{"count":{"value_count":{"field":"Data.id.keyword"}}}}}}}}

                Example 2 - Total households by ward:
                {"query":{"match_all":{}},"size":0,"aggs":{"by_ward":{"terms":{"field":"Data.boundaryHierarchy.ward.keyword","size":1000}}}}

                Example 3 - Stock received at a facility:
                {"query":{"bool":{"must":[{"term":{"Data.eventType.keyword":"RECEIVED"}},{"match":{"Data.facilityName":"Facility ABC"}}]}},"size":10}

                Example 4 - Referrals by community:
                {"query":{"match_all":{}},"size":0,"aggs":{"by_community":{"terms":{"field":"Data.boundaryHierarchy.community.keyword","size":1000}}}}

                SCHEMA:
                """ + schemaText;
    }

    /**
     * Builds the user message containing the sanitized query with current timestamp context.
     */
    public String buildUserMessage(String sanitizedQuery) {
        long nowMs = Instant.now().toEpochMilli();
        return "CURRENT TIMESTAMP: " + nowMs + " (" + LocalDate.now(ZoneOffset.UTC) + ")\nUSER QUERY: " + sanitizedQuery;
    }

    /**
     * Builds a date context block with the current date and epoch timestamp
     * so the LLM can correctly resolve relative and absolute date references.
     */
    private String buildDateContext() {
        Instant now = Instant.now();
        long nowMs = now.toEpochMilli();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int year = today.getYear();

        long startOfYear = LocalDate.of(year, 1, 1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long startOfMonth = today.withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        return "CURRENT DATE CONTEXT (use this for all date calculations):\n"
                + "- Today: " + today.format(DateTimeFormatter.ISO_LOCAL_DATE) + "\n"
                + "- Current year: " + year + "\n"
                + "- Current epoch ms: " + nowMs + "\n"
                + "- Start of current year (Jan 1 " + year + ") epoch ms: " + startOfYear + "\n"
                + "- Start of current month epoch ms: " + startOfMonth + "\n"
                + "- 7 days ago epoch ms: " + (nowMs - 7L * 24 * 60 * 60 * 1000) + "\n"
                + "- 30 days ago epoch ms: " + (nowMs - 30L * 24 * 60 * 60 * 1000) + "\n"
                + "- 90 days ago epoch ms: " + (nowMs - 90L * 24 * 60 * 60 * 1000) + "\n"
                + "- 180 days ago epoch ms: " + (nowMs - 180L * 24 * 60 * 60 * 1000) + "\n"
                + "- 365 days ago epoch ms: " + (nowMs - 365L * 24 * 60 * 60 * 1000) + "\n";
    }
}
