package org.egov.processor.web.models.campaignManager;

import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CampaignResources {
	@JsonProperty("filestoreId")
	@NotNull
    @Size(min = 1, max = 128)
	private String filestoreId = null;

	@JsonProperty("type")
	@NotNull
    @Size(min = 1, max = 128)
	private String type = null;

	@JsonProperty("filename")
	@NotNull
    @Size(min = 1, max = 128)
	private String filename = null;
	
	@JsonProperty("createResourceId")
	@Valid
	private String createResourceId=null;
	
	
}
