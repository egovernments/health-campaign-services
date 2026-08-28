package com.tarento.analytics.dto;

import java.util.List;

/**
 * Opt-in paging controls for raw-document queries.
 *
 * <p>Paging is continuation-based rather than offset-based on purpose: Elasticsearch applies its
 * result-window limit to {@code from + size}, so page-number paging hits the same 10,000 wall the
 * unpaged query does. {@code searchAfter} carries the previous page's last sort values instead,
 * which has no such ceiling.
 *
 * <p>Every field is optional. A request that omits this object entirely leaves the emitted query
 * exactly as the chart configuration defined it.
 */
public class PaginationDto {

	/** Page size. Overrides the size baked into the chart configuration's query when set. */
	private Integer size;

	/**
	 * The field to order by. Required for {@link #searchAfter} to be meaningful, because a
	 * continuation token is only interpretable against the sort that produced it.
	 *
	 * <p>One key, not a list: a unique tiebreaker is appended automatically, and which timestamp a
	 * list would let each caller pick is a decision to be taken once rather than per request.
	 */
	private SortCriteria sort;

	/**
	 * Continuation token: the {@code sort} values of the last document of the previous page, echoed
	 * back from {@code completeness.nextPageToken}.
	 */
	private List<Object> searchAfter;

	/**
	 * Ask Elasticsearch for an exact match count instead of one capped at 10,000.
	 *
	 * <p>Costly on large indices, so it is opt-in. Leaving it off still detects truncation — a
	 * capped total is reported with relation {@code gte}, which is itself the signal.
	 */
	private Boolean trackTotalHits;

	public Integer getSize() {
		return size;
	}

	public void setSize(Integer size) {
		this.size = size;
	}

	public SortCriteria getSort() {
		return sort;
	}

	public void setSort(SortCriteria sort) {
		this.sort = sort;
	}

	public List<Object> getSearchAfter() {
		return searchAfter;
	}

	public void setSearchAfter(List<Object> searchAfter) {
		this.searchAfter = searchAfter;
	}

	public Boolean getTrackTotalHits() {
		return trackTotalHits;
	}

	public void setTrackTotalHits(Boolean trackTotalHits) {
		this.trackTotalHits = trackTotalHits;
	}

	public boolean hasSort() {
		return sort != null && sort.getField() != null && !sort.getField().trim().isEmpty();
	}

	public boolean hasSearchAfter() {
		return searchAfter != null && !searchAfter.isEmpty();
	}

	/**
	 * @return true when nothing on this object would alter the configured query.
	 */
	public boolean isEmpty() {
		return size == null && !hasSort() && !hasSearchAfter() && trackTotalHits == null;
	}
}
