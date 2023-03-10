package org.egov.transformer.models.upstream;

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
 * Stock
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-08T11:49:06.320+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {
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

    @JsonProperty("quantity")
    @NotNull
    private Integer quantity = null;

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("referenceIdType")
    @Size(min=2, max=64)
    private String referenceIdType = null;

    @JsonProperty("transactionType")
    @NotNull
    @Valid
    private TransactionType transactionType = null;

    @JsonProperty("transactionReason")
    @Valid
    private TransactionReason transactionReason = null;

    @JsonProperty("transactingPartyId")
    @NotNull
    @Size(min=2, max=64)
    private String transactingPartyId = null;

    @JsonProperty("transactingPartyType")
    @NotNull
    @Size(min=2, max=64)
    private String transactingPartyType = null;

    @JsonProperty("wayBillNumber")
    @Size(min = 2, max = 200)
    private String wayBillNumber = null;

    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonIgnore
    private Boolean hasErrors = Boolean.FALSE;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;
}

