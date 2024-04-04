package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * Assumption
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Assumption {
    @JsonProperty("id")
    @Valid
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("key")
    @NotNull
    @Size(min = 1, max = 256)
    private String key = null;

    @JsonProperty("value")
    @NotNull
    @Valid
    @DecimalMin(value = "0.01", inclusive = true, message = "Assumption value must be greater than 0")
    @DecimalMax(value = "999.99", inclusive = true, message = "Assumption value must be less than 1000")
    @Digits(integer = 3, fraction = 2, message = "Value must have up to 3 digits and up to 2 decimal points")
    private BigDecimal value = null;

}
