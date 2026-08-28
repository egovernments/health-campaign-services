package com.tarento.analytics.dto;

/**
 * One sort clause for a paginated raw-document query.
 *
 * <p>{@code field} is an Elasticsearch field path as stored in the index, e.g.
 * {@code Data.dateOfEntry}. Sorting on an analyzed text field will be rejected by
 * Elasticsearch, so callers must supply a numeric/date field or a {@code .keyword} sub-field.
 */
public class SortCriteria {

	public static final String ORDER_ASC = "asc";
	public static final String ORDER_DESC = "desc";

	private String field;
	private String order;

	public SortCriteria() {
	}

	public SortCriteria(String field, String order) {
		this.field = field;
		this.order = order;
	}

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public String getOrder() {
		return order;
	}

	public void setOrder(String order) {
		this.order = order;
	}

	/**
	 * @return the requested order, defaulting to ascending when unset or unrecognised.
	 */
	public String resolvedOrder() {
		return ORDER_DESC.equalsIgnoreCase(order) ? ORDER_DESC : ORDER_ASC;
	}
}
