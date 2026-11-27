package digit.web.models.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import java.util.List;


/**
 * PlanEmployeeAssignmentResponse
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanEmployeeAssignmentResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("PlanEmployeeAssignment")
    @Valid
    private List<PlanEmployeeAssignment> planEmployeeAssignment = null;

    @JsonProperty("TotalCount")
    @Valid
    private Integer totalCount = null;
}

