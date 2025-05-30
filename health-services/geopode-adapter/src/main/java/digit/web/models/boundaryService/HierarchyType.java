package digit.web.models.boundaryService;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * HierarchyType
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HierarchyType {

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
