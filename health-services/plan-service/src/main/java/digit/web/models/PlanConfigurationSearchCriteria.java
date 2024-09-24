package digit.web.models;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * PlanConfigurationSearchCriteria
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanConfigurationSearchCriteria {

    @JsonProperty("tenantId")
    @Size(min = 2, max = 256)
    @NotNull
    private String tenantId = null;

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("name")
    private String name = null;

    @JsonProperty("campaignId")
    private String campaignId = null;

    @JsonProperty("status")
    private String status = null;

    @JsonProperty("userUuid")
    private String userUuid = null;

    @JsonProperty("offset")
    @Min(0)
    private Integer offset;

    @JsonProperty("limit")
    @Min(1)
    @Max(50)
    private Integer limit;

}
