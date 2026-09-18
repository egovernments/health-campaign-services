package org.egov.inbox.web.model.dss;

import java.util.Map;

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
public class AggregateRequestDto {

	@Valid
	@JsonProperty("visualizationType")
	private String visualizationType;

	@Valid
	@JsonProperty("moduleLevel")
	private String moduleLevel;

	@JsonProperty("requestDate")
	private RequestDate requestDate;

	@Valid
	@JsonProperty("visualizationCode")
	private String visualizationCode;

	@Valid
	@JsonProperty("filters")
	private Map<String, Object> filters;

}
