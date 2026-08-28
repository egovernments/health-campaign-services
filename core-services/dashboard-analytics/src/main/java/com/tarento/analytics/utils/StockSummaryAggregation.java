package com.tarento.analytics.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.egov.tracer.model.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.dto.StockSummaryRequest;

/**
 * Computes per-facility, per-product stock totals in the service instead of the browser.
 *
 * <p>Both halves live here on purpose. The Elasticsearch aggregation is generated from the same rule
 * table that is used to read the response back, so the query and its interpretation cannot drift
 * apart — which is the failure mode a hand-written query paired with a hand-written reader invites.
 *
 * <p>The rules themselves are a faithful transcription of the arithmetic the dashboard performs
 * today. The part that makes it more than a group-by: a single transaction contributes to <em>two</em>
 * facilities with different measures — the sender and the receiver — and which stored field holds
 * which role flips on the transaction's own entry type and event type. That is why the totals are
 * gathered under two separate groupings and merged afterwards.
 */
@Component
public class StockSummaryAggregation {

	private static final Logger logger = LoggerFactory.getLogger(StockSummaryAggregation.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Upper bound on the aggregation buckets one request may ask Elasticsearch to build, held under
	 * the cluster's default breaker ({@code search.max_buckets}, 65,536) with headroom for the
	 * facility-link aggregation. Enforced here and failed loudly, because a request over the
	 * cluster's own limit does not fail loudly: the 400 is swallowed upstream and surfaces as an
	 * empty HTTP 200 — the silent-failure shape this feature exists to remove. The link aggregation
	 * is excluded from the arithmetic deliberately: its bucket count is bounded by the distinct
	 * sender-receiver pairs that actually transacted, not by the limits' product.
	 */
	static final int MAX_BUCKET_BUDGET = 60_000;

	// Defaulted so this class can be unit-tested without a Spring context; Spring injects over it.
	@Autowired
	private FacilityScopeResolver facilityScopeResolver = new FacilityScopeResolver();

	static final String FIELD_FACILITY = "Data.facilityId.keyword";
	static final String FIELD_TRANSACTING_FACILITY = "Data.transactingFacilityId.keyword";
	static final String FIELD_PRODUCT = "Data.productVariant.keyword";
	static final String FIELD_QUANTITY = "Data.physicalCount";
	static final String FIELD_ENTRY_TYPE = "Data.additionalDetails.stockEntryType.keyword";
	static final String FIELD_STATUS = "Data.additionalDetails.status.keyword";
	static final String FIELD_EVENT_TYPE = "Data.eventType.keyword";

	private static final String ENTRY_ISSUED = "ISSUED";
	private static final String ENTRY_RETURNED = "RETURNED";
	private static final String STATUS_ACCEPTED = "ACCEPTED";
	private static final String STATUS_IN_TRANSIT = "IN_TRANSIT";
	private static final String STATUS_REJECTED = "REJECTED";
	private static final String EVENT_DISPATCHED = "DISPATCHED";
	private static final String EVENT_RECEIVED = "RECEIVED";

	static final String AGG_BY_FACILITY = "stockSummaryByFacility";
	static final String AGG_BY_TRANSACTING_FACILITY = "stockSummaryByTransactingFacility";
	private static final String AGG_BY_PRODUCT = "byProduct";
	private static final String AGG_QUANTITY = "qty";
	private static final String BUCKETS = "buckets";
	private static final String KEY = "key";
	private static final String VALUE = "value";
	private static final String SUM_OTHER_DOC_COUNT = "sum_other_doc_count";
	private static final String DOC_COUNT = "doc_count";

	/**
	 * Joins facility and product into one map key. A NUL cannot occur inside either identifier, so
	 * the split is unambiguous in a way a space or a dash would not be. Written as an escape so the
	 * source stays plain text.
	 */
	private static final char KEY_SEPARATOR = '\u0000';

	/** The totals a transaction can contribute to. */
	private enum Measure {
		RECEIVED, ACCEPTED, ISSUED, REJECTED, RETURNED
	}

	/** One contribution: which grouping it lands under, which transactions it covers, what it adds to. */
	private static final class Rule {
		private final String name;
		private final String groupField;
		private final String entryType;
		private final List<String> statuses;
		private final String eventType; // null matches any
		private final Measure[] measures;

		private Rule(String name, String groupField, String entryType, List<String> statuses, String eventType,
				Measure... measures) {
			this.name = name;
			this.groupField = groupField;
			this.entryType = entryType;
			this.statuses = statuses;
			this.eventType = eventType;
			this.measures = measures;
		}
	}

	private static final List<String> ACTIVE = Arrays.asList(STATUS_ACCEPTED, STATUS_IN_TRANSIT);
	private static final List<String> ACCEPTED_ONLY = Collections.singletonList(STATUS_ACCEPTED);
	private static final List<String> REJECTED_ONLY = Collections.singletonList(STATUS_REJECTED);

	/**
	 * Transcribed from the dashboard's own arithmetic.
	 *
	 * <p>For a returned transaction the sender is always the facility field and the receiver the
	 * counterparty field. For an issued one the roles follow the event type: dispatched keeps that
	 * orientation, received reverses it. Entry types other than issued and returned — receipts and
	 * over/under adjustments — match no rule and are ignored, exactly as the dashboard ignores them
	 * to avoid double counting.
	 */
	private static final List<Rule> RULES = Collections.unmodifiableList(Arrays.asList(
			// Facility field acts as the SENDER
			new Rule("issuedOut", FIELD_FACILITY, ENTRY_ISSUED, ACTIVE, EVENT_DISPATCHED, Measure.ISSUED),
			new Rule("rejectedOut", FIELD_FACILITY, ENTRY_ISSUED, REJECTED_ONLY, EVENT_DISPATCHED, Measure.REJECTED),
			new Rule("returnedOut", FIELD_FACILITY, ENTRY_RETURNED, ACTIVE, null, Measure.RETURNED),
			// Facility field acts as the RECEIVER (an issue recorded from the receiving end)
			new Rule("receivedIn", FIELD_FACILITY, ENTRY_ISSUED, ACCEPTED_ONLY, EVENT_RECEIVED,
					Measure.RECEIVED, Measure.ACCEPTED),
			new Rule("rejectedIn", FIELD_FACILITY, ENTRY_ISSUED, REJECTED_ONLY, EVENT_RECEIVED, Measure.REJECTED),

			// Counterparty field acts as the RECEIVER
			new Rule("receivedCounterparty", FIELD_TRANSACTING_FACILITY, ENTRY_ISSUED, ACCEPTED_ONLY, EVENT_DISPATCHED,
					Measure.RECEIVED, Measure.ACCEPTED),
			new Rule("rejectedCounterparty", FIELD_TRANSACTING_FACILITY, ENTRY_ISSUED, REJECTED_ONLY, EVENT_DISPATCHED,
					Measure.REJECTED),
			new Rule("returnedReceivedCounterparty", FIELD_TRANSACTING_FACILITY, ENTRY_RETURNED, ACCEPTED_ONLY, null,
					Measure.RECEIVED),
			// Counterparty field acts as the SENDER
			new Rule("issuedCounterparty", FIELD_TRANSACTING_FACILITY, ENTRY_ISSUED, ACTIVE, EVENT_RECEIVED,
					Measure.ISSUED),
			new Rule("rejectedSenderCounterparty", FIELD_TRANSACTING_FACILITY, ENTRY_ISSUED, REJECTED_ONLY,
					EVENT_RECEIVED, Measure.REJECTED)));

	// ------------------------------------------------------------------ query

	/**
	 * Adds the totals aggregation to a query that is otherwise unchanged.
	 *
	 * @param queryRoot the Elasticsearch request body; modified in place
	 * @param request   the caller's limits
	 */
	public void applyAggregation(ObjectNode queryRoot, StockSummaryRequest request) {
		if (queryRoot == null || request == null) {
			return;
		}
		int facilityLimit = request.resolvedFacilityLimit();
		int productLimit = request.resolvedProductLimit();
		requireWithinBucketBudget(facilityLimit, productLimit);

		ObjectNode aggs = queryRoot.with("aggs");
		aggs.set(AGG_BY_FACILITY, groupingFor(FIELD_FACILITY, facilityLimit, productLimit));
		aggs.set(AGG_BY_TRANSACTING_FACILITY, groupingFor(FIELD_TRANSACTING_FACILITY, facilityLimit, productLimit));
		// Facility links, read over facility pairs rather than documents, so the walk is bounded by
		// how many facilities exist and not by campaign volume.
		facilityScopeResolver.applyEdgeAggregation(queryRoot, request.getFacilityScope(), facilityLimit);
	}

	/**
	 * Worst case, one grouping builds a bucket per facility, per product under it, and per counting
	 * rule under that — {@code F × (1 + P × (1 + rules))} — and there are two groupings.
	 */
	private void requireWithinBucketBudget(int facilityLimit, int productLimit) {
		long worstCase = 0L;
		for (String groupField : new String[] { FIELD_FACILITY, FIELD_TRANSACTING_FACILITY }) {
			long rules = RULES.stream().filter(rule -> rule.groupField.equals(groupField)).count();
			worstCase += facilityLimit * (1L + productLimit * (1L + rules));
		}
		if (worstCase > MAX_BUCKET_BUDGET) {
			throw new CustomException("INVALID_STOCK_SUMMARY",
					"facilityLimit " + facilityLimit + " and productLimit " + productLimit + " would ask for up to "
							+ worstCase + " aggregation buckets, above the " + MAX_BUCKET_BUDGET
							+ " budget. Lower one of the limits; to cover more facilities, request fewer products "
							+ "per facility, or page facilities across several requests.");
		}
	}

	private ObjectNode groupingFor(String groupField, int facilityLimit, int productLimit) {
		ObjectNode grouping = MAPPER.createObjectNode();
		ObjectNode terms = grouping.with("terms");
		terms.put("field", groupField);
		terms.put("size", facilityLimit);

		ObjectNode byProduct = grouping.with("aggs").with(AGG_BY_PRODUCT);
		ObjectNode productTerms = byProduct.with("terms");
		productTerms.put("field", FIELD_PRODUCT);
		productTerms.put("size", productLimit);

		ObjectNode measures = byProduct.with("aggs");
		for (Rule rule : RULES) {
			if (!rule.groupField.equals(groupField)) {
				continue;
			}
			ObjectNode measure = measures.with(rule.name);
			measure.set("filter", filterFor(rule));
			measure.with("aggs").with(AGG_QUANTITY).with("sum").put("field", FIELD_QUANTITY);
		}
		return grouping;
	}

	private ObjectNode filterFor(Rule rule) {
		ObjectNode filter = MAPPER.createObjectNode();
		ArrayNode must = (ArrayNode) filter.with("bool").withArray("must");

		ObjectNode entry = MAPPER.createObjectNode();
		entry.with("term").put(FIELD_ENTRY_TYPE, rule.entryType);
		must.add(entry);

		ObjectNode status = MAPPER.createObjectNode();
		ArrayNode statusValues = (ArrayNode) status.with("terms").withArray(FIELD_STATUS);
		rule.statuses.forEach(statusValues::add);
		must.add(status);

		if (rule.eventType != null) {
			ObjectNode event = MAPPER.createObjectNode();
			event.with("term").put(FIELD_EVENT_TYPE, rule.eventType);
			must.add(event);
		}
		return filter;
	}

	// ------------------------------------------------------------------ response

	/**
	 * Reads the aggregation back and merges the two groupings into one row per facility and product.
	 *
	 * <p>When {@code facilitiesDropped} comes back true, the shown rows are not merely a shortened
	 * list: the two groupings are truncated independently, so a facility kept in one and cut from
	 * the other keeps its row with one side's measures missing — a plausible-looking, understated
	 * balance. That is why every degraded signal here is also logged at error level.
	 *
	 * @param esResponse the full Elasticsearch response for the dataset
	 * @param scope      which facilities belong in the summary; null leaves every facility in
	 * @return rows plus the completeness signals, or null when the response carries no such
	 *         aggregation
	 */
	public Map<String, Object> assemble(JsonNode esResponse, com.tarento.analytics.dto.FacilityScope scope) {
		if (esResponse == null || !esResponse.isObject()) {
			return null;
		}
		JsonNode aggregations = esResponse.get("aggregations");
		if (aggregations == null || !aggregations.has(AGG_BY_FACILITY)) {
			return null;
		}

		Map<String, Map<Measure, Long>> totals = new LinkedHashMap<>();
		boolean facilitiesDropped = false;
		ReadSignals signals = new ReadSignals();

		for (String grouping : new String[] { AGG_BY_FACILITY, AGG_BY_TRANSACTING_FACILITY }) {
			JsonNode node = aggregations.get(grouping);
			if (node == null) {
				continue;
			}
			if (node.path(SUM_OTHER_DOC_COUNT).asLong(0L) > 0L) {
				facilitiesDropped = true;
			}
			accumulate(node, totals, signals);
		}

		FacilityScopeResolver.Scope resolved = facilityScopeResolver.resolve(esResponse, scope);
		Set<String> allowed = resolved == null ? null : resolved.getFacilityIds();

		List<Map<String, Object>> rows = new ArrayList<>(totals.size());
		for (Map.Entry<String, Map<Measure, Long>> entry : totals.entrySet()) {
			int split = entry.getKey().indexOf(KEY_SEPARATOR);
			if (allowed != null && !allowed.contains(entry.getKey().substring(0, split))) {
				continue;
			}
			Map<Measure, Long> measures = entry.getValue();
			long received = measures.getOrDefault(Measure.RECEIVED, 0L);
			long accepted = measures.getOrDefault(Measure.ACCEPTED, 0L);
			long issued = measures.getOrDefault(Measure.ISSUED, 0L);
			long rejected = measures.getOrDefault(Measure.REJECTED, 0L);
			long returned = measures.getOrDefault(Measure.RETURNED, 0L);

			Map<String, Object> row = new LinkedHashMap<>();
			row.put("facilityId", entry.getKey().substring(0, split));
			row.put("productVariantId", entry.getKey().substring(split + 1));
			row.put("totalReceived", received);
			row.put("totalAccepted", accepted);
			row.put("totalIssued", issued);
			row.put("totalRejected", rejected);
			row.put("totalReturned", returned);
			row.put("balance", accepted - issued - returned);
			rows.add(row);
		}

		// Facilities have transactions, yet not one matched any counting rule: the entry-type and
		// status fields the rules filter on are almost certainly absent from this index — they are
		// written by a transformer-side enrichment that not every environment carries. Without this
		// check that environment sees an empty summary indistinguishable from a quiet campaign.
		boolean noMeasuresMatched = signals.facilityDocs > 0L && signals.ruleMatchedDocs == 0L;

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("rows", rows);
		result.put("facilitiesDropped", facilitiesDropped);
		result.put("productsDropped", signals.productsDropped);
		if (resolved != null) {
			// Say which rule produced the row set. The dashboard switches rules silently today, and a
			// row set nobody can account for is how this whole class of defect stays invisible.
			result.put("facilityScopeRule", resolved.getRule().name());
			result.put("facilityScopeComplete", resolved.isComplete());
		}
		if (noMeasuresMatched) {
			result.put("noMeasuresMatched", true);
		}

		if (facilitiesDropped || signals.productsDropped || (resolved != null && !resolved.isComplete())) {
			logger.error("Incomplete stock summary: facilitiesDropped={}, productsDropped={}, scopeComplete={}. "
					+ "Rows may be missing AND the balances of rows that are shown may be understated, because "
					+ "the two groupings truncate independently. Lower the limits' product per request and page "
					+ "facilities across requests.",
					facilitiesDropped, signals.productsDropped, resolved == null || resolved.isComplete());
		}
		if (noMeasuresMatched) {
			logger.error("Stock summary matched no counting rule despite {} transaction(s) in scope. The index "
					+ "likely lacks {} / {} — the transformer enrichment this summary depends on is not on every "
					+ "environment.", signals.facilityDocs, FIELD_ENTRY_TYPE, FIELD_STATUS);
		}
		return result;
	}

	/** What was seen while reading the buckets back, beyond the totals themselves. */
	private static final class ReadSignals {
		private boolean productsDropped;
		private long facilityDocs;
		private long ruleMatchedDocs;
	}

	private void accumulate(JsonNode grouping, Map<String, Map<Measure, Long>> totals, ReadSignals signals) {
		for (JsonNode facilityBucket : grouping.path(BUCKETS)) {
			String facilityId = facilityBucket.path(KEY).asText();
			if (facilityId.isEmpty()) {
				continue;
			}
			signals.facilityDocs += facilityBucket.path(DOC_COUNT).asLong(0L);
			if (facilityBucket.path(AGG_BY_PRODUCT).path(SUM_OTHER_DOC_COUNT).asLong(0L) > 0L) {
				signals.productsDropped = true;
			}
			for (JsonNode productBucket : facilityBucket.path(AGG_BY_PRODUCT).path(BUCKETS)) {
				String productVariantId = productBucket.path(KEY).asText();
				if (productVariantId.isEmpty()) {
					continue;
				}
				String cell = facilityId + KEY_SEPARATOR + productVariantId;
				for (Rule rule : RULES) {
					JsonNode measureNode = productBucket.get(rule.name);
					if (measureNode == null) {
						continue;
					}
					signals.ruleMatchedDocs += measureNode.path(DOC_COUNT).asLong(0L);
					long quantity = Math.round(measureNode.path(AGG_QUANTITY).path(VALUE).asDouble(0d));
					if (quantity == 0L) {
						continue;
					}
					Map<Measure, Long> measures = totals.computeIfAbsent(cell, k -> new LinkedHashMap<>());
					for (Measure measure : rule.measures) {
						measures.merge(measure, quantity, Long::sum);
					}
				}
			}
		}
	}
}
