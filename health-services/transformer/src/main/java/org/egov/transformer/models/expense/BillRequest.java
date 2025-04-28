package org.egov.transformer.models.expense;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;


import javax.validation.Valid;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class BillRequest {
    @JsonProperty("RequestInfo")
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("bill")
    @Valid
    private Bill bill;

    @JsonProperty("workflow")
    private Workflow workflow;
}
