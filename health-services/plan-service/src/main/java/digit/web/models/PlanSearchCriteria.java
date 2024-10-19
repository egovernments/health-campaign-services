package digit.web.models;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

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
    private String locality = null;

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
    private List<String> jurisdiction = null;

    @JsonProperty("offset")
    private Integer offset = null;

    @JsonProperty("limit")
    private Integer limit = null;

}
