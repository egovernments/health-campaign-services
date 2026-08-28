package com.tarento.analytics.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.analytics.utils.ResponseRecorder;

import java.util.List;
import java.util.Map;

public class AggregateRequestDto {
	
	private String requestId;
	private String visualizationType; 
	private String visualizationCode;
	private String moduleLevel; 
	private String queryType;
	private Map<String, Object> filters; 
	private Map<String, Object> esFilters; 
	private Map<String, Object> aggregationFactors; 
	private RequestDate requestDate; 
	private String interval;
	private ObjectNode chartNode;
	private ResponseRecorder responseRecorder;

	/*
	 * The fields below are omitted from JSON when unset. That is deliberate rather than
	 * cosmetic: the controller derives the response cache key by serializing this object, so
	 * including them as nulls would change the key of every pre-existing request and silently
	 * discard the cache for callers that never asked for any of this.
	 */

	/**
	 * Opt-in paging/sorting for raw-document queries. Null leaves the configured query untouched.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private PaginationDto pagination;

	/**
	 * Exact-match conditions applied straight to the query, keyed by Elasticsearch field path
	 * (e.g. {@code Data.additionalDetails.status.keyword}). A value may be a scalar or a list.
	 *
	 * <p>Unlike {@link #filters}, these need no per-tenant {@code requestQueryMap} entry, so a tenant
	 * whose configuration lacks a mapping is not silently unable to filter. Also unlike
	 * {@link #esFilters}, which the query builder overwrites on every call, these survive to the query.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Map<String, Object> termFilters;

	/** Inclusive range conditions applied straight to the query. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<RangeFilter> rangeFilters;

	/**
	 * Ask the service to compute per-facility, per-product stock totals rather than returning every
	 * transaction for the browser to add up.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private StockSummaryRequest stockSummary;

	@JsonIgnore
	private int hashKey;
	
	public AggregateRequestDto() {} 
	public AggregateRequestDto(AggregateRequestDtoV3 requestDtoV3, String visualizationType, String visualizationCode) { 
		this.visualizationCode = visualizationCode; 
		this.visualizationType = visualizationType; 
		this.moduleLevel = requestDtoV3.getModuleLevel(); 
		this.queryType = requestDtoV3.getQueryType(); 
		this.filters = requestDtoV3.getFilters(); 
		this.esFilters = requestDtoV3.getEsFilters(); 
		this.aggregationFactors = requestDtoV3.getAggregationFactors(); 
		this.requestDate = requestDtoV3.getRequestDate(); 
		this.interval = requestDtoV3.getInterval(); 
		this.chartNode = requestDtoV3.getChartNode(); 
		this.requestId= requestDtoV3.getRequestId();
	}


	public String getRequestId() {
		return requestId;
	}
	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}
	public ObjectNode getChartNode() {
		return chartNode;
	}

	public void setChartNode(ObjectNode chartNode) {
		this.chartNode = chartNode;
	}

	public String getModuleLevel() {
		return moduleLevel;
	}
	public void setModuleLevel(String moduleLevel) {
		this.moduleLevel = moduleLevel;
	}
	public Map<String, Object> getEsFilters() {
		return esFilters;
	}
	public void setEsFilters(Map<String, Object> esFilters) {
		this.esFilters = esFilters;
	}
	public String getVisualizationCode() {
		return visualizationCode;
	}
	public void setVisualizationCode(String visualizationCode) {
		this.visualizationCode = visualizationCode;
	}
	public String getVisualizationType() {
		return visualizationType;
	}
	public void setVisualizationType(String visualizationType) {
		this.visualizationType = visualizationType;
	}
	public String getQueryType() {
		return queryType;
	}
	public void setQueryType(String queryType) {
		this.queryType = queryType;
	}
	public Map<String, Object> getFilters() {
		return filters;
	}
	public void setFilters(Map<String, Object> filters) {
		this.filters = filters;
	}
	public Map<String, Object> getAggregationFactors() {
		return aggregationFactors;
	}
	public void setAggregationFactors(Map<String, Object> aggregationFactors) {
		this.aggregationFactors = aggregationFactors;
	}
	public RequestDate getRequestDate() {
		return requestDate;
	}
	public void setRequestDate(RequestDate requestDate) {
		this.requestDate = requestDate;
	}
	public String getInterval() {
		return interval;
	}
	public void setInterval(String interval) {
		this.interval = interval;
	}
	public ResponseRecorder getResponseRecorder() {
		return responseRecorder;
	}
	public void setResponseRecorder(ResponseRecorder responseRecorder) {
		this.responseRecorder = responseRecorder;
	}
	public PaginationDto getPagination() {
		return pagination;
	}
	public void setPagination(PaginationDto pagination) {
		this.pagination = pagination;
	}
	public Map<String, Object> getTermFilters() {
		return termFilters;
	}
	public void setTermFilters(Map<String, Object> termFilters) {
		this.termFilters = termFilters;
	}
	public List<RangeFilter> getRangeFilters() {
		return rangeFilters;
	}
	public void setRangeFilters(List<RangeFilter> rangeFilters) {
		this.rangeFilters = rangeFilters;
	}
	public StockSummaryRequest getStockSummary() {
		return stockSummary;
	}
	public void setStockSummary(StockSummaryRequest stockSummary) {
		this.stockSummary = stockSummary;
	}
	public int getHashKey() { return hashKey; }
	public void setHashKey(int hashKey) {
		this.hashKey = hashKey;
	}
	
	

}
