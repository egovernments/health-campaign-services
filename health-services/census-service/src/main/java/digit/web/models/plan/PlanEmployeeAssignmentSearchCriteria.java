package digit.web.models.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * PlanEmployeeAssignmentSearchCriteria
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanEmployeeAssignmentSearchCriteria {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    @Size(min = 2, max = 100)
    @NotNull
    private String tenantId = null;

    @JsonProperty("employeeId")
    private String employeeId = null;

    @JsonProperty("planConfigurationId")
    @Size(max = 64)
    private String planConfigurationId = null;

    @JsonProperty("role")
    @Size(min = 2, max = 64)
    private List<String> role = null;

    @JsonProperty("jurisdiction")
    @Valid
    private List<String> jurisdiction = null;

    @JsonProperty("active")
    private Boolean active = null;
}
