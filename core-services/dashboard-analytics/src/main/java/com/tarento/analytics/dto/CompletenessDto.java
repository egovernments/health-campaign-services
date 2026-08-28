package com.tarento.analytics.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tells the caller whether the documents it just received are all of the documents that matched.
 *
 * <p>Without this, a truncated result is indistinguishable from a complete one: the response looks
 * identical either way, which is how two field incidents reached us as user reports rather than as
 * caught defects.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompletenessDto {

	/**
	 * Documents matching the query, present only when Elasticsearch reported an exact count.
	 * Absent when the count was capped — ask for {@code pagination.trackTotalHits} to get it.
	 */
	private Long matched;

	/** Documents actually returned in this response. */
	private Integer returned;

	/** True when more documents matched than were returned. */
	private Boolean truncated;

	/**
	 * Sort values of the last returned document, to be passed back as
	 * {@code pagination.searchAfter} to fetch the next page. Null when the query was not sorted or
	 * this is the final page.
	 */
	private List<Object> nextPageToken;

	public Long getMatched() {
		return matched;
	}

	public void setMatched(Long matched) {
		this.matched = matched;
	}

	public Integer getReturned() {
		return returned;
	}

	public void setReturned(Integer returned) {
		this.returned = returned;
	}

	public Boolean getTruncated() {
		return truncated;
	}

	public void setTruncated(Boolean truncated) {
		this.truncated = truncated;
	}

	public List<Object> getNextPageToken() {
		return nextPageToken;
	}

	public void setNextPageToken(List<Object> nextPageToken) {
		this.nextPageToken = nextPageToken;
	}
}
