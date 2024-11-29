package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

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
    private Set<String> locality = null;

    @JsonProperty("campaignId")
    private String campaignId = null;

    @JsonProperty("planConfigurationId")
    private String planConfigurationId = null;

    @JsonProperty("status")
    private String status = null;

    @JsonProperty("assignee")
    private String assignee = null;

    @JsonProperty("jurisdiction")
    @Valid
    private Set<String> jurisdiction = null;

    @JsonProperty("offset")
    private Integer offset = null;

    @JsonProperty("limit")
    private Integer limit = null;

}
