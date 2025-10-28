package digit.web.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DeliveryRule
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeliveryRule {

    @JsonProperty("endDate")
    private Long endDate;

    @JsonProperty("products")
    @Valid
    private List<Product> products;

    @JsonProperty("startDate")
    private Long startDate;

    @JsonProperty("conditions")
    @Valid
    private List<Condition> conditions;

    @JsonProperty("cycleNumber")
    private Integer cycleNumber;

    @JsonProperty("deliveryNumber")
    private Integer deliveryNumber;

    @JsonProperty("deliveryRuleNumber")
    private Integer deliveryRuleNumber;
}