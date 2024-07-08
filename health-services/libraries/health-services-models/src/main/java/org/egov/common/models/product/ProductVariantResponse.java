package org.egov.common.models.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * ProductVariantResponse
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductVariantResponse {
    @JsonProperty("ResponseInfo")
    @NotNull

    @Valid


    private ResponseInfo responseInfo = null;

    @JsonProperty("ProductVariant")
    @NotNull

    @Valid


    private List<ProductVariant> productVariant = new ArrayList<>();


    public ProductVariantResponse addProductVariantItem(ProductVariant productVariantItem) {
        this.productVariant.add(productVariantItem);
        return this;
    }

}

