package org.egov.common.models.stock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.data.query.annotations.Table;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * StockSearch
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-08T11:49:06.320+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Table(name="stock")
public class StockSearch {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId = null;

    @JsonProperty("facilityId")
    @Size(min=2, max=64)
    private String facilityId = null;

    @JsonProperty("productVariantId")
    @Size(min=2, max=64)
    private String productVariantId = null;

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("wayBillNumber")
    private String wayBillNumber = null;

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

