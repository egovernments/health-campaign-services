package org.egov.referralmanagement.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownsyncJobSearchRequest {
    @JsonProperty("RequestInfo")
    private RequestInfo requestInfo;

    @NotBlank(message = "jobId is required")
    private String jobId;
}
