package org.egov.common.models.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.springframework.validation.annotation.Validated;

/**
 * Stock
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stock extends EgovOfflineModel {

    /* product fields */
    @JsonProperty("productVariantId")
    @NotNull
    @Size(min=2, max=64)
    private String productVariantId;

    @JsonProperty("quantity")
    @NotNull
    @Min(value = 1, message = "Minimum value cannot be less than 1")
    @Max(value = Integer.MAX_VALUE, message = "Value exceeds maximum allowable limit")
    private Integer quantity;

    /* project id in-case of health */ 
    @JsonProperty("referenceId")
    private String referenceId;

    @JsonProperty("referenceIdType")
    @NotNull(message = "referenceIdType must be PROJECT or OTHER")
    @Valid
    private ReferenceIdType referenceIdType;

    // transaction fields 
    @JsonProperty("transactionType")
    @NotNull(message = "transactionType must be either RECEIVED or DISPATCHED")
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
    @NotNull(message = "Sender Type can be either WAREHOUSE or STAFF")
    @Valid
    private SenderReceiverType senderType;
    
    @JsonProperty("receiverId")
    @NotNull
    @Size(min=2, max=64)
    private String receiverId;

    @JsonProperty("receiverType")
    @NotNull(message = "Receiver Type can be either WAREHOUSE or STAFF")
    @Valid
    private SenderReceiverType receiverType;

    @JsonProperty("wayBillNumber")
    @Size(max = 200)
    private String wayBillNumber;

    //TODO remove this
    @JsonProperty("isDeleted")
    @Default
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("dateOfEntry")
    private Long dateOfEntry;

}

