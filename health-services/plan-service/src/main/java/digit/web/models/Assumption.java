package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

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
    @DecimalMax(value = "1000.00", inclusive = true, message = "The assumption value must not exceed 4 digits in total, including up to 2 decimal places.")
    @Digits(integer = 4, fraction = 2, message = "The Assumption value must have up to 10 digits and up to 2 decimal points")
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
