package org.egov.common.models.stock;

import java.util.List;
import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.core.EgovOfflineSearchModel;
import org.springframework.validation.annotation.Validated;

/**
 * StockReconciliationSearch
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReconciliationSearch extends EgovOfflineSearchModel {

    @JsonProperty("facilityId")
    private List<String> facilityId = null;

    @JsonProperty("productVariantId")
    private List<String> productVariantId = null;
}

