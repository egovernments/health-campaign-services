package org.egov.stock.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;

/**
 * StockReconciliationSearch
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-08T11:49:06.320+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReconciliationSearch {
    @JsonProperty("id")
    @Valid
    private IdForSearch id = null;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId = null;

    @JsonProperty("facilityId")
    private String facilityId = null;

    @JsonProperty("productVariantId")
    private String productVariantId = null;
}

