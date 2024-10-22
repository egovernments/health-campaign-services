package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;


public enum BeneficiaryType {

		HOUSEHOLD("HOUSEHOLD"),

		INDIVIDUAL("INDIVIDUAL");

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
			for(BeneficiaryType a:BeneficiaryType.values()){
				if(String.valueOf(a.value).equals(text)){
					return a;
				}
			}

			return null;
		}
}
