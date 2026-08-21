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
public class AdditionalDetails {
	
	@JsonProperty("key")
	@Valid
    private int key;
	
	@JsonProperty("cycleData")
	@Valid
    private CycleData cycleData;
	
	@JsonProperty("beneficiaryType")
	@Valid
    private String beneficiaryType;

}
