package org.egov.common.models.referralmanagement.beneficiarydownsync;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownsyncRequest {

    @JsonProperty("RequestInfo")
	private RequestInfo requestInfo;
    
    @JsonProperty("DownsyncCriteria")
    private DownsyncCriteria downsyncCriteria;
}
