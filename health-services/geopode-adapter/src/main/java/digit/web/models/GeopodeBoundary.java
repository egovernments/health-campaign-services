package digit.web.models;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * GeopodeBoundary
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GeopodeBoundary {
    @JsonProperty("tenantId")
    @NotBlank
    private String tenantId = null;

    @JsonProperty("ISOCode")
    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$", message = "ISOCode must be 3 uppercase letters")
    private String ISOCode = null;

}
