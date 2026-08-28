package com.tarento.analytics.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.dto.AggregateDto;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.CompletenessDto;
import com.tarento.analytics.dto.PaginationDto;
import com.tarento.analytics.utils.CompletenessCalculator;
import com.tarento.analytics.utils.StockSummaryAggregation;
import com.tarento.analytics.utils.RawResponseTransformer;

/**
 * Exercises the handler with the real transformer and calculator, so the assertions cover the
 * behaviour the dashboard actually receives rather than mock interactions.
 */
class RawResponseHandlerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Mirrors the shape of the deployed commodity chart configuration. */
	private static final String COMMODITY_CHART = "{"
			+ "\"chartType\":\"rawResponse\","
			+ "\"queries\":[{\"indexName\":\"stock-index-v1\",\"transformKey\":\"stockBalanceTransformer\","
			+ "\"transformData\":\"rawDocuments\"}],"
			+ "\"transform\":{\"stockBalanceTransformer\":{\"transformationMappings\":[{"
			+ "\"facilityId\":\"Data.facilityId\",\"quantity\":\"Data.physicalCount\","
			+ "\"stockEntryType\":\"Data.additionalDetails.stockEntryType\"}]}},"
			+ "\"aggregationPaths\":[\"stockBalanceTransformer\"]}";

	private RawResponseHandler handler;

	@BeforeEach
	void setUp() {
		handler = new RawResponseHandler();
		ReflectionTestUtils.setField(handler, "rawResponseTransformer", new RawResponseTransformer());
		ReflectionTestUtils.setField(handler, "completenessCalculator", new CompletenessCalculator());
		ReflectionTestUtils.setField(handler, "stockSummaryAggregation", new StockSummaryAggregation());
	}

	private ObjectNode aggregationsWrapper(String esResponseJson) throws Exception {
		ObjectNode datasets = MAPPER.createObjectNode();
		datasets.set("stockBalanceTransformer", MAPPER.readTree(esResponseJson));
		ObjectNode wrapper = MAPPER.createObjectNode();
		wrapper.set(IResponseHandler.AGGREGATIONS, datasets);
		return wrapper;
	}

	private AggregateRequestDto request() throws Exception {
		AggregateRequestDto request = new AggregateRequestDto();
		request.setVisualizationCode("commodityFacilityStockBalance");
		request.setChartNode((ObjectNode) MAPPER.readTree(COMMODITY_CHART));
		return request;
	}

	private static String twoStockDocuments() {
		return "\"hits\":["
				+ "{\"_source\":{\"Data\":{\"facilityId\":\"F1\",\"physicalCount\":10,"
				+ "\"additionalDetails\":{\"stockEntryType\":\"RETURNED\"}}},\"sort\":[1000,\"s-1\"]},"
				+ "{\"_source\":{\"Data\":{\"facilityId\":\"F2\",\"physicalCount\":20,"
				+ "\"additionalDetails\":{\"stockEntryType\":\"ISSUED\"}}},\"sort\":[2000,\"s-2\"]}]";
	}

	@Test
	@DisplayName("documents are still transformed exactly as before")
	@SuppressWarnings("unchecked")
	void documentsAreStillTransformed() throws Exception {
		AggregateDto dto = handler.translate(request(), aggregationsWrapper(
				"{\"hits\":{\"total\":{\"value\":2,\"relation\":\"eq\"}," + twoStockDocuments() + "}}"));

		Map<String, Object> rawResponse = (Map<String, Object>) dto.getCustomData().get("rawResponse");
		List<Map<String, Object>> rows = (List<Map<String, Object>>) rawResponse.get("stockBalanceTransformer");

		assertEquals(2, rows.size());
		assertEquals("F1", rows.get(0).get("facilityId"));
		assertEquals(10, ((Number) rows.get(0).get("quantity")).intValue());
		assertEquals("RETURNED", rows.get(0).get("stockEntryType"));
		assertEquals("F2", rows.get(1).get("facilityId"));
	}

	@Test
	@DisplayName("a complete result reports itself as complete")
	@SuppressWarnings("unchecked")
	void completeResultIsReported() throws Exception {
		AggregateDto dto = handler.translate(request(), aggregationsWrapper(
				"{\"hits\":{\"total\":{\"value\":2,\"relation\":\"eq\"}," + twoStockDocuments() + "}}"));

		Map<String, CompletenessDto> completeness =
				(Map<String, CompletenessDto>) dto.getCustomData().get("completeness");
		CompletenessDto stock = completeness.get("stockBalanceTransformer");

		assertFalse(stock.getTruncated());
		assertEquals(2L, stock.getMatched());
		assertEquals(2, stock.getReturned());
	}

	@Test
	@DisplayName("a capped result is flagged as incomplete and carries a resume token")
	@SuppressWarnings("unchecked")
	void cappedResultIsFlagged() throws Exception {
		AggregateDto dto = handler.translate(request(), aggregationsWrapper(
				"{\"hits\":{\"total\":{\"value\":10000,\"relation\":\"gte\"}," + twoStockDocuments() + "}}"));

		Map<String, CompletenessDto> completeness =
				(Map<String, CompletenessDto>) dto.getCustomData().get("completeness");
		CompletenessDto stock = completeness.get("stockBalanceTransformer");

		assertTrue(stock.getTruncated(), "this is the failure the dashboard could not previously report");
		assertNull(stock.getMatched(), "a capped count is withheld rather than reported as exact");
		assertEquals(Arrays.asList(2000L, "s-2"), stock.getNextPageToken());
	}

	@Test
	@DisplayName("a totals-only request is not reported as a truncated document list")
	@SuppressWarnings("unchecked")
	void totalsOnlyRequestIsNotCalledTruncated() throws Exception {
		// Asking for zero documents returns zero hits by design. Measuring that against the match
		// count would flag every totals request as incomplete, which is a false alarm.
		AggregateRequestDto request = request();
		PaginationDto pagination = new PaginationDto();
		pagination.setSize(0);
		request.setPagination(pagination);

		AggregateDto dto = handler.translate(request, aggregationsWrapper(
				"{\"hits\":{\"total\":{\"value\":25000,\"relation\":\"eq\"},\"hits\":[]},"
						+ "\"aggregations\":{\"stockSummaryByFacility\":{\"buckets\":[{\"key\":\"F1\","
						+ "\"byProduct\":{\"buckets\":[{\"key\":\"P1\","
						+ "\"issuedOut\":{\"qty\":{\"value\":40}}}]}}]}}}"));

		assertFalse(dto.getCustomData().containsKey("completeness"),
				"zero documents were requested, so nothing about documents was truncated");

		Map<String, Object> summaries = (Map<String, Object>) dto.getCustomData().get("stockSummary");
		Map<String, Object> summary = (Map<String, Object>) summaries.get("stockBalanceTransformer");
		List<Map<String, Object>> rows = (List<Map<String, Object>>) summary.get("rows");
		assertEquals(1, rows.size(), "the totals the caller actually asked for are still returned");
		assertEquals(40L, rows.get(0).get("totalIssued"));
	}

	@Test
	@DisplayName("a page of documents is still measured for completeness")
	void explicitPageIsStillMeasured() throws Exception {
		AggregateRequestDto request = request();
		PaginationDto pagination = new PaginationDto();
		pagination.setSize(2);
		request.setPagination(pagination);

		AggregateDto dto = handler.translate(request, aggregationsWrapper(
				"{\"hits\":{\"total\":{\"value\":25000,\"relation\":\"eq\"}," + twoStockDocuments() + "}}"));

		assertTrue(dto.getCustomData().containsKey("completeness"));
	}

	@Test
	@DisplayName("completeness is omitted rather than guessed when the response carries no hits")
	void completenessOmittedWhenHitsAbsent() throws Exception {
		AggregateDto dto = handler.translate(request(), aggregationsWrapper("{\"aggregations\":{}}"));

		assertFalse(dto.getCustomData().containsKey("completeness"));
		assertNotNull(dto.getCustomData().get("rawResponse"), "the existing payload must still be produced");
	}

	@Test
	@DisplayName("the response keeps its existing shape, with completeness added alongside")
	void responseShapeIsAdditive() throws Exception {
		AggregateDto dto = handler.translate(request(), aggregationsWrapper(
				"{\"hits\":{\"total\":{\"value\":2,\"relation\":\"eq\"}," + twoStockDocuments() + "}}"));

		assertTrue(dto.getCustomData().containsKey("rawResponse"));
		assertTrue(dto.getCustomData().containsKey("completeness"));
		assertEquals("commodityFacilityStockBalance", dto.getVisualizationCode());
	}

	@Test
	@DisplayName("an aggregation chart is untouched: no completeness, transformation unchanged")
	@SuppressWarnings("unchecked")
	void aggregationChartUnaffected() throws Exception {
		String aggregationChart = "{\"chartType\":\"rawResponse\","
				+ "\"queries\":[{\"indexName\":\"stock-index-v1\",\"transformKey\":\"stockBalanceTransformer\","
				+ "\"transformData\":\"linearAggregation\"}],"
				+ "\"transform\":{\"stockBalanceTransformer\":{\"transformationMappings\":[{"
				+ "\"total\":\"aggregations.total.value\"}]}}}";

		AggregateRequestDto request = new AggregateRequestDto();
		request.setVisualizationCode("someAggregateChart");
		request.setChartNode((ObjectNode) MAPPER.readTree(aggregationChart));

		AggregateDto dto = handler.translate(request, aggregationsWrapper(
				"{\"hits\":{\"total\":{\"value\":25000,\"relation\":\"eq\"},\"hits\":[]},"
						+ "\"aggregations\":{\"total\":{\"value\":25000}}}"));

		assertFalse(dto.getCustomData().containsKey("completeness"),
				"an aggregation reads every matching document even though it returns none");
		Map<String, Object> rawResponse = (Map<String, Object>) dto.getCustomData().get("rawResponse");
		assertNotNull(rawResponse);
	}

	@Test
	@DisplayName("an empty result set is reported as genuinely empty, not as truncated")
	@SuppressWarnings("unchecked")
	void emptyResultIsNotFlagged() throws Exception {
		AggregateDto dto = handler.translate(request(), aggregationsWrapper(
				"{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"hits\":[]}}"));

		Map<String, CompletenessDto> completeness =
				(Map<String, CompletenessDto>) dto.getCustomData().get("completeness");

		assertFalse(completeness.get("stockBalanceTransformer").getTruncated());
		assertEquals(0, completeness.get("stockBalanceTransformer").getReturned());
	}

	@Test
	@DisplayName("completeness serialises without null padding")
	void completenessSerialisesCleanly() throws Exception {
		AggregateDto dto = handler.translate(request(), aggregationsWrapper(
				"{\"hits\":{\"total\":{\"value\":2,\"relation\":\"eq\"}," + twoStockDocuments() + "}}"));

		JsonNode serialised = MAPPER.valueToTree(dto.getCustomData().get("completeness"));
		JsonNode stock = serialised.get("stockBalanceTransformer");

		assertTrue(stock.has("matched"));
		assertTrue(stock.has("truncated"));
		assertTrue(stock.has("returned"));
	}
}
