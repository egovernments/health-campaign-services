package com.tarento.analytics.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.dto.FacilityScope;

/**
 * The walk must reproduce the dashboard's row set exactly, including the two behaviours that look
 * like oversights but are what is on screen today.
 */
class FacilityScopeResolverTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private FacilityScopeResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new FacilityScopeResolver();
	}

	/** STATE issued to HF1, HF1 issued to HF2, and UPSTREAM issued to STATE. */
	private JsonNode chain() throws Exception {
		return MAPPER.readTree("{\"aggregations\":{\"" + FacilityScopeResolver.AGG_EDGES_DISPATCHED + "\":{"
				+ "\"source\":{\"buckets\":["
				+ "{\"key\":\"STATE\",\"target\":{\"buckets\":[{\"key\":\"HF1\"}]}},"
				+ "{\"key\":\"HF1\",\"target\":{\"buckets\":[{\"key\":\"HF2\"}]}},"
				+ "{\"key\":\"UPSTREAM\",\"target\":{\"buckets\":[{\"key\":\"STATE\"}]}}"
				+ "]}}}}");
	}

	private FacilityScope rootedAtState() {
		FacilityScope scope = new FacilityScope();
		scope.setRootFacilityIds(Collections.singletonList("STATE"));
		return scope;
	}

	@Test
	@DisplayName("the walk reaches facilities issued to, directly and indirectly")
	void walkReachesDownstream() throws Exception {
		FacilityScopeResolver.Scope resolved = resolver.resolve(chain(), rootedAtState());

		assertTrue(resolved.getFacilityIds().contains("HF1"), "issued to directly");
		assertTrue(resolved.getFacilityIds().contains("HF2"), "issued to via HF1");
		assertEquals(FacilityScopeResolver.Rule.SHIPMENT_DERIVED, resolved.getRule());
	}

	@Test
	@DisplayName("the manager's own facility is absent from its own summary, as it is today")
	void rootsAreExcludedByDefault() throws Exception {
		FacilityScopeResolver.Scope resolved = resolver.resolve(chain(), rootedAtState());

		assertFalse(resolved.getFacilityIds().contains("STATE"),
				"reproducing today's rows; including it would change what is on screen");
	}

	@Test
	@DisplayName("a facility that issued TO the manager is never reached")
	void upstreamIsUnreachable() throws Exception {
		FacilityScopeResolver.Scope resolved = resolver.resolve(chain(), rootedAtState());

		assertFalse(resolved.getFacilityIds().contains("UPSTREAM"),
				"the links only run outward, so upstream facilities cannot appear");
	}

	@Test
	@DisplayName("roots can be included deliberately")
	void rootsCanBeIncluded() throws Exception {
		FacilityScope scope = rootedAtState();
		scope.setIncludeRoots(true);

		FacilityScopeResolver.Scope resolved = resolver.resolve(chain(), scope);

		assertTrue(resolved.getFacilityIds().contains("STATE"));
		assertTrue(resolved.getFacilityIds().contains("HF2"), "the walk still runs");
	}

	@Test
	@DisplayName("without starting facilities a different rule applies, and it says so")
	void fallbackIsReported() throws Exception {
		FacilityScopeResolver.Scope resolved = resolver.resolve(chain(), new FacilityScope());

		assertEquals(FacilityScopeResolver.Rule.ALL_RECEIVERS_FALLBACK, resolved.getRule(),
				"the dashboard makes this substitution silently; here it is named");
		assertTrue(resolved.getFacilityIds().contains("STATE"),
				"the fallback includes every facility that received anything, which is a different set");
		assertTrue(resolved.getFacilityIds().contains("HF1"));
	}

	@Test
	@DisplayName("a cycle back to the start does not resurrect the starting facility")
	void cycleDoesNotIncludeRoot() throws Exception {
		JsonNode cyclic = MAPPER.readTree("{\"aggregations\":{\"" + FacilityScopeResolver.AGG_EDGES_DISPATCHED + "\":{"
				+ "\"source\":{\"buckets\":["
				+ "{\"key\":\"STATE\",\"target\":{\"buckets\":[{\"key\":\"HF1\"}]}},"
				+ "{\"key\":\"HF1\",\"target\":{\"buckets\":[{\"key\":\"STATE\"}]}}"
				+ "]}}}}");

		FacilityScopeResolver.Scope resolved = resolver.resolve(cyclic, rootedAtState());

		assertFalse(resolved.getFacilityIds().contains("STATE"), "roots are visited from the outset");
		assertTrue(resolved.getFacilityIds().contains("HF1"));
	}

	@Test
	@DisplayName("links recorded from the receiving end are followed too")
	void receivedEventLinksAreFollowed() throws Exception {
		// An issue logged as RECEIVED reverses which field holds the sender, so the link runs the
		// other way round in the index and would be missed if only one grouping were read.
		JsonNode received = MAPPER.readTree("{\"aggregations\":{"
				+ "\"" + FacilityScopeResolver.AGG_EDGES_DISPATCHED + "\":{\"source\":{\"buckets\":[]}},"
				+ "\"" + FacilityScopeResolver.AGG_EDGES_RECEIVED + "\":{\"source\":{\"buckets\":["
				+ "{\"key\":\"STATE\",\"target\":{\"buckets\":[{\"key\":\"HF7\"}]}}]}}}}");

		FacilityScopeResolver.Scope resolved = resolver.resolve(received, rootedAtState());

		assertTrue(resolved.getFacilityIds().contains("HF7"));
	}

	@Test
	@DisplayName("no scope requested means no scoping, and no link aggregation is asked for")
	void scopeIsOptIn() throws Exception {
		ObjectNode query = MAPPER.createObjectNode();
		resolver.applyEdgeAggregation(query, null, 100);
		assertFalse(query.has("aggs"), "a caller that did not ask for scoping pays nothing for it");

		assertNull(resolver.resolve(chain(), null));
		assertNull(resolver.resolve(MAPPER.readTree("{\"aggregations\":{}}"), rootedAtState()),
				"nothing to walk means no claim about which facilities belong");
	}

	@Test
	@DisplayName("the link query reads facility pairs, not documents, so it cannot grow with volume")
	void linkQueryIsBoundedByFacilities() throws Exception {
		ObjectNode query = MAPPER.createObjectNode();

		resolver.applyEdgeAggregation(query, rootedAtState(), 250);

		JsonNode dispatched = query.get("aggs").get(FacilityScopeResolver.AGG_EDGES_DISPATCHED);
		assertEquals(250, dispatched.get("aggs").get("source").get("terms").get("size").asInt());
		assertEquals(StockSummaryAggregation.FIELD_FACILITY,
				dispatched.get("aggs").get("source").get("terms").get("field").asText());
		assertEquals(StockSummaryAggregation.FIELD_TRANSACTING_FACILITY,
				dispatched.get("aggs").get("source").get("aggs").get("target").get("terms").get("field").asText());
		// Only issued transactions define a link, matching the dashboard.
		assertEquals("ISSUED", dispatched.get("filter").get("bool").get("must").get(0)
				.get("term").get(StockSummaryAggregation.FIELD_ENTRY_TYPE).asText());
	}

	@Test
	@DisplayName("scoping the totals drops facilities outside the walk")
	void totalsAreFilteredToTheScope() throws Exception {
		JsonNode response = MAPPER.readTree("{\"aggregations\":{"
				+ "\"" + StockSummaryAggregation.AGG_BY_FACILITY + "\":{\"buckets\":["
				+ "{\"key\":\"HF1\",\"byProduct\":{\"buckets\":[{\"key\":\"P1\","
				+ "\"issuedOut\":{\"qty\":{\"value\":10}}}]}},"
				+ "{\"key\":\"UPSTREAM\",\"byProduct\":{\"buckets\":[{\"key\":\"P1\","
				+ "\"issuedOut\":{\"qty\":{\"value\":99}}}]}}]},"
				+ "\"" + FacilityScopeResolver.AGG_EDGES_DISPATCHED + "\":{\"source\":{\"buckets\":["
				+ "{\"key\":\"STATE\",\"target\":{\"buckets\":[{\"key\":\"HF1\"}]}},"
				+ "{\"key\":\"UPSTREAM\",\"target\":{\"buckets\":[{\"key\":\"STATE\"}]}}]}}}}");

		StockSummaryAggregation aggregation = new StockSummaryAggregation();
		java.util.Map<String, Object> assembled = aggregation.assemble(response, rootedAtState());

		@SuppressWarnings("unchecked")
		java.util.List<java.util.Map<String, Object>> rows =
				(java.util.List<java.util.Map<String, Object>>) assembled.get("rows");

		assertEquals(1, rows.size(), "UPSTREAM is outside the walk and must not appear");
		assertEquals("HF1", rows.get(0).get("facilityId"));
		assertEquals(FacilityScopeResolver.Rule.SHIPMENT_DERIVED.name(), assembled.get("facilityScopeRule"));
	}

	@Test
	@DisplayName("without a scope every facility the filters touched is kept")
	void unscopedKeepsEverything() throws Exception {
		JsonNode response = MAPPER.readTree("{\"aggregations\":{\""
				+ StockSummaryAggregation.AGG_BY_FACILITY + "\":{\"buckets\":["
				+ "{\"key\":\"HF1\",\"byProduct\":{\"buckets\":[{\"key\":\"P1\","
				+ "\"issuedOut\":{\"qty\":{\"value\":10}}}]}},"
				+ "{\"key\":\"UPSTREAM\",\"byProduct\":{\"buckets\":[{\"key\":\"P1\","
				+ "\"issuedOut\":{\"qty\":{\"value\":99}}}]}}]}}}");

		java.util.Map<String, Object> assembled = new StockSummaryAggregation().assemble(response, null);

		@SuppressWarnings("unchecked")
		java.util.List<java.util.Map<String, Object>> rows =
				(java.util.List<java.util.Map<String, Object>>) assembled.get("rows");

		assertEquals(2, rows.size());
		assertFalse(assembled.containsKey("facilityScopeRule"), "no scope was applied, so none is claimed");
	}

	@Test
	@DisplayName("a complete link aggregation is reported as complete")
	void completeLinksAreReportedComplete() throws Exception {
		FacilityScopeResolver.Scope resolved = resolver.resolve(chain(), rootedAtState());

		assertTrue(resolved.isComplete());
	}

	@Test
	@DisplayName("links cut by the terms limits mark the walk incomplete instead of failing silently")
	void truncatedLinksAreReportedIncomplete() throws Exception {
		// sum_other_doc_count > 0 on the source terms: more sending facilities exist than were
		// returned, so the walk may be missing rows it should have reached.
		JsonNode truncatedSource = MAPPER.readTree("{\"aggregations\":{\""
				+ FacilityScopeResolver.AGG_EDGES_DISPATCHED + "\":{"
				+ "\"source\":{\"sum_other_doc_count\":42,\"buckets\":["
				+ "{\"key\":\"STATE\",\"target\":{\"buckets\":[{\"key\":\"HF1\"}]}}]}}}}");
		assertFalse(resolver.resolve(truncatedSource, rootedAtState()).isComplete());

		// The same signal one level down, on a source bucket's target list.
		JsonNode truncatedTarget = MAPPER.readTree("{\"aggregations\":{\""
				+ FacilityScopeResolver.AGG_EDGES_DISPATCHED + "\":{"
				+ "\"source\":{\"buckets\":["
				+ "{\"key\":\"STATE\",\"target\":{\"sum_other_doc_count\":3,\"buckets\":[{\"key\":\"HF1\"}]}}]}}}}");
		assertFalse(resolver.resolve(truncatedTarget, rootedAtState()).isComplete());

		// And the fallback rule carries the signal too.
		assertFalse(resolver.resolve(truncatedSource, new FacilityScope()).isComplete());
	}

	@Test
	@DisplayName("an incomplete walk is stamped onto the assembled summary")
	void incompleteWalkSurfacesOnTheSummary() throws Exception {
		JsonNode response = MAPPER.readTree("{\"aggregations\":{"
				+ "\"" + StockSummaryAggregation.AGG_BY_FACILITY + "\":{\"buckets\":["
				+ "{\"key\":\"HF1\",\"byProduct\":{\"buckets\":[{\"key\":\"P1\","
				+ "\"issuedOut\":{\"qty\":{\"value\":10}}}]}}]},"
				+ "\"" + FacilityScopeResolver.AGG_EDGES_DISPATCHED + "\":{\"source\":{"
				+ "\"sum_other_doc_count\":42,\"buckets\":["
				+ "{\"key\":\"STATE\",\"target\":{\"buckets\":[{\"key\":\"HF1\"}]}}]}}}}");

		java.util.Map<String, Object> assembled = new StockSummaryAggregation().assemble(response, rootedAtState());

		assertEquals(Boolean.FALSE, assembled.get("facilityScopeComplete"));
	}

	@Test
	@DisplayName("multiple starting facilities are all walked from")
	void multipleRoots() throws Exception {
		FacilityScope scope = new FacilityScope();
		scope.setRootFacilityIds(Arrays.asList("STATE", "UPSTREAM"));

		FacilityScopeResolver.Scope resolved = resolver.resolve(chain(), scope);

		assertTrue(resolved.getFacilityIds().contains("HF1"));
		assertTrue(resolved.getFacilityIds().contains("HF2"));
		assertFalse(resolved.getFacilityIds().contains("UPSTREAM"), "still a root, still excluded");
	}
}
