package org.egov.stock.web.models;

import java.util.List;
import javax.validation.Valid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.data.query.annotations.Table;
import org.springframework.validation.annotation.Validated;

/**
 * StockReconciliationSearch
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-08T11:49:06.320+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Table(name = "stock_reconciliation_log")
@Deprecated
public class StockReconciliationSearch {
    @JsonProperty("id")
    @Valid
    private List<String> id = null;

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId = null;

    @JsonProperty("facilityId")
    private List<String> facilityId = null;

    @JsonProperty("productVariantId")
    private List<String> productVariantId = null;
}

