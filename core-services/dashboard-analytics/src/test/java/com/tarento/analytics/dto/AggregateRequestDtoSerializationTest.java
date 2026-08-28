package com.tarento.analytics.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The controller builds the response cache key by serializing this request object, so how it
 * serializes is behaviour rather than an implementation detail.
 */
class AggregateRequestDtoSerializationTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private AggregateRequestDto legacyRequest() {
		AggregateRequestDto request = new AggregateRequestDto();
		request.setVisualizationCode("commodityFacilityStockBalance");
		request.setVisualizationType("metric");
		request.setModuleLevel("HOME");
		request.setFilters(new HashMap<>(Collections.singletonMap("campaignNumber", "C-1")));
		return request;
	}

	@Test
	@DisplayName("a request that uses none of the new fields serialises without mentioning them")
	void unusedFieldsAreOmitted() throws Exception {
		String json = MAPPER.writeValueAsString(legacyRequest());

		assertFalse(json.contains("pagination"), "an unchanged caller must not see its cache key move");
		assertFalse(json.contains("termFilters"));
		assertFalse(json.contains("rangeFilters"));
	}

	@Test
	@DisplayName("two identical legacy requests hash the same")
	void legacyRequestsHashConsistently() throws Exception {
		assertEquals(MAPPER.writeValueAsString(legacyRequest()).hashCode(),
				MAPPER.writeValueAsString(legacyRequest()).hashCode());
	}

	@Test
	@DisplayName("each page of a paged request produces a distinct cache key")
	void pagesDoNotShareACacheKey() throws Exception {
		AggregateRequestDto firstPage = legacyRequest();
		PaginationDto first = new PaginationDto();
		first.setSize(100);
		first.setSort(new SortCriteria("Data.dateOfEntry", "desc"));
		firstPage.setPagination(first);

		AggregateRequestDto secondPage = legacyRequest();
		PaginationDto second = new PaginationDto();
		second.setSize(100);
		second.setSort(new SortCriteria("Data.dateOfEntry", "desc"));
		second.setSearchAfter(Arrays.asList(1735689600000L, "stock-42"));
		secondPage.setPagination(second);

		String firstJson = MAPPER.writeValueAsString(firstPage);
		String secondJson = MAPPER.writeValueAsString(secondPage);

		assertNotEquals(firstJson, secondJson);
		assertNotEquals(firstJson.hashCode(), secondJson.hashCode(),
				"if pages shared a cache key, page two would serve page one's rows");
	}

	@Test
	@DisplayName("differing filters produce a distinct cache key")
	void filtersAffectTheCacheKey() throws Exception {
		AggregateRequestDto returned = legacyRequest();
		returned.setTermFilters(Collections.singletonMap("Data.additionalDetails.stockEntryType.keyword", "RETURNED"));

		AggregateRequestDto issued = legacyRequest();
		issued.setTermFilters(Collections.singletonMap("Data.additionalDetails.stockEntryType.keyword", "ISSUED"));

		assertNotEquals(MAPPER.writeValueAsString(returned), MAPPER.writeValueAsString(issued));
	}

	@Test
	@DisplayName("the request round-trips from the JSON a caller would actually send")
	void deserialisesFromClientJson() throws Exception {
		String body = "{\"visualizationCode\":\"commodityFacilityStockBalance\","
				+ "\"termFilters\":{\"Data.additionalDetails.status.keyword\":\"IN_TRANSIT\"},"
				+ "\"rangeFilters\":[{\"field\":\"Data.dateOfEntry\",\"from\":1000,\"to\":2000}],"
				+ "\"pagination\":{\"size\":50,\"trackTotalHits\":true,"
				+ "\"sort\":{\"field\":\"Data.dateOfEntry\",\"order\":\"desc\"},"
				+ "\"searchAfter\":[1735689600000,\"stock-42\"]}}";

		AggregateRequestDto request = MAPPER.readValue(body, AggregateRequestDto.class);

		assertEquals(50, request.getPagination().getSize());
		assertTrue(request.getPagination().getTrackTotalHits());
		assertEquals("Data.dateOfEntry", request.getPagination().getSort().getField());
		assertEquals("desc", request.getPagination().getSort().resolvedOrder());
		assertEquals(2, request.getPagination().getSearchAfter().size());
		assertEquals("Data.dateOfEntry", request.getRangeFilters().get(0).getField());
		assertEquals("IN_TRANSIT", request.getTermFilters().get("Data.additionalDetails.status.keyword"));
	}
}
