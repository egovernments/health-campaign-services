package org.egov.project.web.models;

import java.util.List;

import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.ErrorQueueContract;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataErrorDumpRequest {

	RequestInfo requestInfo;

	List<ErrorQueueContract> errorEntities;
}
