package org.egov.referralmanagement.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownsyncJobSearchResponse {
    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo;
    private DownsyncJobDetail job;
}
