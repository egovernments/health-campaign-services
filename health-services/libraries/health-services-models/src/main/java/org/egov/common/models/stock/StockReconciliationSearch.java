package org.egov.common.models.stock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.List;

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
public class StockReconciliationSearch {
    @JsonProperty("id")
    @Valid
    private List<String> id = null;

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId = null;

    @JsonProperty("facilityId")
    @Size(min=2, max=64)
    private String facilityId = null;

    @JsonProperty("productVariantId")
    @Size(min=2, max=64)
    private String productVariantId = null;
}

