package digit.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PlanSearchCriteria
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanSearchCriteria {

    @JsonProperty("ids")
    private Set<String> ids = null;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId = null;

    @JsonProperty("locality")
    private List<String> locality = null;

    @JsonProperty("campaignId")
    private String campaignId = null;

    @JsonProperty("planConfigurationId")
    private String planConfigurationId = null;

    @JsonProperty("facilityIds")
    private Set<String> facilityIds = null;

    @JsonProperty("onRoadCondition")
    private String onRoadCondition = null;

    @JsonProperty("terrain")
    private String terrain = null;

    @JsonProperty("securityQ1")
    private String securityQ1 = null;

    @JsonProperty("securityQ2")
    private String securityQ2 = null;

    @JsonProperty("status")
    private String status = null;

    @JsonProperty("assignee")
    private String assignee = null;

    @JsonProperty("jurisdiction")
    @Valid
    private List<String> jurisdiction = null;

    @JsonProperty("offset")
    private Integer offset = null;

    @JsonProperty("limit")
    private Integer limit = null;

    @JsonIgnore
    private Map<String, Set<String>> filtersMap = null;

}
