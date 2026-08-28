package com.tarento.analytics.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.dto.FacilityScope;

/**
 * Works out which facilities belong in the stock summary.
 *
 * <p>This is the seam the facility-list decision sits behind. Today it walks shipment links, which
 * is what the dashboard does; sourcing the list from the campaign hierarchy instead would be a
 * second branch here, leaving the totals arithmetic untouched.
 *
 * <p>The walk is deliberately bounded by the number of facilities rather than the number of
 * transactions: the links are read from an aggregation over facility pairs, not from the documents
 * themselves, so it does not grow with campaign volume and cannot reach the record limit.
 */
@Component
public class FacilityScopeResolver {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	static final String AGG_EDGES_DISPATCHED = "facilityEdgesDispatched";
	static final String AGG_EDGES_RECEIVED = "facilityEdgesReceived";
	private static final String AGG_TARGET = "target";
	private static final String BUCKETS = "buckets";
	private static final String KEY = "key";
	private static final String SUM_OTHER_DOC_COUNT = "sum_other_doc_count";

	/** How the row set was arrived at, so the caller is never left guessing. */
	public enum Rule {
		/** Walked outward from the caller's own facilities. */
		SHIPMENT_DERIVED,
		/**
		 * No starting facilities were supplied, so every facility that received anything is included.
		 * This mirrors the dashboard's own fallback, which today happens silently.
		 */
		ALL_RECEIVERS_FALLBACK
	}

	/** The resolved row set, which rule produced it, and whether the links it walked were complete. */
	public static final class Scope {
		private final Set<String> facilityIds;
		private final Rule rule;
		private final boolean complete;

		Scope(Set<String> facilityIds, Rule rule, boolean complete) {
			this.facilityIds = facilityIds;
			this.rule = rule;
			this.complete = complete;
		}

		public Set<String> getFacilityIds() {
			return facilityIds;
		}

		public Rule getRule() {
			return rule;
		}

		/**
		 * False when the link aggregation returned fewer distinct facilities than exist — the walk
		 * may then be missing rows it should have reached, so the caller must say so rather than
		 * present the row set as authoritative.
		 */
		public boolean isComplete() {
			return complete;
		}
	}

	/**
	 * Adds the facility-pair aggregation the walk reads. Two of them, because which stored field
	 * holds the sender flips with the event type — the same reason the totals are gathered twice.
	 */
	public void applyEdgeAggregation(ObjectNode queryRoot, FacilityScope scope, int facilityLimit) {
		if (queryRoot == null || scope == null) {
			return;
		}
		ObjectNode aggs = queryRoot.with("aggs");
		aggs.set(AGG_EDGES_DISPATCHED, edges(StockSummaryAggregation.FIELD_FACILITY,
				StockSummaryAggregation.FIELD_TRANSACTING_FACILITY, "DISPATCHED", facilityLimit));
		aggs.set(AGG_EDGES_RECEIVED, edges(StockSummaryAggregation.FIELD_TRANSACTING_FACILITY,
				StockSummaryAggregation.FIELD_FACILITY, "RECEIVED", facilityLimit));
	}

	private ObjectNode edges(String fromField, String toField, String eventType, int limit) {
		ObjectNode grouping = MAPPER.createObjectNode();
		ObjectNode filter = grouping.with("filter");
		ArrayNode must = (ArrayNode) filter.with("bool").withArray("must");

		ObjectNode entry = MAPPER.createObjectNode();
		entry.with("term").put(StockSummaryAggregation.FIELD_ENTRY_TYPE, "ISSUED");
		must.add(entry);
		ObjectNode event = MAPPER.createObjectNode();
		event.with("term").put(StockSummaryAggregation.FIELD_EVENT_TYPE, eventType);
		must.add(event);

		ObjectNode from = grouping.with("aggs").with("source");
		from.with("terms").put("field", fromField).put("size", limit);
		ObjectNode to = from.with("aggs").with(AGG_TARGET);
		to.with("terms").put("field", toField).put("size", limit);
		return grouping;
	}

	/**
	 * Reads the links back and walks them.
	 *
	 * @return the facilities that belong in the summary, or null when the response carries no link
	 *         aggregation to walk
	 */
	public Scope resolve(JsonNode esResponse, FacilityScope scope) {
		if (esResponse == null || scope == null) {
			return null;
		}
		JsonNode aggregations = esResponse.get("aggregations");
		if (aggregations == null || !aggregations.has(AGG_EDGES_DISPATCHED)) {
			return null;
		}

		Map<String, Set<String>> shippedTo = new HashMap<>();
		Set<String> receivers = new HashSet<>();
		// Bitwise on purpose: both sides must be read even when the first already reported truncation.
		boolean truncated = readEdges(aggregations.get(AGG_EDGES_DISPATCHED), shippedTo, receivers)
				| readEdges(aggregations.get(AGG_EDGES_RECEIVED), shippedTo, receivers);

		if (!scope.hasRoots()) {
			// The dashboard makes the same substitution when it does not yet know the manager's own
			// facilities. Reproduced so the rows match, but reported rather than applied silently.
			return new Scope(new LinkedHashSet<>(receivers), Rule.ALL_RECEIVERS_FALLBACK, !truncated);
		}

		Set<String> visited = new HashSet<>(scope.getRootFacilityIds());
		Set<String> reached = new LinkedHashSet<>();
		Deque<String> queue = new ArrayDeque<>(scope.getRootFacilityIds());
		while (!queue.isEmpty()) {
			String current = queue.removeFirst();
			for (String child : shippedTo.getOrDefault(current, java.util.Collections.emptySet())) {
				if (visited.add(child)) {
					reached.add(child);
					queue.addLast(child);
				}
			}
		}

		// Roots are excluded unless asked for: a manager's own facility does not appear in its own
		// summary today, and quietly adding it would change what is on screen.
		if (scope.rootsIncluded()) {
			Set<String> withRoots = new LinkedHashSet<>(scope.getRootFacilityIds());
			withRoots.addAll(reached);
			return new Scope(withRoots, Rule.SHIPMENT_DERIVED, !truncated);
		}
		return new Scope(reached, Rule.SHIPMENT_DERIVED, !truncated);
	}

	/**
	 * @return true when Elasticsearch reported more distinct facilities than the terms limits let it
	 *         return — links are then missing and any walk over them is incomplete
	 */
	private boolean readEdges(JsonNode grouping, Map<String, Set<String>> shippedTo, Set<String> receivers) {
		if (grouping == null) {
			return false;
		}
		boolean truncated = grouping.path("source").path(SUM_OTHER_DOC_COUNT).asLong(0L) > 0L;
		for (JsonNode fromBucket : grouping.path("source").path(BUCKETS)) {
			String from = fromBucket.path(KEY).asText();
			if (from.isEmpty()) {
				continue;
			}
			if (fromBucket.path(AGG_TARGET).path(SUM_OTHER_DOC_COUNT).asLong(0L) > 0L) {
				truncated = true;
			}
			List<String> targets = new ArrayList<>();
			for (JsonNode toBucket : fromBucket.path(AGG_TARGET).path(BUCKETS)) {
				String to = toBucket.path(KEY).asText();
				if (!to.isEmpty()) {
					targets.add(to);
					receivers.add(to);
				}
			}
			if (!targets.isEmpty()) {
				shippedTo.computeIfAbsent(from, k -> new LinkedHashSet<>()).addAll(targets);
			}
		}
		return truncated;
	}
}
