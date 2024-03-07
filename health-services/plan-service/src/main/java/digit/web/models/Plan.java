package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * Plan
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Plan {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("locality")
    @Size(min = 2)
    private String locality = null;

    @JsonProperty("executionPlanId")
    private String executionPlanId = null;

    @JsonProperty("planConfigurationId")
    private String planConfigurationId = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("activities")
    @Valid
    private List<Activity> activities = null;

    @JsonProperty("resources")
    @Valid
    private List<Resource> resources = null;

    @JsonProperty("targets")
    @Valid
    private List<Target> targets = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

}
