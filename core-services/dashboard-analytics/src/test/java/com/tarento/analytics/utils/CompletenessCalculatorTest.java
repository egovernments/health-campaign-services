package com.tarento.analytics.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarento.analytics.dto.CompletenessDto;

class CompletenessCalculatorTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** A chart whose single query returns documents, as the commodity dashboard's does. */
	private static final String DOCUMENT_CHART =
			"{\"queries\":[{\"indexName\":\"stock-index-v1\",\"transformKey\":\"stockBalanceTransformer\","
					+ "\"transformData\":\"rawDocuments\"}]}";

	/** A chart whose query aggregates instead, so it returns no documents to be truncated. */
	private static final String AGGREGATION_CHART =
			"{\"queries\":[{\"indexName\":\"stock-index-v1\",\"transformKey\":\"stockBalanceTransformer\","
					+ "\"transformData\":\"linearAggregation\"}]}";

	private CompletenessCalculator calculator;

	@BeforeEach
	void setUp() {
		calculator = new CompletenessCalculator();
	}

	private JsonNode esResponse(String totalJson, int hitCount) {
		StringBuilder hits = new StringBuilder();
		for (int i = 0; i < hitCount; i++) {
			if (i > 0) {
				hits.append(',');
			}
			hits.append("{\"_source\":{\"Data\":{\"id\":\"stock-").append(i).append("\"}}}");
		}
		try {
			return MAPPER.readTree("{\"hits\":{\"total\":" + totalJson + ",\"hits\":[" + hits + "]}}");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private JsonNode datasets(JsonNode response) {
		return MAPPER.createObjectNode().set("stockBalanceTransformer", response);
	}

	// ---------------------------------------------------------------- truncation detection

	@Test
	@DisplayName("a dataset with no ES response at all is reported as queryFailed, never as empty")
	void missingResponseIsQueryFailedNotEmpty() throws Exception {
		// The shape the HTTP layer produces when Elasticsearch rejects the query: the dataset key is
		// present but its value is a NullNode. Rendering that as an empty dataset is the silent
		// failure this whole feature exists to remove.
		JsonNode failed = MAPPER.createObjectNode().set("stockBalanceTransformer", MAPPER.nullNode());

		Map<String, CompletenessDto> result =
				calculator.calculate(failed, MAPPER.readTree(DOCUMENT_CHART), "commodityFacilityStockBalance");

		assertTrue(result.get("stockBalanceTransformer").getQueryFailed());
		assertNull(result.get("stockBalanceTransformer").getTruncated(), "nothing else is meaningful");
	}

	@Test
	@DisplayName("failure detection runs even for a totals-only request; truncation reporting does not")
	void failureDetectionIgnoresDocumentsRequestedFlag() throws Exception {
		JsonNode failed = MAPPER.createObjectNode().set("stockBalanceTransformer", MAPPER.nullNode());
		Map<String, CompletenessDto> failedResult =
				calculator.calculate(failed, MAPPER.readTree(DOCUMENT_CHART), "c", false);
		assertTrue(failedResult.get("stockBalanceTransformer").getQueryFailed());

		// A healthy response with documentsRequested=false stays unreported (zero-size page is not truncation).
		Map<String, CompletenessDto> healthy = calculator.calculate(
				datasets(esResponse("{\"value\":500,\"relation\":\"eq\"}", 0)),
				MAPPER.readTree(DOCUMENT_CHART), "c", false);
		assertTrue(healthy.isEmpty());
	}

	@Test
	@DisplayName("an object response without hits stays unreported — filtered, not failed")
	void objectWithoutHitsIsNotQueryFailed() throws Exception {
		// e.g. an aggregationFilterPath stripped hits from the response: unusable for completeness,
		// but not the failed-query shape; no confident wrong claim either way.
		JsonNode noHits = MAPPER.createObjectNode().set("stockBalanceTransformer", MAPPER.createObjectNode());

		Map<String, CompletenessDto> result =
				calculator.calculate(noHits, MAPPER.readTree(DOCUMENT_CHART), "c");

		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("more matches than returned is reported as truncated")
	void moreMatchesThanReturnedIsTruncated() throws Exception {
		CompletenessDto dto = calculator.fromEsResponse(esResponse("{\"value\":25000,\"relation\":\"eq\"}", 3));

		assertEquals(25000L, dto.getMatched());
		assertEquals(3, dto.getReturned());
		assertTrue(dto.getTruncated());
	}

	@Test
	@DisplayName("a capped total is truncated even when matched equals returned")
	void cappedTotalAtPageSizeIsTruncated() throws Exception {
		// The exact production shape: size 10000, at least 10000 matches, 10000 returned.
		// Comparing matched to returned alone would call this complete, which is the bug.
		CompletenessDto dto = calculator.fromEsResponse(esResponse("{\"value\":10000,\"relation\":\"gte\"}", 10000));

		assertNull(dto.getMatched(), "a capped count must not be reported as if it were exact");
		assertEquals(10000, dto.getReturned());
		assertTrue(dto.getTruncated(), "a 'gte' relation means the count was capped, so documents were dropped");
	}

	@Test
	@DisplayName("a fully returned result is not truncated")
	void completeResultIsNotTruncated() throws Exception {
		CompletenessDto dto = calculator.fromEsResponse(esResponse("{\"value\":3,\"relation\":\"eq\"}", 3));

		assertFalse(dto.getTruncated());
		assertEquals(3, dto.getReturned());
	}

	@Test
	@DisplayName("an empty but complete result is not truncated")
	void emptyResultIsNotTruncated() throws Exception {
		CompletenessDto dto = calculator.fromEsResponse(esResponse("{\"value\":0,\"relation\":\"eq\"}", 0));

		assertEquals(0L, dto.getMatched());
		assertEquals(0, dto.getReturned());
		assertFalse(dto.getTruncated(),
				"an empty tab must be reported as genuinely empty, not as a truncation");
	}

	@Test
	@DisplayName("a response shape this cluster never produces yields no completeness")
	void unrecognisedTotalShapeYieldsNull() throws Exception {
		CompletenessDto dto = calculator.fromEsResponse(esResponse("42", 10));

		assertNull(dto.getMatched(), "a total shape this cluster never emits is not guessed at");
		assertEquals(10, dto.getReturned());
		assertFalse(dto.getTruncated(), "an unknown total cannot be called truncated");
	}

	// ---------------------------------------------------------------- degradation

	@Test
	@DisplayName("a response with no hits block yields no completeness rather than a failure")
	void missingHitsYieldsNull() throws Exception {
		assertNull(calculator.fromEsResponse(MAPPER.readTree("{\"aggregations\":{}}")),
				"a chart that filters hits out of its response should degrade quietly");
		assertNull(calculator.fromEsResponse(null));
		assertNull(calculator.fromEsResponse(MAPPER.readTree("[]")));
	}

	@Test
	@DisplayName("a hits block with neither total nor documents yields no completeness")
	void emptyHitsBlockYieldsNull() throws Exception {
		assertNull(calculator.fromEsResponse(MAPPER.readTree("{\"hits\":{}}")));
	}

	// ---------------------------------------------------------------- continuation token

	@Test
	@DisplayName("the last document's sort values become the next page token")
	void nextPageTokenComesFromLastHit() throws Exception {
		JsonNode response = MAPPER.readTree("{\"hits\":{\"total\":{\"value\":500,\"relation\":\"eq\"},\"hits\":["
				+ "{\"_source\":{},\"sort\":[1000,\"stock-a\"]},"
				+ "{\"_source\":{},\"sort\":[2000,\"stock-b\"]}]}}");

		CompletenessDto dto = calculator.fromEsResponse(response);

		assertEquals(2, dto.getNextPageToken().size());
		assertEquals(2000L, dto.getNextPageToken().get(0));
		assertEquals("stock-b", dto.getNextPageToken().get(1));
	}

	@Test
	@DisplayName("an unsorted query produces no continuation token")
	void unsortedQueryHasNoToken() throws Exception {
		CompletenessDto dto = calculator.fromEsResponse(esResponse("{\"value\":5,\"relation\":\"eq\"}", 5));

		assertNull(dto.getNextPageToken(), "without a sort there is no stable position to resume from");
	}

	@Test
	@DisplayName("an empty page produces no continuation token")
	void emptyPageHasNoToken() throws Exception {
		CompletenessDto dto = calculator.fromEsResponse(esResponse("{\"value\":0,\"relation\":\"eq\"}", 0));

		assertNull(dto.getNextPageToken());
	}

	// ---------------------------------------------------------------- dataset selection

	@Test
	@DisplayName("document-returning datasets are measured")
	void documentDatasetIsMeasured() throws Exception {
		Map<String, CompletenessDto> result = calculator.calculate(
				datasets(esResponse("{\"value\":25000,\"relation\":\"gte\"}", 10000)),
				MAPPER.readTree(DOCUMENT_CHART), "commodityFacilityStockBalance");

		assertEquals(1, result.size());
		assertTrue(result.get("stockBalanceTransformer").getTruncated());
	}

	@Test
	@DisplayName("aggregation datasets are skipped, because size 0 would look like total truncation")
	void aggregationDatasetIsSkipped() throws Exception {
		Map<String, CompletenessDto> result = calculator.calculate(
				datasets(esResponse("{\"value\":25000,\"relation\":\"eq\"}", 0)),
				MAPPER.readTree(AGGREGATION_CHART), "someAggregateChart");

		assertTrue(result.isEmpty(),
				"an aggregation covers every matching document even though it returns none of them");
	}

	@Test
	@DisplayName("a query that declares no transform type is not measured")
	void undeclaredTransformDataIsSkipped() throws Exception {
		Map<String, CompletenessDto> result = calculator.calculate(
				datasets(esResponse("{\"value\":25000,\"relation\":\"eq\"}", 0)),
				MAPPER.readTree("{\"queries\":[{\"indexName\":\"stock-index-v1\"}]}"), "legacyChart");

		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("a suffixed response key still resolves to its dataset configuration")
	void suffixedResponseKeyResolves() throws Exception {
		JsonNode datasets = MAPPER.createObjectNode()
				.set("stockBalanceTransformer_1", esResponse("{\"value\":900,\"relation\":\"eq\"}", 100));

		Map<String, CompletenessDto> result =
				calculator.calculate(datasets, MAPPER.readTree(DOCUMENT_CHART), "commodityFacilityStockBalance");

		assertEquals(1, result.size());
		assertTrue(result.get("stockBalanceTransformer_1").getTruncated());
	}

	@Test
	@DisplayName("missing or malformed input produces an empty result rather than an error")
	void malformedInputIsSafe() throws Exception {
		assertTrue(calculator.calculate(null, MAPPER.readTree(DOCUMENT_CHART), "c").isEmpty());
		assertTrue(calculator.calculate(MAPPER.readTree("{}"), null, "c").isEmpty());
		assertTrue(calculator.calculate(MAPPER.readTree("[]"), MAPPER.readTree(DOCUMENT_CHART), "c").isEmpty());
		assertTrue(calculator.calculate(MAPPER.readTree("{}"), MAPPER.readTree("{}"), "c").isEmpty());
	}

	// ---------------------------------------------------------------- truncation rule

	@Test
	@DisplayName("the truncation rule holds across the cases that matter")
	void truncationRule() {
		assertTrue(calculator.isTruncated(10000L, "gte", 10000), "capped count at full page");
		assertTrue(calculator.isTruncated(25000L, "eq", 100), "exact count above page size");
		assertFalse(calculator.isTruncated(100L, "eq", 100), "exact count equal to what was returned");
		assertFalse(calculator.isTruncated(0L, "eq", 0), "genuinely empty");
		assertFalse(calculator.isTruncated(null, null, 5), "unknown total cannot be called truncated");
	}
}
