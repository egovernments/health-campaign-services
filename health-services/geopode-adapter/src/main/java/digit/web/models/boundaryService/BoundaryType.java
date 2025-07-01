package digit.web.models.boundaryService;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * BoundaryType
 */
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-10-16T17:02:11.361704+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryType {

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("code")
    @NotNull
    private String code = null;

    @JsonProperty("active")
    private Boolean active = null;


}
