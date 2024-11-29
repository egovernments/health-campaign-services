package digit.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
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
    @NotNull
    private String planConfigurationId = null;

    @JsonProperty("planConfigurationName")
    private String planConfigurationName = null;

    @JsonProperty("facilityName")
    private String facilityName = null;

    @JsonProperty("facilityStatus")
    private String facilityStatus = null;

    @JsonProperty("facilityType")
    private String facilityType = null;

    @JsonProperty("residingBoundaries")
    private Set<String> residingBoundaries = null;

    @JsonProperty("facilityId")
    private String facilityId = null;

    @JsonProperty("offset")
    private Integer offset = null;

    @JsonProperty("limit")
    private Integer limit = null;

    @JsonIgnore
    private Map<String, String> filtersMap = null;

}
