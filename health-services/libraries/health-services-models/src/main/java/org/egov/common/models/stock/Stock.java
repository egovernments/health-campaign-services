package org.egov.common.models.stock;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stock
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-08T11:49:06.320+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stock {
	
    @JsonProperty("id")
    @Size(min=2, max=64)
    private String id;

    @JsonProperty("clientReferenceId")
    @Size(min=2, max=64)
    private String clientReferenceId;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min=2, max=1000)
    private String tenantId;

    /* product fields */
    @JsonProperty("productVariantId")
    @NotNull
    @Size(min=2, max=64)
    private String productVariantId;

    @JsonProperty("quantity")
    @NotNull
    private Integer quantity;

    /* project id in-case of health */ 
    @JsonProperty("referenceId")
    private String referenceId;

    @JsonProperty("referenceIdType")
    @Size(min=2, max=64)
    private String referenceIdType;

    // transaction fields 
    @JsonProperty("transactionType")
    @NotNull
    @Valid
    private TransactionType transactionType;

    @JsonProperty("transactionReason")
    @Valid
    private TransactionReason transactionReason;

    @JsonProperty("senderId")
    @NotNull
    @Size(min=2, max=64)
    private String senderId;

    @JsonProperty("senderType")
    @NotNull
    @Size(min=2, max=64)
    private String senderType;
    
    @JsonProperty("receiverId")
    @NotNull
    @Size(min=2, max=64)
    private String receiverId;

    @JsonProperty("receiverType")
    @NotNull
    @Size(min=2, max=64)
    private String receiverType;

    @JsonProperty("wayBillNumber")
    @Size(min = 2, max = 200)
    private String wayBillNumber;

    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields;

    @JsonProperty("isDeleted")
    @Default
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    private Integer rowVersion;

    @JsonIgnore
    @Default
    private Boolean hasErrors = Boolean.FALSE;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;

    @JsonProperty("dateOfEntry")
    private Long dateOfEntry;

    @JsonProperty("clientAuditDetails")
    @Valid
    private AuditDetails clientAuditDetails;
}

