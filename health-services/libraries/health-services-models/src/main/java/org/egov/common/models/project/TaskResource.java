package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
* TaskResource
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskResource   {
    @JsonProperty("id")
    @Size(min=2,max=64)
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId = null;

    @JsonProperty("clientReferenceId")
    @Size(min = 2, max = 64)
    private String clientReferenceId = null;

    @JsonProperty("taskId")
    @Size(min = 2, max = 64)
    private String taskId = null;

    @JsonProperty("productVariantId")
    @NotNull
    @Size(min=2,max=64)
    private String productVariantId = null;

    @JsonProperty("quantity")
    @NotNull
    private Long quantity = null;

    @JsonProperty("isDelivered")
    @NotNull
    private Boolean isDelivered = null;

    @JsonProperty("deliveryComment")
    @Size(min=0,max=1000)
    private String deliveryComment = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

    /**
     * Additional fields that may be used for extending the information stored with each task.
     */
    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields = null;
}

