package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
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

    @JsonProperty("PlanConfigurationResponse")
    @Valid
    private List<PlanConfiguration> planConfigurationResponse = null;


    public PlanConfigurationResponse addPlanConfigurationResponseItem(PlanConfiguration planConfigurationResponseItem) {
        if (this.planConfigurationResponse == null) {
            this.planConfigurationResponse = new ArrayList<>();
        }
        this.planConfigurationResponse.add(planConfigurationResponseItem);
        return this;
    }

}
