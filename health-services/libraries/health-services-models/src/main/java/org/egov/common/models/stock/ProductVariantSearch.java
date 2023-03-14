package org.egov.common.models.stock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.data.query.annotations.Table;

import javax.validation.constraints.Size;
import java.util.List;

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
