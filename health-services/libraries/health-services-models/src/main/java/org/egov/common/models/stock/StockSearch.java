package org.egov.common.models.stock;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * StockSearch
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockSearch {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId = null;

    @JsonProperty("facilityId")
    @Size(min=2, max=64)
    private String facilityId = null;

    @JsonProperty("productVariantId")
    private List<String> productVariantId = null;

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("wayBillNumber")
    private List<String> wayBillNumber = null;

    @JsonProperty("referenceIdType")
    @Size(min=2, max=64)
    private String referenceIdType = null;

    @JsonProperty("transactionType")
    @Valid
    private TransactionType transactionType = null;

    @JsonProperty("transactionReason")
    @Valid
    private TransactionReason transactionReason = null;

    @JsonProperty("transactingPartyId")
    @Size(min=2, max=64)
    private String transactingPartyId = null;

    @JsonProperty("transactingPartyType")
    private String transactingPartyType = null;
}

