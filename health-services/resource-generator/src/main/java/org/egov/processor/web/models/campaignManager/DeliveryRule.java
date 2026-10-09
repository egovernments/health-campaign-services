package org.egov.processor.web.models.campaignManager;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

public class DeliveryRule {
	
	@JsonProperty("products")
	@Valid
    private List<Object> products; // Change Object to the appropriate type
	
	@JsonProperty("conditions")
	@Valid
    private List<CampaignCondition> conditions;
	
	@JsonProperty("cycleNumber")
	@Valid
    private int cycleNumber;
	
	@JsonProperty("deliveryNumber")
	@Valid
    private int deliveryNumber;
	
	@JsonProperty("deliveryRuleNumber")
	@Valid
    private int deliveryRuleNumber;
	
	@JsonProperty("endDate")
	@Valid
    private long endDate;
	
	@JsonProperty("startDate")
	@Valid
    private long startDate;
}
