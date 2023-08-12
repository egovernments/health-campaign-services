package org.egov.errorhandler.models;

import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.ErrorDetail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DumpRequest {
    RequestInfo requestInfo;

    ErrorDetail errorDetail;
}
