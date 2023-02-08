package org.egov.stock.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * StockResponse
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-08T11:49:06.320+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.response.ResponseInfo responseInfo = null;

    @JsonProperty("Stock")
    @NotNull
    @Valid
    private List<Stock> stock = new ArrayList<>();


    public StockResponse addStockItem(Stock stockItem) {
        this.stock.add(stockItem);
        return this;
    }
}

