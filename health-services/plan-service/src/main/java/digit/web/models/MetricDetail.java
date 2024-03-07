package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricDetail {

    @JsonProperty("value")
    private BigDecimal metricValue = null;

    @JsonProperty("comparator")
    private String metricComparator = null;

    @JsonProperty("unit")
    private String metricUnit = null;

}
