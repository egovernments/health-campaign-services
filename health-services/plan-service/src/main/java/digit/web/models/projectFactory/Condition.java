package digit.web.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Condition
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Condition {

    @JsonProperty("value")
    private String value;

    @JsonProperty("operator")
    private String operator;

    @JsonProperty("attribute")
    private String attribute;
}