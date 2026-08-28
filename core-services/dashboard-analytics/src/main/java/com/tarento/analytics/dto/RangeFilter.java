package com.tarento.analytics.dto;

/**
 * An inclusive range condition applied to a raw-document query, e.g. a date-of-entry window.
 *
 * <p>Either bound may be omitted to leave that side open. {@code field} is an Elasticsearch
 * field path as stored in the index, e.g. {@code Data.dateOfEntry}.
 *
 * <p>This exists because the chart-configuration date mechanism ({@code dateRefField}) is blank
 * for several charts, so the request date range is silently ignored for them. Supplying an
 * explicit range here filters server-side without altering the meaning of existing configuration.
 */
public class RangeFilter {

	private String field;
	private Object from;
	private Object to;

	public RangeFilter() {
	}

	public RangeFilter(String field, Object from, Object to) {
		this.field = field;
		this.from = from;
		this.to = to;
	}

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public Object getFrom() {
		return from;
	}

	public void setFrom(Object from) {
		this.from = from;
	}

	public Object getTo() {
		return to;
	}

	public void setTo(Object to) {
		this.to = to;
	}

	public boolean hasBound() {
		return from != null || to != null;
	}
}
