package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Set;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanFacilitySearchCriteria {

    @JsonProperty("ids")
    private Set<String> ids = null;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId = null;

    @JsonProperty("planConfigurationId")
    private String planConfigurationId = null;

    @JsonProperty("residingBoundaries")
    private List<String> residingBoundaries = null;

    @JsonProperty("offset")
    private Integer offset = null;

    @JsonProperty("limit")
    private Integer limit = null;
}
