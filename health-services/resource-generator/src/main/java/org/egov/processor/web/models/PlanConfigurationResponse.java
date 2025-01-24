package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

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


    public PlanConfigurationResponse addPlanConfigurationResponseItem(PlanConfiguration planConfigurationResponseItem) {
        if (this.planConfiguration == null) {
            this.planConfiguration = new ArrayList<>();
        }
        this.planConfiguration.add(planConfigurationResponseItem);
        return this;
    }

}
