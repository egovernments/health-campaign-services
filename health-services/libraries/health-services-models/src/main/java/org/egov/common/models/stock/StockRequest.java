package org.egov.common.models.stock;

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
 * StockRequest
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.request.RequestInfo requestInfo = null;

    @JsonProperty("Stock")
    @NotNull
    @Valid
    private Stock stock = null;
}

