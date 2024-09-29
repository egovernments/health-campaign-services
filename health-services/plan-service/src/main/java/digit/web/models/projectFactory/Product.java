package digit.web.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Product
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product {

    @JsonProperty("name")
    private String name;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("value")
    private String value;
}

