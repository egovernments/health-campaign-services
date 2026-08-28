package com.tarento.analytics.dto;

import org.egov.tracer.model.CustomException;

/**
 * Opt-in request for per-facility, per-product stock totals computed by the service.
 *
 * <p>Without this the same figures are produced by pulling every transaction into the browser and
 * looping over them, which is only correct while the whole campaign fits in one response.
 *
 * <p>Both limits bound how many buckets Elasticsearch is asked to build, and their product is
 * additionally bounded by the aggregation's bucket budget (see
 * {@code StockSummaryAggregation.MAX_BUCKET_BUDGET}) so that a request can never trip the
 * cluster's bucket breaker — that failure is swallowed upstream and would surface as a silently
 * empty response, the exact defect this feature exists to remove. The defaults sit inside the
 * budget; a caller raising one limit may need to lower the other. Whether the facility list
 * should instead come from the campaign hierarchy is an open question; until it is settled the
 * limit is the caller's, and the response reports when it was hit rather than quietly truncating.
 */
public class StockSummaryRequest {

	private static final int DEFAULT_FACILITY_LIMIT = 250;
	private static final int DEFAULT_PRODUCT_LIMIT = 15;
	private static final int MAX_LIMIT = 10000;

	/** Maximum number of facilities to return totals for. */
	private Integer facilityLimit;

	/** Maximum number of products per facility. */
	private Integer productLimit;

	/**
	 * Which facilities appear as rows. Null means every facility the filters touch, which is the
	 * honest default when the caller has not said whose summary this is.
	 */
	private FacilityScope facilityScope;

	public Integer getFacilityLimit() {
		return facilityLimit;
	}

	public void setFacilityLimit(Integer facilityLimit) {
		this.facilityLimit = facilityLimit;
	}

	public Integer getProductLimit() {
		return productLimit;
	}

	public void setProductLimit(Integer productLimit) {
		this.productLimit = productLimit;
	}

	public FacilityScope getFacilityScope() {
		return facilityScope;
	}

	public void setFacilityScope(FacilityScope facilityScope) {
		this.facilityScope = facilityScope;
	}

	public int resolvedFacilityLimit() {
		return clamp(facilityLimit, DEFAULT_FACILITY_LIMIT);
	}

	public int resolvedProductLimit() {
		return clamp(productLimit, DEFAULT_PRODUCT_LIMIT);
	}

	private int clamp(Integer requested, int fallback) {
		if (requested == null) {
			return fallback;
		}
		if (requested <= 0) {
			throw new CustomException("INVALID_STOCK_SUMMARY", "stockSummary limits must be greater than zero");
		}
		return Math.min(requested, MAX_LIMIT);
	}
}
