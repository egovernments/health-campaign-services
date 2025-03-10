package org.egov.common.models.product;

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
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

/**
 * ProductResponse
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductResponse {
    @JsonProperty("ResponseInfo")
    @NotNull

    @Valid


    private ResponseInfo responseInfo = null;

    @JsonProperty("Product")
    @NotNull

    @Valid


    private List<Product> product = new ArrayList<>();


    public ProductResponse addProductItem(Product productItem) {
        this.product.add(productItem);
        return this;
    }

}

