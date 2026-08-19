package org.egov.inbox.web.model.dss;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class InsightsWidget {

	@Valid
	@JsonProperty("name")
	private String name;

	@Valid
	@JsonProperty("value")
	private Object value;

	@Valid
	@JsonProperty("indicator")
	private String indicator;

	@Valid
	@JsonProperty("colorCode")
	private String colorCode;

}
