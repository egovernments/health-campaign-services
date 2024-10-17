package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

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
    @DecimalMin(value = "0.01", inclusive = true, message = "The Assumption value must be greater than 0")
    @DecimalMax(value = "9999999999.99", inclusive = true, message = "The assumption value must not exceed 10 digits in total, including up to 2 decimal places.")
    @Digits(integer = 10, fraction = 2, message = "The Assumption value must have up to 10 digits and up to 2 decimal points")
    private BigDecimal value = null;

    @JsonProperty("source")
    @NotNull(message = "Source cannot be null. Please specify a valid source.")
    private Source source = null;

    @JsonProperty("category")
    @NotNull
    @Size(min = 2, max = 64)
    private String category = null;

    @JsonProperty("active")
    @NotNull
    private Boolean active = true;

}
