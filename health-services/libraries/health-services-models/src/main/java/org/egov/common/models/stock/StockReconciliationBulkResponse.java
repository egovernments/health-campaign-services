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
 * StockReconciliationResponse
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReconciliationBulkResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.response.ResponseInfo responseInfo = null;

    @JsonProperty("StockReconciliation")
    @NotNull
    @Valid
    private List<StockReconciliation> stockReconciliation = new ArrayList<>();

    public StockReconciliationBulkResponse addStockReconciliationItem(StockReconciliation stockReconciliationItem) {
        this.stockReconciliation.add(stockReconciliationItem);
        return this;
    }
}

