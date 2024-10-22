package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * PlanEmployeeAssignment
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanEmployeeAssignment {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId = null;

    @JsonProperty("planConfigurationId")
    @NotNull
    @Size(min = 2, max = 64)
    private String planConfigurationId = null;

    @JsonProperty("employeeId")
    @NotNull
    @Size(min = 2, max = 64)
    private String employeeId = null;

    @JsonProperty("role")
    @NotNull
    @Size(min = 2, max = 64)
    private String role = null;

    @JsonProperty("hierarchyLevel")
    @Size(min = 2, max = 64)
    private String hierarchyLevel = null;

    @JsonProperty("jurisdiction")
    @Valid
    @NotEmpty
    private Set<String> jurisdiction = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("active")
    private Boolean active = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;
}
