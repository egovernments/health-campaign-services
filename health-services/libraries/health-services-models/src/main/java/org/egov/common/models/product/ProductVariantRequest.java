package org.egov.common.models.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * ProductVariantRequest
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductVariantRequest {

    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("ProductVariant")
    @NotNull
    @Size(min = 1)
    @Valid
    private List<ProductVariant> productVariant = new ArrayList<>();

    @JsonProperty("apiOperation")
    @Valid
    private ApiOperation apiOperation = null;

    public ProductVariantRequest addProductVariantItem(ProductVariant productVariantItem) {
        this.productVariant.add(productVariantItem);
        return this;
    }
}

