package org.egov.product.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.data.query.annotations.Table;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
* ProductVariantSearch
*/
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T16:45:24.641+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Table(name="product_variant")
public class ProductVariantSearch   {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("productId")
    @Size(min = 2, max = 64)
    private String productId = null;

    @JsonProperty("sku")
    @Size(min = 0, max = 1000)
    private String sku = null;

    @JsonProperty("variation")
    @Size(min = 0, max = 1000)
    private String variation = null;
}

