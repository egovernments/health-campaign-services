package org.egov.processor.web.models.campaignManager;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

public class CycleData {

	@JsonProperty("cycleData")
	@Valid
    private List<Object> cycleData; // Change Object to the appropriate type
	
	@JsonProperty("cycleConfigureDate")
	@Valid
    private CycleConfigureDate cycleConfigureDate;
}
