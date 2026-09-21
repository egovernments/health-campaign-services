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
public class Boundary {

	@JsonProperty("code")
	@NotNull
    @Size(min = 1, max = 64)
    private String code;
	
	@JsonProperty("type")
	@NotNull
    @Size(min = 1, max = 128)
    private String type;
	
	@JsonProperty("isRoot")
    private boolean isRoot;
	
	@JsonProperty("parent")
	@Valid
    private String parent;
	
	@JsonProperty("includeAllChildren")
    private boolean includeAllChildren;
}
