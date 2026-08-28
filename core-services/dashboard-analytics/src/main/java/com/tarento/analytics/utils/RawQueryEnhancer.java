package com.tarento.analytics.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.egov.tracer.model.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.constant.Constants;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.PaginationDto;
import com.tarento.analytics.dto.RangeFilter;
import com.tarento.analytics.dto.SortCriteria;

/**
 * Adds request-driven filtering, sorting and continuation paging onto a raw-document Elasticsearch
 * query that was otherwise taken verbatim from chart configuration.
 *
 * <p>Everything here is additive and opt-in: when a request carries no pagination and no explicit
 * filters, the query is returned exactly as configuration defined it.
 */
@Component
public class RawQueryEnhancer {

	private static final Logger logger = LoggerFactory.getLogger(RawQueryEnhancer.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();

	// Defaulted so the class can be unit-tested without a Spring context; Spring injects over it.
	@Autowired
	private StockSummaryAggregation stockSummaryAggregation = new StockSummaryAggregation();

	private static final String QUERY = "query";
	private static final String BOOL = "bool";
	private static final String FILTER = "filter";
	private static final String TERM = "term";
	private static final String TERMS = "terms";
	private static final String RANGE = "range";
	private static final String ORDER = "order";
	private static final String GTE = "gte";
	private static final String LTE = "lte";
	private static final String SOURCE = "_source";
	private static final String KEYWORD_SUFFIX = ".keyword";

	static final String SIZE = "size";
	static final String SORT = "sort";
	static final String SEARCH_AFTER = "search_after";
	static final String TRACK_TOTAL_HITS = "track_total_hits";

	/**
	 * Field appended to every paginated sort so that documents sharing a sort value cannot be
	 * repeated across pages or skipped between them.
	 *
	 * <p>Held in code rather than per-tenant configuration deliberately: it is identical for every
	 * tenant, and the indexer nests every document under {@code Data} with an {@code id}, so this
	 * holds for any index this service reads. Confirm the field is mapped with a keyword sub-field
	 * before relying on paging against a new index.
	 */
	static final String TIEBREAKER_FIELD = "Data.id.keyword";

	/**
	 * Applies the request's explicit filters and paging controls to {@code queryRoot} in place.
	 *
	 * @param queryRoot   the Elasticsearch request body built from chart configuration
	 * @param request     the incoming dashboard request
	 * @param queryConfig the chart's query configuration node; decides whether this dataset returns
	 *                    documents at all, and bounds which fields the caller may name
	 */
	public void enhance(ObjectNode queryRoot, AggregateRequestDto request, JsonNode queryConfig) {
		if (queryRoot == null || request == null || !returnsDocuments(queryConfig)) {
			return;
		}
		Set<String> allowedFields = projectedFields(queryConfig);
		applyExplicitFilters(queryRoot, request.getTermFilters(), request.getRangeFilters(), allowedFields);
		applyPagination(queryRoot, request.getPagination(), allowedFields);
		// Totals are computed over everything the filters match, independently of how many documents
		// this page happens to return.
		stockSummaryAggregation.applyAggregation(queryRoot, request.getStockSummary());
	}

	/**
	 * A chart may mix document queries with aggregation queries, and the caller's paging describes
	 * only the document list. Applying it to an aggregation dataset would rewrite its {@code size: 0}
	 * and impose a sort it never asked for, so those datasets are left exactly as configured.
	 *
	 * <p>This is the same distinction {@code CompletenessCalculator} draws when deciding which
	 * datasets can meaningfully be called truncated.
	 */
	boolean returnsDocuments(JsonNode queryConfig) {
		if (queryConfig == null || !queryConfig.hasNonNull(Constants.JsonPaths.TRANSFORM_DATA)) {
			return false;
		}
		return Constants.JsonPaths.TRANSFORM_DATA_RAW_DOCUMENTS
				.equalsIgnoreCase(queryConfig.get(Constants.JsonPaths.TRANSFORM_DATA).asText());
	}

	/**
	 * The fields a caller may filter or sort on: exactly those the chart's own {@code _source}
	 * projection already returns.
	 *
	 * <p>Bounding it this way keeps a caller from naming a field the chart does not expose — sort
	 * values come back in the continuation token, so an unbounded field name would be readable one
	 * value at a time. An empty set means the chart declared no projection, and nothing can be
	 * inferred about its fields.
	 */
	Set<String> projectedFields(JsonNode queryConfig) {
		Set<String> fields = new HashSet<>();
		if (queryConfig == null) {
			return fields;
		}
		JsonNode aggrQuery = queryConfig.get(Constants.JsonPaths.AGGREGATION_QUERY);
		if (aggrQuery == null || !aggrQuery.isTextual()) {
			return fields;
		}
		try {
			JsonNode source = MAPPER.readTree(aggrQuery.asText()).get(SOURCE);
			if (source != null && source.isArray()) {
				for (JsonNode field : source) {
					fields.add(field.asText());
				}
			}
		} catch (Exception ex) {
			logger.warn("Could not read the configured _source projection; caller-named fields will not be "
					+ "restricted for this query: {}", ex.getMessage());
		}
		return fields;
	}

	/**
	 * @param field         a caller-supplied field path, possibly with a {@code .keyword} sub-field
	 * @param allowedFields the chart's projection; an empty set imposes no restriction
	 */
	private void requireAllowed(String field, Set<String> allowedFields, String usage) {
		if (allowedFields.isEmpty()) {
			return;
		}
		String base = field.endsWith(KEYWORD_SUFFIX)
				? field.substring(0, field.length() - KEYWORD_SUFFIX.length())
				: field;
		if (!allowedFields.contains(base) && !allowedFields.contains(field)) {
			throw new CustomException("INVALID_REQUEST_FIELD", "Cannot " + usage + " on '" + field
					+ "': this chart returns only " + allowedFields);
		}
	}

	/**
	 * Appends exact-match and range conditions to the query's bool filter clause.
	 *
	 * <p>These bypass the per-tenant {@code requestQueryMap}, so a tenant whose configuration was
	 * never given a mapping can still filter server-side.
	 */
	void applyExplicitFilters(ObjectNode queryRoot, Map<String, Object> termFilters, List<RangeFilter> rangeFilters,
			Set<String> allowedFields) {
		boolean hasTerms = termFilters != null && !termFilters.isEmpty();
		boolean hasRanges = rangeFilters != null && !rangeFilters.isEmpty();
		if (!hasTerms && !hasRanges) {
			return;
		}

		ArrayNode filterArray = (ArrayNode) queryRoot.with(QUERY).with(BOOL).withArray(FILTER);

		if (hasTerms) {
			for (Map.Entry<String, Object> entry : termFilters.entrySet()) {
				String field = entry.getKey();
				Object value = entry.getValue();
				if (StringUtils.isBlank(field) || value == null) {
					continue;
				}
				String trimmed = field.trim();
				requireAllowed(trimmed, allowedFields, "filter");
				filterArray.add(buildTermCondition(trimmed, value));
			}
		}

		if (hasRanges) {
			for (RangeFilter rangeFilter : rangeFilters) {
				if (rangeFilter == null || StringUtils.isBlank(rangeFilter.getField()) || !rangeFilter.hasBound()) {
					continue;
				}
				String trimmed = rangeFilter.getField().trim();
				requireAllowed(trimmed, allowedFields, "filter");
				filterArray.add(buildRangeCondition(trimmed, rangeFilter));
			}
		}
	}

	private ObjectNode buildTermCondition(String field, Object value) {
		ObjectNode condition = MAPPER.createObjectNode();
		if (value instanceof Collection || value.getClass().isArray()) {
			condition.with(TERMS).set(field, MAPPER.valueToTree(value));
		} else {
			condition.with(TERM).set(field, MAPPER.valueToTree(value));
		}
		return condition;
	}

	private ObjectNode buildRangeCondition(String field, RangeFilter rangeFilter) {
		ObjectNode condition = MAPPER.createObjectNode();
		ObjectNode bounds = condition.with(RANGE).with(field);
		if (rangeFilter.getFrom() != null) {
			bounds.set(GTE, MAPPER.valueToTree(rangeFilter.getFrom()));
		}
		if (rangeFilter.getTo() != null) {
			bounds.set(LTE, MAPPER.valueToTree(rangeFilter.getTo()));
		}
		return condition;
	}

	/**
	 * Applies page size, sort order, continuation token and exact-count opt-in.
	 *
	 * <p>Paging is continuation-based because Elasticsearch's result-window limit applies to
	 * {@code from + size}; offset paging would hit the same ceiling the unpaged query does.
	 */
	void applyPagination(ObjectNode queryRoot, PaginationDto pagination, Set<String> allowedFields) {
		if (pagination == null || pagination.isEmpty()) {
			return;
		}

		if (pagination.getSize() != null) {
			if (pagination.getSize() < 0) {
				throw new CustomException("INVALID_PAGINATION", "pagination.size must not be negative");
			}
			queryRoot.put(SIZE, pagination.getSize());
		}

		ArrayNode sortArray = null;
		if (pagination.hasSort()) {
			sortArray = buildSortArray(pagination.getSort(), allowedFields);
			queryRoot.set(SORT, sortArray);
		}

		if (pagination.hasSearchAfter()) {
			if (sortArray == null) {
				throw new CustomException("INVALID_PAGINATION",
						"pagination.searchAfter requires pagination.sort: a continuation token is only "
								+ "interpretable against the sort that produced it");
			}
			if (pagination.getSearchAfter().size() != sortArray.size()) {
				throw new CustomException("INVALID_PAGINATION", "pagination.searchAfter has " + pagination.getSearchAfter().size()
						+ " value(s) but the sort has " + sortArray.size()
						+ " clause(s); pass back the nextPageToken exactly as it was returned");
			}
			queryRoot.set(SEARCH_AFTER, MAPPER.valueToTree(pagination.getSearchAfter()));
		}

		if (Boolean.TRUE.equals(pagination.getTrackTotalHits())) {
			queryRoot.put(TRACK_TOTAL_HITS, true);
		}
	}

	/** The caller's sort key, followed by the tiebreaker that makes paging stable. */
	private ArrayNode buildSortArray(SortCriteria criteria, Set<String> allowedFields) {
		String field = criteria.getField().trim();
		requireAllowed(field, allowedFields, "sort");

		String order = criteria.resolvedOrder();
		ArrayNode sortArray = MAPPER.createArrayNode();
		sortArray.add(sortClause(field, order));
		if (!field.equals(TIEBREAKER_FIELD)) {
			sortArray.add(sortClause(TIEBREAKER_FIELD, order));
		}
		return sortArray;
	}

	private ObjectNode sortClause(String field, String order) {
		ObjectNode clause = MAPPER.createObjectNode();
		clause.with(field).put(ORDER, order);
		return clause;
	}
}
