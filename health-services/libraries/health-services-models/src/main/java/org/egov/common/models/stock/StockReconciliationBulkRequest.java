package org.egov.common.models.stock;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * StockReconciliationRequest
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReconciliationBulkRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.request.RequestInfo requestInfo = null;

    @JsonProperty("StockReconciliation")
    @NotNull
    @Valid
    @Size(min = 1)
    private List<StockReconciliation> stockReconciliation = new ArrayList<>();


    public StockReconciliationBulkRequest addStockReconciliationItem(StockReconciliation stockReconciliationItem) {
        this.stockReconciliation.add(stockReconciliationItem);
        return this;
    }

}

