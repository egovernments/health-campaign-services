package digit.web.models.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;

/**
 * Plan Facility DTO
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanFacilityDTO {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId = null;

    @JsonProperty("planConfigurationId")
    @NotNull
    @Size(max = 64)
    private String planConfigurationId = null;

    @JsonProperty("facilityId")
    @NotNull
    @Size(max = 64)
    private String facilityId = null;

    @JsonProperty("residingBoundary")
    @NotNull
    @Size(min = 1, max = 64)
    private String residingBoundary = null;

    // Changed List<String> to String to store as JSON
    @JsonProperty("serviceBoundaries")
    @NotNull
    @Size(min = 1)
    private String serviceBoundaries = null; // Store as JSON string

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("active")
    @NotNull
    private Boolean active = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

}