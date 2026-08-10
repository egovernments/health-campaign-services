package org.egov.common.models.referralmanagement.beneficiarydownsync;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.egov.common.contract.request.RequestInfo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownsyncRequest {

    @JsonProperty("RequestInfo")
	private RequestInfo requestInfo;
    
    @JsonProperty("DownsyncCriteria")
    // The controller dereferences getDownsyncCriteria() immediately with no null guard, so a
    // request omitting this produced an NPE (HTTP 500) rather than a 400. @NotNull turns that into
    // a clean rejection; @Valid descends into the object so DownsyncCriteria's own @NotNull on
    // locality/projectId/tenantId can fire — without it those three constraints were dead.
    @NotNull
    @Valid
    private DownsyncCriteria downsyncCriteria;
}
