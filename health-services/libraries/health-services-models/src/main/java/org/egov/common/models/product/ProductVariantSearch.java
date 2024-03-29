package org.egov.common.models.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Size;
import java.util.List;

/**
* ProductVariantSearch
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T16:45:24.641+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
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

