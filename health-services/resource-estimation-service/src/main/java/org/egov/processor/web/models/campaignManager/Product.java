package org.egov.processor.web.models.campaignManager;

import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product {

	@JsonProperty("name")
	@Valid
	private String name;

	@JsonProperty("count")
	@Valid
	private int count;
	
	@JsonProperty("value")
	@Valid
	private String value;
}
