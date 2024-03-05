package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2024-03-04T09:55:29.782094600+05:30[Asia/Calcutta]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Plan {
    @JsonProperty("id")
    @Valid
    private UUID id = null;

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


    public Plan addActivitiesItem(Activity activitiesItem) {
        if (this.activities == null) {
            this.activities = new ArrayList<>();
        }
        this.activities.add(activitiesItem);
        return this;
    }

    public Plan addResourcesItem(Resource resourcesItem) {
        if (this.resources == null) {
            this.resources = new ArrayList<>();
        }
        this.resources.add(resourcesItem);
        return this;
    }

    public Plan addTargetsItem(Target targetsItem) {
        if (this.targets == null) {
            this.targets = new ArrayList<>();
        }
        this.targets.add(targetsItem);
        return this;
    }

}
