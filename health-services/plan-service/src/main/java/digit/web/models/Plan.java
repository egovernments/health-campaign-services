package digit.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.models.Workflow;
import org.springframework.validation.annotation.Validated;
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
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId = null;

    @JsonProperty("locality")
    @Size(min = 1, max = 64)
    private String locality = null;

    @JsonProperty("campaignId")
    @Size(max = 64)
    private String campaignId = null;

    @JsonProperty("planConfigurationId")
    @Size(max = 64)
    private String planConfigurationId = null;

    @JsonProperty("status")
    @Size(max = 64)
    private String status = null;

    @JsonProperty("assignee")
    private List<String> assignee = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("activities")
    @Valid
    private List<Activity> activities;

    @JsonProperty("resources")
    @Valid
    private List<Resource> resources;

    @JsonProperty("targets")
    @Valid
    private List<Target> targets;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    @JsonProperty("jurisdictionMapping")
    private Map<String, String> jurisdictionMapping;

    @JsonProperty("additionalFields")
    @Valid
    private List<AdditionalField> additionalFields = null;

    @JsonIgnore
    private String boundaryAncestralPath = null;

    @JsonIgnore
    private boolean isRequestFromResourceEstimationConsumer;

    @JsonIgnore
    private List<String> assigneeJurisdiction;

    @JsonProperty("workflow")
    @Valid
    private Workflow workflow;

}
