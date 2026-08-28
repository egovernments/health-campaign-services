package com.tarento.analytics.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.PaginationDto;
import com.tarento.analytics.dto.RangeFilter;
import com.tarento.analytics.dto.SortCriteria;

class RawQueryEnhancerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** The commodity chart's configured query, as it actually appears in tenant configuration. */
	private static final String CONFIGURED_QUERY =
			"{\"size\":10000,\"_source\":[\"Data.facilityId\",\"Data.physicalCount\"]}";

	private RawQueryEnhancer enhancer;

	@BeforeEach
	void setUp() {
		enhancer = new RawQueryEnhancer();
	}

	private ObjectNode configuredQuery() throws JsonProcessingException {
		return (ObjectNode) MAPPER.readTree(CONFIGURED_QUERY);
	}

	/** A document-returning query whose projection bounds the fields a caller may name. */
	private JsonNode documentQueryConfig() throws JsonProcessingException {
		return MAPPER.readTree("{\"transformData\":\"rawDocuments\",\"aggrQuery\":"
				+ "\"{\\\"size\\\":10000,\\\"_source\\\":[\\\"Data.facilityId\\\",\\\"Data.dateOfEntry\\\","
				+ "\\\"Data.id\\\",\\\"Data.additionalDetails.status\\\",\\\"Data.additionalDetails.stockEntryType\\\",\\\"Data.valid\\\"]}\"}");
	}

	/** A document query that declares no projection, so no field restriction can be inferred. */
	private JsonNode unprojectedQueryConfig() throws JsonProcessingException {
		return MAPPER.readTree("{\"transformData\":\"rawDocuments\"}");
	}

	private JsonNode aggregationQueryConfig() throws JsonProcessingException {
		return MAPPER.readTree("{\"transformData\":\"termsAggregation\"}");
	}

	private static final java.util.Set<String> NO_RESTRICTION = java.util.Collections.emptySet();

	// ---------------------------------------------------------------- backward compatibility

	@Test
	@DisplayName("a request with no pagination and no filters leaves the configured query untouched")
	void leavesQueryUntouchedWhenNothingRequested() throws Exception {
		ObjectNode query = configuredQuery();
		String before = MAPPER.writeValueAsString(query);

		enhancer.enhance(query, new AggregateRequestDto(), documentQueryConfig());

		assertEquals(before, MAPPER.writeValueAsString(query));
	}

	// ------------------------------------------------- aggregation datasets are left alone

	private AggregateRequestDto fullyLoadedRequest() {
		AggregateRequestDto request = new AggregateRequestDto();
		request.setTermFilters(Collections.singletonMap("Data.additionalDetails.status.keyword", "IN_TRANSIT"));
		request.setRangeFilters(Collections.singletonList(new RangeFilter("Data.dateOfEntry", 1L, 2L)));
		PaginationDto pagination = new PaginationDto();
		pagination.setSize(100);
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "desc"));
		request.setPagination(pagination);
		return request;
	}

	@Test
	@DisplayName("an aggregation dataset keeps its configured shape when the caller pages the document list")
	void aggregationDatasetIsNotPaged() throws Exception {
		// A chart may hold both a document query and an aggregation query. The caller's paging
		// describes the documents; rewriting the aggregation's size:0 and imposing a sort on it
		// would return unwanted hits and can fail outright if the sort field is unmapped there.
		ObjectNode aggregationQuery = (ObjectNode) MAPPER.readTree(
				"{\"size\":0,\"aggs\":{\"byFacility\":{\"terms\":{\"field\":\"Data.facilityId.keyword\"}}}}");
		String before = MAPPER.writeValueAsString(aggregationQuery);

		enhancer.enhance(aggregationQuery, fullyLoadedRequest(), aggregationQueryConfig());

		assertEquals(before, MAPPER.writeValueAsString(aggregationQuery));
	}

	@Test
	@DisplayName("the document dataset of that same chart is still paged")
	void documentDatasetIsStillPaged() throws Exception {
		ObjectNode query = configuredQuery();

		enhancer.enhance(query, fullyLoadedRequest(), documentQueryConfig());

		assertEquals(100, query.get("size").asInt());
		assertTrue(query.has("sort"));
		assertEquals(2, query.get("query").get("bool").get("filter").size());
	}

	@Test
	@DisplayName("a query that declares no transform type is treated as not document-returning")
	void undeclaredTransformDataIsNotEnhanced() throws Exception {
		ObjectNode query = configuredQuery();
		String before = MAPPER.writeValueAsString(query);

		enhancer.enhance(query, fullyLoadedRequest(), MAPPER.readTree("{\"indexName\":\"stock-index-v1\"}"));
		enhancer.enhance(query, fullyLoadedRequest(), null);

		assertEquals(before, MAPPER.writeValueAsString(query));
	}

	@Test
	@DisplayName("the document-returning predicate matches only rawDocuments")
	void returnsDocumentsPredicate() throws Exception {
		assertTrue(enhancer.returnsDocuments(documentQueryConfig()));
		assertTrue(enhancer.returnsDocuments(MAPPER.readTree("{\"transformData\":\"RAWDOCUMENTS\"}")));
		assertFalse(enhancer.returnsDocuments(aggregationQueryConfig()));
		assertFalse(enhancer.returnsDocuments(MAPPER.readTree("{\"transformData\":\"linearAggregation\"}")));
		assertFalse(enhancer.returnsDocuments(MAPPER.readTree("{}")));
		assertFalse(enhancer.returnsDocuments(null));
	}

	@Test
	@DisplayName("an empty pagination object is treated as no pagination at all")
	void emptyPaginationIsANoOp() throws Exception {
		ObjectNode query = configuredQuery();
		String before = MAPPER.writeValueAsString(query);

		AggregateRequestDto request = new AggregateRequestDto();
		request.setPagination(new PaginationDto());
		enhancer.enhance(query, request, documentQueryConfig());

		assertEquals(before, MAPPER.writeValueAsString(query));
	}

	@Test
	@DisplayName("empty filter collections are a no-op rather than an empty bool clause")
	void emptyFiltersAreANoOp() throws Exception {
		ObjectNode query = configuredQuery();
		String before = MAPPER.writeValueAsString(query);

		AggregateRequestDto request = new AggregateRequestDto();
		request.setTermFilters(Collections.emptyMap());
		request.setRangeFilters(Collections.emptyList());
		enhancer.enhance(query, request, documentQueryConfig());

		assertEquals(before, MAPPER.writeValueAsString(query));
	}

	// ---------------------------------------------------------------- paging

	@Test
	@DisplayName("page size overrides the size baked into configuration")
	void sizeOverridesConfiguredSize() throws Exception {
		ObjectNode query = configuredQuery();
		assertEquals(10000, query.get("size").asInt());

		PaginationDto pagination = new PaginationDto();
		pagination.setSize(100);
		enhancer.applyPagination(query, pagination, NO_RESTRICTION);

		assertEquals(100, query.get("size").asInt());
	}

	@Test
	@DisplayName("a negative page size is rejected")
	void negativeSizeRejected() throws Exception {
		ObjectNode query = configuredQuery();
		PaginationDto pagination = new PaginationDto();
		pagination.setSize(-1);

		assertThrows(CustomException.class, () -> enhancer.applyPagination(query, pagination, NO_RESTRICTION));
	}

	@Test
	@DisplayName("sort clauses are emitted in order with the requested direction")
	void sortIsEmittedInOrder() throws Exception {
		ObjectNode query = configuredQuery();
		PaginationDto pagination = new PaginationDto();
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "desc"));

		enhancer.applyPagination(query, pagination, NO_RESTRICTION);

		JsonNode sort = query.get("sort");
		assertEquals(2, sort.size(), "the caller's key plus the appended tiebreaker");
		assertEquals("desc", sort.get(0).get("Data.dateOfEntry").get("order").asText());
		assertEquals("desc", sort.get(1).get("Data.id.keyword").get("order").asText(),
				"the tiebreaker follows the primary direction so paging reads in one order");
	}

	@Test
	@DisplayName("an unrecognised sort order falls back to ascending rather than reaching Elasticsearch")
	void unknownSortOrderDefaultsToAscending() throws Exception {
		ObjectNode query = configuredQuery();
		PaginationDto pagination = new PaginationDto();
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "sideways"));

		enhancer.applyPagination(query, pagination, NO_RESTRICTION);

		assertEquals("asc", query.get("sort").get(0).get("Data.dateOfEntry").get("order").asText());
	}

	@Test
	@DisplayName("the configured tiebreaker is appended so pages cannot repeat or skip rows")
	void tiebreakerIsAppended() throws Exception {
		ObjectNode query = configuredQuery();
		PaginationDto pagination = new PaginationDto();
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "desc"));

		enhancer.applyPagination(query, pagination, NO_RESTRICTION);

		JsonNode sort = query.get("sort");
		assertEquals(2, sort.size());
		assertTrue(sort.get(1).has("Data.id.keyword"));
		assertEquals("desc", sort.get(1).get("Data.id.keyword").get("order").asText(),
				"tiebreaker should follow the primary sort direction");
	}

	@Test
	@DisplayName("a tiebreaker the caller already sorted on is not added twice")
	void tiebreakerNotDuplicated() throws Exception {
		ObjectNode query = configuredQuery();
		PaginationDto pagination = new PaginationDto();
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "desc"));

		enhancer.applyPagination(query, pagination, NO_RESTRICTION);

		assertEquals(2, query.get("sort").size());
	}

	@Test
	@DisplayName("a continuation token is emitted as search_after")
	void searchAfterIsEmitted() throws Exception {
		ObjectNode query = configuredQuery();
		PaginationDto pagination = new PaginationDto();
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "desc"));
		pagination.setSearchAfter(Arrays.asList(1735689600000L, "stock-7"));

		enhancer.applyPagination(query, pagination, NO_RESTRICTION);

		assertEquals(2, query.get("search_after").size());
		assertEquals(1735689600000L, query.get("search_after").get(0).asLong());
		assertEquals("stock-7", query.get("search_after").get(1).asText());
	}

	@Test
	@DisplayName("a continuation token without a sort is rejected instead of silently mis-paging")
	void searchAfterWithoutSortRejected() throws Exception {
		ObjectNode query = configuredQuery();
		PaginationDto pagination = new PaginationDto();
		pagination.setSearchAfter(Collections.singletonList(123L));

		CustomException error =
				assertThrows(CustomException.class, () -> enhancer.applyPagination(query, pagination, NO_RESTRICTION));
		assertTrue(error.getMessage().contains("requires pagination.sort"));
	}

	@Test
	@DisplayName("a continuation token whose arity does not match the sort is rejected")
	void searchAfterArityMismatchRejected() throws Exception {
		ObjectNode query = configuredQuery();
		PaginationDto pagination = new PaginationDto();
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "desc"));
		// The tiebreaker makes the effective sort two clauses, so a one-value token is stale.
		pagination.setSearchAfter(Collections.singletonList(123L));

		CustomException error = assertThrows(CustomException.class,
				() -> enhancer.applyPagination(query, pagination, NO_RESTRICTION));
		assertTrue(error.getMessage().contains("nextPageToken"));
	}

	@Test
	@DisplayName("a caller cannot filter or sort on a field the chart does not return")
	void fieldsOutsideTheProjectionAreRejected() throws Exception {
		AggregateRequestDto filtering = new AggregateRequestDto();
		filtering.setTermFilters(Collections.singletonMap("Data.userName.keyword", "someone"));
		CustomException filterError = assertThrows(CustomException.class,
				() -> enhancer.enhance(configuredQuery(), filtering, documentQueryConfig()));
		assertTrue(filterError.getMessage().contains("Cannot filter on"));

		AggregateRequestDto sorting = new AggregateRequestDto();
		PaginationDto pagination = new PaginationDto();
		pagination.setSort(new SortCriteria("Data.userName", "asc"));
		sorting.setPagination(pagination);
		CustomException sortError = assertThrows(CustomException.class,
				() -> enhancer.enhance(configuredQuery(), sorting, documentQueryConfig()));
		assertTrue(sortError.getMessage().contains("Cannot sort on"));
	}

	@Test
	@DisplayName("a keyword sub-field of a projected field is allowed")
	void keywordSubFieldIsAllowed() throws Exception {
		ObjectNode query = configuredQuery();
		AggregateRequestDto request = new AggregateRequestDto();
		request.setTermFilters(Collections.singletonMap("Data.additionalDetails.status.keyword", "IN_TRANSIT"));

		enhancer.enhance(query, request, documentQueryConfig());

		assertEquals(1, query.get("query").get("bool").get("filter").size());
	}

	@Test
	@DisplayName("a chart that declares no projection imposes no field restriction")
	void unprojectedChartImposesNoRestriction() throws Exception {
		ObjectNode query = configuredQuery();
		AggregateRequestDto request = new AggregateRequestDto();
		request.setTermFilters(Collections.singletonMap("Data.anything.keyword", "x"));

		enhancer.enhance(query, request, unprojectedQueryConfig());

		assertEquals(1, query.get("query").get("bool").get("filter").size());
	}

	@Test
	@DisplayName("track_total_hits is only emitted when explicitly requested")
	void trackTotalHitsIsOptIn() throws Exception {
		ObjectNode optedIn = configuredQuery();
		PaginationDto on = new PaginationDto();
		on.setTrackTotalHits(true);
		enhancer.applyPagination(optedIn, on, NO_RESTRICTION);
		assertTrue(optedIn.get("track_total_hits").asBoolean());

		ObjectNode optedOut = configuredQuery();
		PaginationDto off = new PaginationDto();
		off.setTrackTotalHits(false);
		off.setSize(10);
		enhancer.applyPagination(optedOut, off, NO_RESTRICTION);
		assertFalse(optedOut.has("track_total_hits"));
	}

	// ---------------------------------------------------------------- filters

	@Test
	@DisplayName("a scalar term filter becomes a term condition")
	void scalarTermFilter() throws Exception {
		ObjectNode query = configuredQuery();
		Map<String, Object> filters = new LinkedHashMap<>();
		filters.put("Data.additionalDetails.status.keyword", "IN_TRANSIT");

		enhancer.applyExplicitFilters(query, filters, null, NO_RESTRICTION);

		JsonNode filter = query.get("query").get("bool").get("filter");
		assertEquals(1, filter.size());
		assertEquals("IN_TRANSIT", filter.get(0).get("term").get("Data.additionalDetails.status.keyword").asText());
	}

	@Test
	@DisplayName("a list term filter becomes a terms condition")
	void listTermFilter() throws Exception {
		ObjectNode query = configuredQuery();
		Map<String, Object> filters = new LinkedHashMap<>();
		filters.put("Data.additionalDetails.stockEntryType.keyword", Arrays.asList("RETURNED", "ISSUED"));

		enhancer.applyExplicitFilters(query, filters, null, NO_RESTRICTION);

		JsonNode terms = query.get("query").get("bool").get("filter").get(0)
				.get("terms").get("Data.additionalDetails.stockEntryType.keyword");
		assertEquals(2, terms.size());
		assertEquals("RETURNED", terms.get(0).asText());
	}

	@Test
	@DisplayName("a range filter emits inclusive bounds")
	void rangeFilterWithBothBounds() throws Exception {
		ObjectNode query = configuredQuery();

		enhancer.applyExplicitFilters(query, null, Collections.singletonList(new RangeFilter("Data.dateOfEntry", 1000L, 2000L)), NO_RESTRICTION);

		JsonNode range = query.get("query").get("bool").get("filter").get(0).get("range").get("Data.dateOfEntry");
		assertEquals(1000L, range.get("gte").asLong());
		assertEquals(2000L, range.get("lte").asLong());
	}

	@Test
	@DisplayName("a half-open range omits the absent bound")
	void rangeFilterWithOneBound() throws Exception {
		ObjectNode query = configuredQuery();

		enhancer.applyExplicitFilters(query, null, Collections.singletonList(new RangeFilter("Data.dateOfEntry", 1000L, null)), NO_RESTRICTION);

		JsonNode range = query.get("query").get("bool").get("filter").get(0).get("range").get("Data.dateOfEntry");
		assertTrue(range.has("gte"));
		assertFalse(range.has("lte"));
	}

	@Test
	@DisplayName("filters that carry no usable field or value are skipped, not emitted as broken clauses")
	void unusableFiltersAreSkipped() throws Exception {
		ObjectNode query = configuredQuery();
		Map<String, Object> filters = new LinkedHashMap<>();
		filters.put("  ", "value");
		filters.put("Data.facilityId", null);
		filters.put("Data.valid", "yes");

		enhancer.applyExplicitFilters(query, filters, Arrays.asList(
				new RangeFilter("Data.dateOfEntry", null, null),
				new RangeFilter("   ", 1L, 2L),
				null), NO_RESTRICTION);

		JsonNode filter = query.get("query").get("bool").get("filter");
		assertEquals(1, filter.size());
		assertEquals("yes", filter.get(0).get("term").get("Data.valid").asText());
	}

	@Test
	@DisplayName("explicit filters append to filters already merged from the request, they do not replace them")
	void explicitFiltersAppendToExisting() throws Exception {
		ObjectNode query = (ObjectNode) MAPPER.readTree(
				"{\"size\":10000,\"query\":{\"bool\":{\"filter\":[{\"term\":{\"Data.campaignNumber.keyword\":\"C-1\"}}]}}}");
		Map<String, Object> filters = new LinkedHashMap<>();
		filters.put("Data.additionalDetails.status.keyword", "IN_TRANSIT");

		enhancer.applyExplicitFilters(query, filters, null, NO_RESTRICTION);

		JsonNode filter = query.get("query").get("bool").get("filter");
		assertEquals(2, filter.size());
		assertEquals("C-1", filter.get(0).get("term").get("Data.campaignNumber.keyword").asText());
	}

	@Test
	@DisplayName("a configured must clause survives the addition of explicit filters")
	void mustClauseSurvives() throws Exception {
		ObjectNode query = (ObjectNode) MAPPER.readTree(
				"{\"query\":{\"bool\":{\"must_not\":[{\"range\":{\"Data.createdTime\":{\"lte\":1785090600000}}}]}}}");
		Map<String, Object> filters = new LinkedHashMap<>();
		filters.put("Data.valid", "yes");

		enhancer.applyExplicitFilters(query, filters, null, NO_RESTRICTION);

		assertEquals(1, query.get("query").get("bool").get("must_not").size(),
				"the tenant's own must_not clause must not be disturbed");
		assertEquals(1, query.get("query").get("bool").get("filter").size());
	}

	// ---------------------------------------------------------------- end to end

	@Test
	@DisplayName("a full pending-returns style request produces a bounded, ordered, filtered query")
	void fullRequestProducesBoundedQuery() throws Exception {
		ObjectNode query = configuredQuery();

		AggregateRequestDto request = new AggregateRequestDto();
		Map<String, Object> termFilters = new LinkedHashMap<>();
		termFilters.put("Data.additionalDetails.stockEntryType.keyword", "RETURNED");
		termFilters.put("Data.additionalDetails.status.keyword", "IN_TRANSIT");
		request.setTermFilters(termFilters);
		request.setRangeFilters(
				Collections.singletonList(new RangeFilter("Data.dateOfEntry", 1735689600000L, 1738368000000L)));
		PaginationDto pagination = new PaginationDto();
		pagination.setSize(50);
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "desc"));
		pagination.setTrackTotalHits(true);
		request.setPagination(pagination);

		enhancer.enhance(query, request, documentQueryConfig());

		assertEquals(50, query.get("size").asInt(), "page size must replace the 10,000 default");
		assertEquals(2, query.get("sort").size(), "sort must carry the tiebreaker");
		assertTrue(query.get("track_total_hits").asBoolean());
		assertEquals(3, query.get("query").get("bool").get("filter").size(),
				"two term filters and one range filter");
		assertTrue(query.has("_source"), "the configured source projection must be preserved");
	}

	@Test
	@DisplayName("the emitted body matches the Elasticsearch 8 request format exactly")
	void emittedBodyMatchesElasticsearchFormat() throws Exception {
		// Pins the wire format. Elasticsearch is not reachable from the build, so this is the
		// reviewable record of what the service actually sends: sort as an array of
		// single-key objects, search_after as a bare value array, track_total_hits as a boolean.
		ObjectNode query = (ObjectNode) MAPPER.readTree("{\"size\":10000}");

		AggregateRequestDto request = new AggregateRequestDto();
		request.setTermFilters(Collections.singletonMap("Data.additionalDetails.status.keyword", "IN_TRANSIT"));
		request.setRangeFilters(Collections.singletonList(new RangeFilter("Data.dateOfEntry", 1000L, 2000L)));
		PaginationDto pagination = new PaginationDto();
		pagination.setSize(2);
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "desc"));
		pagination.setSearchAfter(Arrays.asList(1500L, "stock-7"));
		pagination.setTrackTotalHits(true);
		request.setPagination(pagination);

		enhancer.enhance(query, request, documentQueryConfig());

		String expected = "{"
				+ "\"size\":2,"
				+ "\"query\":{\"bool\":{\"filter\":["
				+ "{\"term\":{\"Data.additionalDetails.status.keyword\":\"IN_TRANSIT\"}},"
				+ "{\"range\":{\"Data.dateOfEntry\":{\"gte\":1000,\"lte\":2000}}}"
				+ "]}},"
				+ "\"sort\":[{\"Data.dateOfEntry\":{\"order\":\"desc\"}},{\"Data.id.keyword\":{\"order\":\"desc\"}}],"
				+ "\"search_after\":[1500,\"stock-7\"],"
				+ "\"track_total_hits\":true"
				+ "}";

		// Compared as text, not as a node tree: Jackson treats IntNode(1500) and LongNode(1500) as
		// unequal even though both serialise to 1500, and it is the serialised bytes that reach
		// Elasticsearch.
		assertEquals(expected, MAPPER.writeValueAsString(query));
	}
}
