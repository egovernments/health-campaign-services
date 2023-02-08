package org.egov.stock.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

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
    private String id = null;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId = null;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId = null;

    @JsonProperty("facilityId")
    @NotNull
    private String facilityId = null;

    @JsonProperty("productVariantId")
    @NotNull
    private String productVariantId = null;

    @JsonProperty("quantity")
    @NotNull
    private Integer quantity = null;

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("referenceIdType")
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
    private String transactingPartyId = null;

    @JsonProperty("transactingPartyType")
    @NotNull
    private String transactingPartyType = null;

    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = null;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;
}

