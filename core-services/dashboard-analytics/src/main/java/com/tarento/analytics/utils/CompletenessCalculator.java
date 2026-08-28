package com.tarento.analytics.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.tarento.analytics.constant.Constants;
import com.tarento.analytics.dto.CompletenessDto;

/**
 * Works out whether a raw-document response actually contains every matching document, and says so.
 *
 * <p>Elasticsearch caps a result set silently: a truncated response is shaped exactly like a
 * complete one. Reading {@code hits.total} alongside the returned document count turns that into an
 * explicit signal, at no extra query cost, because the whole response already reaches this layer.
 *
 * <p><strong>Assumes the caller asked for documents.</strong> A query deliberately run with a page
 * size of zero — a request for totals alone — returns no documents by design, and measuring that
 * against the match count would report it as truncated. Only the request knows the difference, so
 * that check belongs to the caller of this class, not to this class.
 */
@Component
public class CompletenessCalculator {

	private static final Logger logger = LoggerFactory.getLogger(CompletenessCalculator.class);

	private static final String HITS = "hits";
	private static final String TOTAL = "total";
	private static final String VALUE = "value";
	private static final String RELATION = "relation";
	private static final String SORT = "sort";
	private static final String RELATION_GTE = "gte";

	/**
	 * Builds one entry per document-returning dataset in the response.
	 *
	 * @param datasetsNode      response keyed by dataset, each value a full Elasticsearch response
	 * @param chartNode         the chart configuration, used to tell document queries from aggregations
	 * @param visualizationCode chart identifier, for logging
	 * @return dataset key to completeness; empty when nothing in the response returns documents
	 */
	public Map<String, CompletenessDto> calculate(JsonNode datasetsNode, JsonNode chartNode, String visualizationCode) {
		Map<String, CompletenessDto> result = new LinkedHashMap<>();
		if (datasetsNode == null || !datasetsNode.isObject()) {
			return result;
		}

		Set<String> documentDatasets = documentReturningDatasets(chartNode);
		if (documentDatasets.isEmpty()) {
			return result;
		}

		Iterator<Map.Entry<String, JsonNode>> fields = datasetsNode.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> entry = fields.next();
			String responseKey = entry.getKey();
			if (!documentDatasets.contains(responseKey) && !documentDatasets.contains(baseKey(responseKey))) {
				continue;
			}
			CompletenessDto completeness = fromEsResponse(entry.getValue());
			if (completeness == null) {
				continue;
			}
			result.put(responseKey, completeness);
			if (Boolean.TRUE.equals(completeness.getTruncated())) {
				logger.error(
						"Incomplete dashboard result: chart '{}' dataset '{}' returned {} document(s), {}. "
								+ "The view is showing a partial, unordered sample. Narrow the filters or page through "
								+ "the result set.",
						visualizationCode, responseKey, completeness.getReturned(),
						completeness.getMatched() != null
								? "out of " + completeness.getMatched() + " that matched"
								: "and more matched than could be counted");
			}
		}
		return result;
	}

	/**
	 * Reads {@code hits} from one Elasticsearch response.
	 *
	 * @return null when the response carries no {@code hits} block — for instance because a chart
	 *         configured {@code aggregationFilterPath} filtered it out of the response.
	 */
	CompletenessDto fromEsResponse(JsonNode esResponse) {
		if (esResponse == null || !esResponse.isObject()) {
			return null;
		}
		JsonNode hits = esResponse.get(HITS);
		if (hits == null || !hits.isObject()) {
			return null;
		}

		// Elasticsearch reports {"value": n, "relation": "eq"|"gte"}; the relation is what makes a
		// capped count recognisable as one.
		JsonNode totalNode = hits.get(TOTAL);
		Long matched = null;
		String relation = null;
		if (totalNode != null && totalNode.isObject()) {
			if (totalNode.hasNonNull(VALUE)) {
				matched = totalNode.get(VALUE).asLong();
			}
			if (totalNode.hasNonNull(RELATION)) {
				relation = totalNode.get(RELATION).asText();
			}
		}

		JsonNode hitsArray = hits.get(HITS);
		Integer returned = hitsArray != null && hitsArray.isArray() ? hitsArray.size() : null;

		if (matched == null && returned == null) {
			return null;
		}

		CompletenessDto dto = new CompletenessDto();
		// Reported only when Elasticsearch says the count is exact. A capped count of 10,000 rendered
		// as "10,000 records" would be a more confident lie than saying nothing; callers that need the
		// real figure ask for it with pagination.trackTotalHits.
		dto.setMatched(RELATION_GTE.equalsIgnoreCase(relation) ? null : matched);
		dto.setReturned(returned);
		dto.setTruncated(isTruncated(matched, relation, returned));
		dto.setNextPageToken(nextPageToken(hitsArray, returned));
		return dto;
	}

	/**
	 * A capped total is reported as {@code gte}, and that is a truncation signal in its own right:
	 * when the page size equals the cap, matched and returned are both 10,000 and comparing them
	 * alone would wrongly report the result as complete.
	 */
	boolean isTruncated(Long matched, String relation, Integer returned) {
		if (RELATION_GTE.equalsIgnoreCase(relation)) {
			return true;
		}
		return matched != null && returned != null && matched > returned;
	}

	private List<Object> nextPageToken(JsonNode hitsArray, Integer returned) {
		if (hitsArray == null || !hitsArray.isArray() || returned == null || returned == 0) {
			return null;
		}
		JsonNode lastHit = hitsArray.get(returned - 1);
		if (lastHit == null) {
			return null;
		}
		JsonNode sortValues = lastHit.get(SORT);
		if (sortValues == null || !sortValues.isArray() || sortValues.isEmpty()) {
			return null;
		}
		List<Object> token = new ArrayList<>(sortValues.size());
		for (JsonNode sortValue : sortValues) {
			token.add(toJavaValue(sortValue));
		}
		return token;
	}

	private Object toJavaValue(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isNumber()) {
			return node.isIntegralNumber() ? (Object) node.asLong() : (Object) node.asDouble();
		}
		if (node.isBoolean()) {
			return node.asBoolean();
		}
		return node.asText();
	}

	/**
	 * Identifies the datasets that return documents rather than aggregations.
	 *
	 * <p>Only these can be truncated. An aggregation query runs with {@code size: 0} and returns no
	 * documents at all, so measuring it against {@code hits.total} would report every aggregation as
	 * incomplete.
	 */
	private Set<String> documentReturningDatasets(JsonNode chartNode) {
		if (chartNode == null) {
			return Collections.emptySet();
		}
		JsonNode queries = chartNode.get(Constants.JsonPaths.QUERIES);
		if (queries == null || !queries.isArray()) {
			return Collections.emptySet();
		}

		Set<String> keys = new HashSet<>();
		for (JsonNode query : queries) {
			if (query == null || !query.isObject() || !query.hasNonNull(Constants.JsonPaths.TRANSFORM_DATA)) {
				continue;
			}
			if (!Constants.JsonPaths.TRANSFORM_DATA_RAW_DOCUMENTS
					.equalsIgnoreCase(query.get(Constants.JsonPaths.TRANSFORM_DATA).asText())) {
				continue;
			}
			keys.add(datasetKey(query));
		}
		return keys;
	}

	private String datasetKey(JsonNode query) {
		if (query.hasNonNull("key")) {
			return query.get("key").asText();
		}
		if (query.hasNonNull(Constants.JsonPaths.TRANSFORM_KEY)) {
			return query.get(Constants.JsonPaths.TRANSFORM_KEY).asText();
		}
		return query.hasNonNull(Constants.JsonPaths.INDEX_NAME) ? query.get(Constants.JsonPaths.INDEX_NAME).asText() : "";
	}

	/** Mirrors the suffixing applied when two datasets resolve to the same response key. */
	private String baseKey(String responseKey) {
		return responseKey.contains("_") ? responseKey.substring(0, responseKey.lastIndexOf('_')) : responseKey;
	}
}
