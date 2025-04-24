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
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2025-04-24T14:43:05.749340509+05:30[Asia/Kolkata]")
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
