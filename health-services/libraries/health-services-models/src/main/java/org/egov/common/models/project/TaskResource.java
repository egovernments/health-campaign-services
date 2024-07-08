package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.core.AdditionalFields;
import org.springframework.validation.annotation.Validated;

/**
* TaskResource
*/
@Validated


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
    private Double quantity = null;

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

