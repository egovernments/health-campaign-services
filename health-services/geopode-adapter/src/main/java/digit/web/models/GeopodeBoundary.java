package digit.web.models;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

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
    @NotNull
    private String tenantId = null;

    @JsonProperty("ISOCode")
    @NotNull
    private String ISOCode = null;

}
