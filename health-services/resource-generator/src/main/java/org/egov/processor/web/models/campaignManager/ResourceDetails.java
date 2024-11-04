package org.egov.processor.web.models.campaignManager;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResourceDetails {

    @JsonProperty("type")
    @NotNull
    @Size(min = 1, max = 64)
    private String type;

    @JsonProperty("hierarchyType")
    @NotNull
    @Size(min = 1, max = 64)
    private String hierarchyType;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId;

    @JsonProperty("fileStoreId")
    @NotNull
    private String fileStoreId;

    @JsonProperty("action")
    @Size(min = 1, max = 64)
    private String action;

    @JsonProperty("campaignId")
    private String campaignId;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

}
