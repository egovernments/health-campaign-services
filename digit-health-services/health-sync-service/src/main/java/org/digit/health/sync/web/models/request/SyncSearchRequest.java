package org.digit.health.sync.web.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.health.sync.web.models.ReferenceId;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.beans.factory.annotation.Required;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncSearchRequest {

    @JsonProperty("requestInfo")
    private RequestInfo requestInfo;

    @NotNull(message = "Tenant Id is required")
    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("syncId")
    private String syncId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("reference")
    private ReferenceId reference;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

}
