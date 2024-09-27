package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * PlanFacilityResponse
 * This class represents the response structure for plan facility-related operations.
 * It encapsulates the response information and a list of plan facilities.
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanFacilityResponse {

    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo = null;

    @JsonProperty("PlanFacility")
    @Valid
    private List<PlanFacility> planFacility = null;

    public PlanFacilityResponse responseInfo(ResponseInfo responseInfo) {
        this.responseInfo = responseInfo;
        return this;
    }

    public PlanFacilityResponse addPlanFacilityItem(PlanFacility planFacilityItem) {
        if (this.planFacility == null) {
            this.planFacility = new ArrayList<>();
        }
        this.planFacility.add(planFacilityItem);
        return this;
    }
}
