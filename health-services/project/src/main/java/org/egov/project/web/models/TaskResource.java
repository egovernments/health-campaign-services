package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class TaskResource   {
    @JsonProperty("id")
    @Size(min=2,max=64)
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId = null;

    @JsonProperty("productVariantId")
    @NotNull
    @Size(min=2,max=64)
    private String productVariantId = null;

    @JsonProperty("quantity")
    @NotNull
    private String quantity = null;

    @JsonProperty("isDelivered")
    @NotNull
    private Boolean isDelivered = null;

    @JsonProperty("deliveryComment")
    @Size(min=0,max=1000)
    private String deliveryComment = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

}

