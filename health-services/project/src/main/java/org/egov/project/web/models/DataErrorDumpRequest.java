package org.egov.project.web.models;

import javax.validation.constraints.NotNull;

import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.ErrorQueueContract;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataErrorDumpRequest {

	@NotNull
	@JsonProperty("RequestInfo")
	RequestInfo requestInfo;

	@NotNull
	@JsonProperty("ErrorQueueContract")
	ErrorQueueContract errorQueueContract;
}
