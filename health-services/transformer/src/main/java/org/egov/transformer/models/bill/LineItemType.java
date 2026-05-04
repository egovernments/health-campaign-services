package org.egov.transformer.models.bill;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Type of line item
 */
public enum LineItemType {
	
	PAYABLE("PAYABLE"),

	DEDUCTION("DEDUCTION");

	private String value;

	LineItemType(String value) {
		this.value = value;
	}

	@Override
	@JsonValue
	public String toString() {
		return String.valueOf(value);
	}

	@JsonCreator
	public static LineItemType fromValue(String text) {
		for (LineItemType b : LineItemType.values()) {
			if (String.valueOf(b.value).equalsIgnoreCase(text)) {
				return b;
			}
		}
		return null;
	}
}