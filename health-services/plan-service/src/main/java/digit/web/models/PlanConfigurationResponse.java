package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * PlanConfigurationResponse
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanConfigurationResponse {
    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("PlanConfiguration")
    @Valid
    private List<PlanConfiguration> planConfiguration = null;

    @JsonProperty("TotalCount")
    @Valid
    private Integer totalCount = null;

    public PlanConfigurationResponse addPlanConfigurationResponseItem(PlanConfiguration planConfigurationResponseItem) {
        if (this.planConfiguration == null) {
            this.planConfiguration = new ArrayList<>();
        }
        this.planConfiguration.add(planConfigurationResponseItem);
        return this;
    }

}
