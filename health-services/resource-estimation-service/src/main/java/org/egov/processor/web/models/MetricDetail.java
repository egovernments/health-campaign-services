package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricDetail {

    @JsonProperty("value")
    @NotNull
    private BigDecimal metricValue = null;

    @JsonProperty("comparator")
    @NotNull
    @Size(min = 1, max = 64)
    private String metricComparator = null;

    @JsonProperty("unit")
    @NotNull
    @Size(min = 1, max = 128)
    private String metricUnit = null;

}
