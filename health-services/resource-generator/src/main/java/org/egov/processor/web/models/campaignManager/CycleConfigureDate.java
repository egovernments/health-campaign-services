package org.egov.processor.web.models.campaignManager;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

/** 
 * The cycle duration in days.
 * This specifies the duration of the cycle for deliveries.
 */
public class CycleConfigureDate {
	
	 /** 
     * The cycle duration in days.
     * This specifies the duration of the cycle for deliveries.
     */
	@JsonProperty("cycle")
	@Valid
    private int cycle;
	
	
	/**
     * The number of deliveries within the cycle.
     * This indicates how many deliveries occur during each cycle period.
     */
	@JsonProperty("deliveries")
	@Valid
    private int deliveries;
}
