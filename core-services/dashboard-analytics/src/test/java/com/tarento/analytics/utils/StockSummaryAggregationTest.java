package com.tarento.analytics.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.dto.StockSummaryRequest;

/**
 * The totals must match, transaction for transaction, what the dashboard computes in the browser
 * today. These cases are written from that arithmetic rather than from the implementation.
 */
class StockSummaryAggregationTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private StockSummaryAggregation aggregation;

	@BeforeEach
	void setUp() {
		aggregation = new StockSummaryAggregation();
	}

	// ---------------------------------------------------------------- query construction

	@Test
	@DisplayName("no aggregation is added unless the caller asks for one")
	void aggregationIsOptIn() throws Exception {
		ObjectNode query = (ObjectNode) MAPPER.readTree("{\"size\":10000}");
		String before = MAPPER.writeValueAsString(query);

		aggregation.applyAggregation(query, null);

		assertEquals(before, MAPPER.writeValueAsString(query));
	}

	@Test
	@DisplayName("both groupings are emitted, because a transaction counts against two facilities")
	void bothGroupingsEmitted() throws Exception {
		ObjectNode query = (ObjectNode) MAPPER.readTree("{\"size\":0}");

		aggregation.applyAggregation(query, new StockSummaryRequest());

		JsonNode aggs = query.get("aggs");
		assertTrue(aggs.has(StockSummaryAggregation.AGG_BY_FACILITY));
		assertTrue(aggs.has(StockSummaryAggregation.AGG_BY_TRANSACTING_FACILITY));
		assertEquals(StockSummaryAggregation.FIELD_FACILITY,
				aggs.get(StockSummaryAggregation.AGG_BY_FACILITY).get("terms").get("field").asText());
		assertEquals(StockSummaryAggregation.FIELD_TRANSACTING_FACILITY,
				aggs.get(StockSummaryAggregation.AGG_BY_TRANSACTING_FACILITY).get("terms").get("field").asText());
	}

	@Test
	@DisplayName("the caller's limits bound how many buckets are asked for")
	void limitsAreApplied() throws Exception {
		ObjectNode query = MAPPER.createObjectNode();
		StockSummaryRequest request = new StockSummaryRequest();
		request.setFacilityLimit(25);
		request.setProductLimit(7);

		aggregation.applyAggregation(query, request);

		JsonNode byFacility = query.get("aggs").get(StockSummaryAggregation.AGG_BY_FACILITY);
		assertEquals(25, byFacility.get("terms").get("size").asInt());
		assertEquals(7, byFacility.get("aggs").get("byProduct").get("terms").get("size").asInt());
	}

	@Test
	@DisplayName("limits default when unset and are rejected when nonsensical")
	void limitDefaultsAndValidation() {
		StockSummaryRequest unset = new StockSummaryRequest();
		assertEquals(250, unset.resolvedFacilityLimit());
		assertEquals(15, unset.resolvedProductLimit());

		StockSummaryRequest zero = new StockSummaryRequest();
		zero.setFacilityLimit(0);
		assertThrows(CustomException.class, zero::resolvedFacilityLimit);

		StockSummaryRequest huge = new StockSummaryRequest();
		huge.setFacilityLimit(999999);
		assertEquals(10000, huge.resolvedFacilityLimit(), "an unbounded request is clamped, not honoured");
	}

	@Test
	@DisplayName("a request whose limits would exceed the bucket budget is refused loudly")
	void bucketBudgetIsEnforced() {
		// The cluster's own bucket breaker turns into a swallowed 400 and an empty HTTP 200 upstream,
		// so the only acceptable failure mode is a refusal before the query is sent.
		ObjectNode query = MAPPER.createObjectNode();
		StockSummaryRequest request = new StockSummaryRequest();
		request.setFacilityLimit(10000);
		request.setProductLimit(10000);

		CustomException error = assertThrows(CustomException.class,
				() -> aggregation.applyAggregation(query, request));
		assertTrue(error.getMessage().contains("budget"));
		assertFalse(query.has("aggs"), "the refused request must not leave a partial aggregation behind");

		// The defaults themselves must sit inside the budget.
		aggregation.applyAggregation(MAPPER.createObjectNode(), new StockSummaryRequest());
	}

	@Test
	@DisplayName("each measure carries the filter that selects the transactions it counts")
	void measuresCarryTheirFilters() throws Exception {
		ObjectNode query = MAPPER.createObjectNode();
		aggregation.applyAggregation(query, new StockSummaryRequest());

		JsonNode measures = query.get("aggs").get(StockSummaryAggregation.AGG_BY_FACILITY)
				.get("aggs").get("byProduct").get("aggs");

		JsonNode issuedOut = measures.get("issuedOut");
		JsonNode must = issuedOut.get("filter").get("bool").get("must");
		assertEquals(3, must.size(), "entry type, status and event type");
		assertEquals("ISSUED", must.get(0).get("term").get(StockSummaryAggregation.FIELD_ENTRY_TYPE).asText());
		assertEquals(2, must.get(1).get("terms").get(StockSummaryAggregation.FIELD_STATUS).size());
		assertEquals("DISPATCHED", must.get(2).get("term").get(StockSummaryAggregation.FIELD_EVENT_TYPE).asText());
		assertEquals(StockSummaryAggregation.FIELD_QUANTITY,
				issuedOut.get("aggs").get("qty").get("sum").get("field").asText());

		// A returned transaction always has the facility field as its sender, so it is not narrowed
		// by event type the way an issue is.
		assertEquals(2, measures.get("returnedOut").get("filter").get("bool").get("must").size());
	}

	// ---------------------------------------------------------------- reading the response back

	/** Builds a response where one facility/product cell has one measure set to a quantity. */
	private JsonNode response(String grouping, String facility, String product, String measure, long qty) {
		return responseWithOther(grouping, facility, product, measure, qty, 0);
	}

	private JsonNode responseWithOther(String grouping, String facility, String product, String measure, long qty,
			int otherDocCount) {
		// assemble() keys off the facility grouping being present, so a grouping-only fixture needs an
		// empty one alongside it — but never a second copy of the grouping under test.
		String placeholder = StockSummaryAggregation.AGG_BY_FACILITY.equals(grouping)
				? ""
				: ",\"" + StockSummaryAggregation.AGG_BY_FACILITY + "\":{\"buckets\":[]}";
		try {
			return MAPPER.readTree("{\"aggregations\":{\"" + grouping + "\":{"
					+ "\"sum_other_doc_count\":" + otherDocCount + ",\"buckets\":[{\"key\":\"" + facility + "\","
					+ "\"byProduct\":{\"buckets\":[{\"key\":\"" + product + "\","
					+ "\"" + measure + "\":{\"qty\":{\"value\":" + qty + "}}}]}}]}"
					+ placeholder + "}}");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> firstRow(Map<String, Object> assembled) {
		List<Map<String, Object>> rows = (List<Map<String, Object>>) assembled.get("rows");
		return rows.isEmpty() ? null : rows.get(0);
	}

	/** Assembles with no facility scope, i.e. every facility the response mentions stays in. */
	private Map<String, Object> assembleAll(JsonNode esResponse) {
		return aggregation.assemble(esResponse, null);
	}

	@Test
	@DisplayName("products cut from a facility's bucket list are reported, not silently absent")
	void productTruncationIsReported() throws Exception {
		JsonNode truncatedProducts = MAPPER.readTree("{\"aggregations\":{\""
				+ StockSummaryAggregation.AGG_BY_FACILITY + "\":{\"sum_other_doc_count\":0,\"buckets\":[{"
				+ "\"key\":\"F1\",\"doc_count\":50,"
				+ "\"byProduct\":{\"sum_other_doc_count\":7,\"buckets\":[{\"key\":\"P1\","
				+ "\"issuedOut\":{\"doc_count\":3,\"qty\":{\"value\":40}}}]}}]}}}");

		Map<String, Object> assembled = assembleAll(truncatedProducts);

		assertEquals(Boolean.TRUE, assembled.get("productsDropped"));
		assertEquals(Boolean.FALSE, assembled.get("facilitiesDropped"));
	}

	@Test
	@DisplayName("transactions in scope but matching no counting rule raise the enrichment alarm")
	void enrichmentAbsenceIsReported() throws Exception {
		// Facility buckets carry documents, yet every rule filter matched zero of them — the shape an
		// index takes when the entry-type/status enrichment is missing from that environment.
		JsonNode noRuleMatches = MAPPER.readTree("{\"aggregations\":{\""
				+ StockSummaryAggregation.AGG_BY_FACILITY + "\":{\"sum_other_doc_count\":0,\"buckets\":[{"
				+ "\"key\":\"F1\",\"doc_count\":120,"
				+ "\"byProduct\":{\"sum_other_doc_count\":0,\"buckets\":[{\"key\":\"P1\","
				+ "\"issuedOut\":{\"doc_count\":0,\"qty\":{\"value\":0}}}]}}]}}}");

		Map<String, Object> assembled = assembleAll(noRuleMatches);

		assertEquals(Boolean.TRUE, assembled.get("noMeasuresMatched"));

		// The same response with a real match must not raise it.
		Map<String, Object> healthy = assembleAll(MAPPER.readTree("{\"aggregations\":{\""
				+ StockSummaryAggregation.AGG_BY_FACILITY + "\":{\"sum_other_doc_count\":0,\"buckets\":[{"
				+ "\"key\":\"F1\",\"doc_count\":120,"
				+ "\"byProduct\":{\"sum_other_doc_count\":0,\"buckets\":[{\"key\":\"P1\","
				+ "\"issuedOut\":{\"doc_count\":4,\"qty\":{\"value\":40}}}]}}]}}}"));
		assertFalse(healthy.containsKey("noMeasuresMatched"));
	}

	@Test
	@DisplayName("a dispatched issue counts as issued for the sending facility")
	void dispatchedIssueCountsAsIssuedForSender() throws Exception {
		Map<String, Object> row = firstRow(assembleAll(
				response(StockSummaryAggregation.AGG_BY_FACILITY, "F1", "P1", "issuedOut", 40)));

		assertEquals("F1", row.get("facilityId"));
		assertEquals("P1", row.get("productVariantId"));
		assertEquals(40L, row.get("totalIssued"));
		assertEquals(0L, row.get("totalReceived"));
	}

	@Test
	@DisplayName("an accepted issue counts as received AND accepted for the receiving facility")
	void acceptedIssueCountsForReceiver() throws Exception {
		Map<String, Object> row = firstRow(assembleAll(response(
				StockSummaryAggregation.AGG_BY_TRANSACTING_FACILITY, "F2", "P1", "receivedCounterparty", 30)));

		assertEquals(30L, row.get("totalReceived"));
		assertEquals(30L, row.get("totalAccepted"),
				"the dashboard credits an accepted issue to both totals, and so must this");
	}

	@Test
	@DisplayName("a rejected issue counts against both sides, as it does in the dashboard")
	void rejectedIssueCountsOnBothSides() throws Exception {
		Map<String, Object> sender = firstRow(assembleAll(
				response(StockSummaryAggregation.AGG_BY_FACILITY, "F1", "P1", "rejectedOut", 5)));
		Map<String, Object> receiver = firstRow(assembleAll(response(
				StockSummaryAggregation.AGG_BY_TRANSACTING_FACILITY, "F2", "P1", "rejectedCounterparty", 5)));

		assertEquals(5L, sender.get("totalRejected"));
		assertEquals(5L, receiver.get("totalRejected"));
	}

	@Test
	@DisplayName("a return counts as returned for the returning facility")
	void returnCountsAsReturnedForSender() throws Exception {
		Map<String, Object> row = firstRow(assembleAll(
				response(StockSummaryAggregation.AGG_BY_FACILITY, "F2", "P1", "returnedOut", 12)));

		assertEquals(12L, row.get("totalReturned"));
	}

	@Test
	@DisplayName("an accepted return counts as received for the facility getting stock back")
	void acceptedReturnCountsAsReceived() throws Exception {
		Map<String, Object> row = firstRow(assembleAll(response(
				StockSummaryAggregation.AGG_BY_TRANSACTING_FACILITY, "F1", "P1", "returnedReceivedCounterparty", 12)));

		assertEquals(12L, row.get("totalReceived"));
		assertEquals(0L, row.get("totalAccepted"),
				"an accepted return is received stock but is not an acceptance in the issue sense");
	}

	@Test
	@DisplayName("an issue recorded from the receiving end reverses which field is the sender")
	void receivedEventReversesTheRoles() throws Exception {
		// facility field is the RECEIVER here
		Map<String, Object> receiverSide = firstRow(assembleAll(
				response(StockSummaryAggregation.AGG_BY_FACILITY, "F9", "P1", "receivedIn", 8)));
		assertEquals(8L, receiverSide.get("totalReceived"));

		// counterparty field is the SENDER here
		Map<String, Object> senderSide = firstRow(assembleAll(response(
				StockSummaryAggregation.AGG_BY_TRANSACTING_FACILITY, "F8", "P1", "issuedCounterparty", 8)));
		assertEquals(8L, senderSide.get("totalIssued"));
	}

	@Test
	@DisplayName("balance is accepted minus issued minus returned")
	void balanceFollowsTheDashboardFormula() throws Exception {
		JsonNode merged = MAPPER.readTree("{\"aggregations\":{\""
				+ StockSummaryAggregation.AGG_BY_FACILITY + "\":{\"buckets\":[{\"key\":\"F1\",\"byProduct\":{"
				+ "\"buckets\":[{\"key\":\"P1\","
				+ "\"receivedIn\":{\"qty\":{\"value\":100}},"
				+ "\"issuedOut\":{\"qty\":{\"value\":30}},"
				+ "\"returnedOut\":{\"qty\":{\"value\":20}}}]}}]}}}");

		Map<String, Object> row = firstRow(assembleAll(merged));

		assertEquals(100L, row.get("totalAccepted"));
		assertEquals(30L, row.get("totalIssued"));
		assertEquals(20L, row.get("totalReturned"));
		assertEquals(50L, row.get("balance"));
	}

	@Test
	@DisplayName("totals for the same facility and product are merged across both groupings")
	void bothGroupingsMergeIntoOneRow() throws Exception {
		JsonNode both = MAPPER.readTree("{\"aggregations\":{"
				+ "\"" + StockSummaryAggregation.AGG_BY_FACILITY + "\":{\"buckets\":[{\"key\":\"F1\",\"byProduct\":{"
				+ "\"buckets\":[{\"key\":\"P1\",\"issuedOut\":{\"qty\":{\"value\":40}}}]}}]},"
				+ "\"" + StockSummaryAggregation.AGG_BY_TRANSACTING_FACILITY + "\":{\"buckets\":[{\"key\":\"F1\","
				+ "\"byProduct\":{\"buckets\":[{\"key\":\"P1\","
				+ "\"receivedCounterparty\":{\"qty\":{\"value\":15}}}]}}]}}}");

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> rows = (List<Map<String, Object>>) assembleAll(both).get("rows");

		assertEquals(1, rows.size(), "a facility that both sends and receives is one row, not two");
		assertEquals(40L, rows.get(0).get("totalIssued"));
		assertEquals(15L, rows.get(0).get("totalReceived"));
	}

	@Test
	@DisplayName("entry types the dashboard ignores are ignored here too")
	void ignoredEntryTypesAreNeverCounted() throws Exception {
		// Receipts and over/under adjustments duplicate an issue, so counting them would double up.
		// They match no rule, so a response containing only them yields no totals at all.
		JsonNode onlyIgnored = MAPPER.readTree("{\"aggregations\":{\""
				+ StockSummaryAggregation.AGG_BY_FACILITY + "\":{\"buckets\":[{\"key\":\"F1\",\"byProduct\":{"
				+ "\"buckets\":[{\"key\":\"P1\",\"receiptSomething\":{\"qty\":{\"value\":999}}}]}}]}}}");

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> rows = (List<Map<String, Object>>) assembleAll(onlyIgnored).get("rows");

		assertTrue(rows.isEmpty());
	}

	@Test
	@DisplayName("dropped facility buckets are reported rather than passed off as the whole picture")
	void droppedFacilitiesAreReported() throws Exception {
		Map<String, Object> complete = assembleAll(
				response(StockSummaryAggregation.AGG_BY_FACILITY, "F1", "P1", "issuedOut", 1));
		assertFalse((Boolean) complete.get("facilitiesDropped"));

		Map<String, Object> truncated = assembleAll(responseWithOther(
				StockSummaryAggregation.AGG_BY_FACILITY, "F1", "P1", "issuedOut", 1, 42));
		assertTrue((Boolean) truncated.get("facilitiesDropped"),
				"hitting the facility limit must be visible, not silent");
	}

	@Test
	@DisplayName("a response without this aggregation yields nothing rather than an empty summary")
	void absentAggregationYieldsNull() throws Exception {
		assertNull(assembleAll(MAPPER.readTree("{\"hits\":{\"total\":{\"value\":1}}}")));
		assertNull(assembleAll(MAPPER.readTree("{\"aggregations\":{\"somethingElse\":{}}}")));
		assertNull(assembleAll(null));
	}
}
