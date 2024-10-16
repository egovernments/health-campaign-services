package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;


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

