package org.egov.product.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * ProductVariantRequest
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T16:45:24.641+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequest {
    @JsonProperty("RequestInfo")
    @NotNull

    @Valid


    private RequestInfo requestInfo = null;

    @JsonProperty("ProductVariant")
    @NotNull

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

