package org.egov.common.models.stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * StockReconciliation
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-08T11:49:06.320+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReconciliation {
    @JsonProperty("id")
    @Size(min=2, max=64)
    private String id = null;

    @JsonProperty("clientReferenceId")
    @Size(min=2, max=64)
    private String clientReferenceId = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min=2, max=1000)
    private String tenantId = null;

    @JsonProperty("facilityId")
    @NotNull
    @Size(min=2, max=64)
    private String facilityId = null;

    @JsonProperty("productVariantId")
    @NotNull
    @Size(min=2, max=64)
    private String productVariantId = null;

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("referenceIdType")
    @Size(min=2, max=64)
    private String referenceIdType = null;

    @JsonProperty("physicalCount")
    private Integer physicalCount = null;

    @JsonProperty("calculatedCount")
    private Integer calculatedCount = null;

    @JsonProperty("commentsOnReconciliation")
    private String commentsOnReconciliation = null;

    @JsonProperty("dateOfReconciliation")
    private Integer dateOfReconciliation = null;

    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = null;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonIgnore
    private Boolean hasErrors = Boolean.FALSE;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;
}

