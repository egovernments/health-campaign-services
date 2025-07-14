package digit.web.models.census;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * CensusSearchCriteria
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CensusSearchCriteria {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("ids")
    private Set<String> ids = null;

    @JsonProperty("tenantId")
    @Size(min = 1, max = 100)
    private String tenantId = null;

    @JsonProperty("areaCodes")
    private List<String> areaCodes = null;

    @JsonProperty("status")
    private String status = null;

    @JsonProperty("assignee")
    private String assignee = null;

    @JsonProperty("source")
    private String source = null;

    @JsonProperty("facilityAssigned")
    private Boolean facilityAssigned = null;

    @JsonProperty("jurisdiction")
    private List<String> jurisdiction = null;

    @JsonProperty("effectiveTo")
    private Long effectiveTo = null;

    @JsonProperty("limit")
    private Integer limit = null;

    @JsonProperty("offset")
    private Integer offset = null;

    public CensusSearchCriteria addAreaCodesItem(String areaCodesItem) {
        if (this.areaCodes == null) {
            this.areaCodes = new ArrayList<>();
        }
        this.areaCodes.add(areaCodesItem);
        return this;
    }

}