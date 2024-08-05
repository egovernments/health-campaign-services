package org.egov.processor.web.models.campaignManager;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

public class CampaignCondition {
	
	
	@JsonProperty("value")
	@Valid
    private Object value; // Change Object to the appropriate type
	
	@JsonProperty("operator")
	@Valid
    private Object operator; // Change Object to the appropriate type
	
	@JsonProperty("attribute")
	@Valid
    private String attribute;
}
