package org.egov.processor.web.models.campaignManager;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

public class CycleConfigureDate {
	
	@JsonProperty("cycle")
	@Valid
    private int cycle;
	
	@JsonProperty("deliveries")
	@Valid
    private int deliveries;
}
