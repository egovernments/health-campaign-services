package org.egov.stock.web.models;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.data.query.annotations.Table;
import org.egov.common.models.stock.TransactionReason;
import org.egov.common.models.stock.TransactionType;
import org.springframework.validation.annotation.Validated;

/**
 * StockSearch
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-08T11:49:06.320+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@Table(name="stock")
@Deprecated
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

