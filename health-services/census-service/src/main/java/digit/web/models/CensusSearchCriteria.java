package digit.web.models;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

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

    @JsonProperty("tenantId")
    @Size(min = 1, max = 100)
    private String tenantId = null;

    @JsonProperty("areaCodes")
    private List<String> areaCodes = null;

    @JsonProperty("status")
    private String status = null;

    @JsonProperty("assignee")
    private String assignee = null;

    @JsonProperty("jurisdiction")
    private List<String> jurisdiction = null;

    public CensusSearchCriteria addAreaCodesItem(String areaCodesItem) {
        if (this.areaCodes == null) {
            this.areaCodes = new ArrayList<>();
        }
        this.areaCodes.add(areaCodesItem);
        return this;
    }

}
