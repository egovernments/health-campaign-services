package org.egov.common.models.stock;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * Represents a bulk response for stock reconciliation, containing response metadata and a list of stock reconciliation entries.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReconciliationBulkResponse {

    /**
     * Metadata about the API response, including details such as request status and information.
     */
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.response.ResponseInfo responseInfo = null;

    /**
     * List of stock reconciliation items returned in the response.
     */
    @JsonProperty("StockReconciliation")
    @NotNull
    @Valid
    private List<StockReconciliation> stockReconciliation = new ArrayList<>();

    /**
     * Total number of stock reconciliation items in the response, defaults to 0 if not specified.
     */
    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    /**
     * Adds a single stock reconciliation item to the list and returns the updated response.
     *
     * @param stockReconciliationItem The stock reconciliation item to add to the list.
     * @return The updated StockReconciliationBulkResponse instance.
     */
    public StockReconciliationBulkResponse addStockReconciliationItem(StockReconciliation stockReconciliationItem) {
        this.stockReconciliation.add(stockReconciliationItem);
        return this;
    }
}
