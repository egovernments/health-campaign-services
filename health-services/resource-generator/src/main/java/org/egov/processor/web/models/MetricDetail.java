package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Arrays;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricDetail {

    @JsonProperty("value")
    @NotNull
    @DecimalMin(value = "0.01", inclusive = true, message = "Metric value must be greater than 0")
    @DecimalMax(value = "999.99", inclusive = true, message = "Metric value must be less than 1000")
    @Digits(integer = 3, fraction = 2, message = "Metric value must have up to 3 digits and up to 2 decimal points")
    private BigDecimal metricValue = null;

    @JsonProperty("comparator")
    @NotNull
    private MetricComparatorEnum metricComparator = null;

    @JsonProperty("unit")
    @NotNull
    @Size(min = 1, max = 128)
    private String metricUnit = null;

    public enum MetricComparatorEnum {
        GREATER_THAN(">"),

        LESS_THAN("<"),

        GREATER_THAN_OR_EQUAL_TO(">="),

        LESS_THAN_OR_EQUAL_TO("<="),

        EQUAL("=");

        private final String symbol;

        MetricComparatorEnum(String symbol) {
            this.symbol = symbol;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(symbol);
        }

        @JsonCreator
        public static MetricComparatorEnum fromValue(String text) {
            return Arrays.stream(MetricComparatorEnum.values())
                    .filter(b -> String.valueOf(b.symbol).equals(text))
                    .findFirst()
                    .orElse(null); // Return null if no matching enum value is found
        }
    }

}
