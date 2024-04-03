package org.egov.common.models.product;

import java.util.List;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
* ProductVariantSearch
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductVariantSearch   {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("productId")
    private List<String> productId = null;

    @JsonProperty("sku")
    @Size(min = 0, max = 1000)
    private String sku = null;

    @JsonProperty("variation")
    @Size(min = 0, max = 1000)
    private String variation = null;
}

