package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * Target
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Target {

    @JsonProperty("id")
    @Valid
    private String id = null;

    @JsonProperty("metric")
    private String metric = null;

    @JsonProperty("metricDetail")
    @Valid
    private MetricDetail metricDetail = null;

    @JsonProperty("activityCode")
    @Size(min = 2, max = 128)
    private String activityCode = null;

}
