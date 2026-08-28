package com.tarento.analytics.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.dao.ElasticSearchDao;
import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.PaginationDto;
import com.tarento.analytics.dto.RangeFilter;
import com.tarento.analytics.dto.SortCriteria;
import com.tarento.analytics.model.ElasticSearchDictator;
import com.tarento.analytics.utils.RawQueryEnhancer;

/**
 * Covers the raw-document query builder as the dashboard actually drives it: the tenant's configured
 * query is the base, request filters are merged in, and paging is layered on top.
 */
class QueryServiceImplRawTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** The commodity chart's query configuration, matching what tenants deploy today. */
	private static final String QUERY_CONFIG = "{"
			+ "\"module\":\"COMMON\","
			+ "\"indexName\":\"stock-index-v1\","
			+ "\"dateRefField\":\"\","
			+ "\"transformData\":\"rawDocuments\","
			+ "\"requestQueryMap\":\"{\\\"campaignNumber\\\":\\\"Data.campaignNumber.keyword\\\"}\","
			+ "\"aggrQuery\":\"{\\\"size\\\":10000,\\\"_source\\\":[\\\"Data.facilityId\\\",\\\"Data.dateOfEntry\\\",\\\"Data.id\\\",\\\"Data.additionalDetails.stockEntryType\\\",\\\"Data.additionalDetails.status\\\"]}\"}";

	private QueryServiceImpl queryService;

	@BeforeEach
	void setUp() throws Exception {
		queryService = new QueryServiceImpl();

		ElasticSearchDao dao = mock(ElasticSearchDao.class);
		when(dao.createSearchDictatorV2(any(), anyString(), anyString(), anyString()))
				.thenReturn(new ElasticSearchDictator());
		// A minimal source builder stands in for the filter-merging half of the pipeline, which is
		// exercised by its own code path; this test is about what the raw builder does on top.
		when(dao.buildElasticSearchQuery(any()))
				.thenReturn(new SearchRequest("stock-index-v1").source(new SearchSourceBuilder().size(0)));

		ReflectionTestUtils.setField(queryService, "elasticSearchDao", dao);
		ReflectionTestUtils.setField(queryService, "rawQueryEnhancer", new RawQueryEnhancer());
	}

	private AggregateRequestDto baseRequest() {
		AggregateRequestDto request = new AggregateRequestDto();
		request.setModuleLevel("HOME");
		request.setFilters(new HashMap<>());
		return request;
	}

	private JsonNode queryConfig() throws Exception {
		return MAPPER.readTree(QUERY_CONFIG);
	}



	@Test
	@DisplayName("an unchanged request still emits the configured size and projection")
	void unchangedRequestPreservesConfiguredQuery() throws Exception {
		ObjectNode query = queryService.getChartConfigurationQueryRaw(
				baseRequest(), queryConfig(), "stock-index-v1", "");

		assertEquals(10000, query.get("size").asInt(), "the configured cap must still be honoured");
		assertEquals("Data.facilityId", query.get("_source").get(0).asText());
		assertFalse(query.has("sort"), "no sort should appear unless one was asked for");
		assertFalse(query.has("search_after"));
		assertFalse(query.has("track_total_hits"));
	}

	@Test
	@DisplayName("paging replaces the configured cap and orders the result")
	void pagingReplacesCapAndOrders() throws Exception {
		AggregateRequestDto request = baseRequest();
		PaginationDto pagination = new PaginationDto();
		pagination.setSize(100);
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "desc"));
		pagination.setTrackTotalHits(true);
		request.setPagination(pagination);

		ObjectNode query = queryService.getChartConfigurationQueryRaw(
				request, queryConfig(), "stock-index-v1", "");

		assertEquals(100, query.get("size").asInt());
		assertEquals(2, query.get("sort").size(), "the configured tiebreaker must be appended");
		assertTrue(query.get("track_total_hits").asBoolean());
	}

	@Test
	@DisplayName("the pending-returns filters reach the query without any tenant configuration change")
	void pendingReturnsFiltersReachTheQuery() throws Exception {
		AggregateRequestDto request = baseRequest();
		Map<String, Object> termFilters = new LinkedHashMap<>();
		termFilters.put("Data.additionalDetails.stockEntryType.keyword", "RETURNED");
		termFilters.put("Data.additionalDetails.status.keyword", "IN_TRANSIT");
		request.setTermFilters(termFilters);

		ObjectNode query = queryService.getChartConfigurationQueryRaw(
				request, queryConfig(), "stock-index-v1", "");

		JsonNode filters = query.get("query").get("bool").get("filter");
		assertEquals(2, filters.size());
		assertEquals("RETURNED",
				filters.get(0).get("term").get("Data.additionalDetails.stockEntryType.keyword").asText());
		assertEquals("IN_TRANSIT",
				filters.get(1).get("term").get("Data.additionalDetails.status.keyword").asText());
	}

	@Test
	@DisplayName("a date window is applied even though this chart declares no date reference field")
	void dateWindowAppliedDespiteBlankDateRefField() throws Exception {
		AggregateRequestDto request = baseRequest();
		request.setRangeFilters(Collections.singletonList(
				new RangeFilter("Data.dateOfEntry", 1735689600000L, 1738368000000L)));

		ObjectNode query = queryService.getChartConfigurationQueryRaw(
				request, queryConfig(), "stock-index-v1", "");

		JsonNode range = query.get("query").get("bool").get("filter").get(0).get("range").get("Data.dateOfEntry");
		assertEquals(1735689600000L, range.get("gte").asLong());
		assertEquals(1738368000000L, range.get("lte").asLong());
	}

	@Test
	@DisplayName("a second page carries the continuation token through")
	void secondPageCarriesToken() throws Exception {
		AggregateRequestDto request = baseRequest();
		PaginationDto pagination = new PaginationDto();
		pagination.setSize(100);
		pagination.setSort(new SortCriteria("Data.dateOfEntry", "desc"));
		pagination.setSearchAfter(java.util.Arrays.asList(1735689600000L, "stock-42"));
		request.setPagination(pagination);

		ObjectNode query = queryService.getChartConfigurationQueryRaw(
				request, queryConfig(), "stock-index-v1", "");

		assertEquals(2, query.get("search_after").size());
		assertEquals("stock-42", query.get("search_after").get(1).asText());
		assertFalse(query.has("from"), "continuation paging must not fall back to offset paging");
	}

	@Test
	@DisplayName("a malformed paging request fails loudly instead of returning the wrong page")
	void malformedPagingFailsLoudly() throws Exception {
		AggregateRequestDto request = baseRequest();
		PaginationDto pagination = new PaginationDto();
		pagination.setSearchAfter(Collections.singletonList(123L));
		request.setPagination(pagination);

		org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
				() -> queryService.getChartConfigurationQueryRaw(request, queryConfig(), "stock-index-v1", ""));
	}
}
