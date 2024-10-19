package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

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
    private List<String> role = null;

    @JsonProperty("jurisdiction")
    @Valid
    private List<String> jurisdiction = null;

    @JsonProperty("active")
    private Boolean active = null;
}