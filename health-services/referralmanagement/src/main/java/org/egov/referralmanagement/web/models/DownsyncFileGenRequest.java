package org.egov.referralmanagement.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownsyncFileGenRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    private RequestInfo requestInfo;

    @NotBlank
    private String tenantId;

    private String rootProjectId;

    private boolean forceRefresh = false;
}
