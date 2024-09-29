package digit.web.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.web.models.Pagination;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * CampaignSearchCriteria
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Validated
public class CampaignSearchCriteria {

    @JsonProperty("ids")
    @Size(min = 1)
    private List<String> ids;

    @JsonProperty("tenantId")
    @Size(min = 2, max = 256)
    private String tenantId;

    @JsonIgnore
    private List<String> status;

    @JsonIgnore
    private String createdBy;

    @JsonIgnore
    private Boolean campaignsIncludeDates;

    @JsonIgnore
    private Integer startDate;

    @JsonIgnore
    private Integer endDate;

    @JsonProperty("pagination")
    @Valid
    private Pagination pagination;
}