package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BeneficiaryType {

	HOUSEHOLD("HOUSEHOLD"),

	INDIVIDUAL("INDIVIDUAL"),
	
	SPAQ_1("SPAQ-1"),
	
	SPAQ_2("SPAQ-2"),
	
	THREE_ELEVEN_MONTH("3-11MONTH"),
	
	TWELVE_FIFTY_NINE_MONTH("12-59MONTH"),
	
	SIX_ELEVEN_MONTH("6-11MONTH"),
	
	PRODUCT("PRODUCT"),
	
	VAS_RED("VAS-RED"),
	
	VAS_BLUE("VAS-BLUE");

	private String value;

	BeneficiaryType(String value) {
		this.value = value;
	}

	@Override
	@JsonValue
	public String toString() {
		return String.valueOf(value);
	}

	@JsonCreator
	public static BeneficiaryType fromValue(String text) {
		for (BeneficiaryType a : BeneficiaryType.values()) {
			if (String.valueOf(a.value).equalsIgnoreCase(text)) {
				return a;
			}
		}

		return null;
	}
}