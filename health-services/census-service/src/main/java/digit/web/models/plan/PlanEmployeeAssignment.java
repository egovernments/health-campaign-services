package digit.web.models.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;

import java.util.List;

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

    @JsonProperty("jurisdiction")
    @Valid
    @NotEmpty
    private List<String> jurisdiction = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("active")
    private Boolean active = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;
}
