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
 * Represents a bulk response for stock items, containing response metadata and a list of stock entries.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockBulkResponse {

    /**
     * Metadata about the API response, including details such as request status and information.
     */
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.response.ResponseInfo responseInfo = null;

    /**
     * List of stock items returned in the response.
     */
    @JsonProperty("Stock")
    @NotNull
    @Valid
    private List<Stock> stock = new ArrayList<>();

    /**
     * Total number of stock items in the response, defaults to 0 if not specified.
     */
    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    /**
     * Adds a single stock item to the list of stock and returns the updated response.
     *
     * @param stockItem The stock item to add to the list.
     * @return The updated StockBulkResponse instance.
     */
    public StockBulkResponse addStockItem(Stock stockItem) {
        this.stock.add(stockItem);
        return this;
    }
}


