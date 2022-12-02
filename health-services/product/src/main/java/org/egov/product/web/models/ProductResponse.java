package org.egov.product.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * ProductResponse
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T16:45:24.641+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

