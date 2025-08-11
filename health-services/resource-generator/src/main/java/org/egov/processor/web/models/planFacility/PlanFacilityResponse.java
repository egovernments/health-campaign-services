package org.egov.processor.web.models.planFacility;

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
 * PlanFacilityResponse
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanFacilityResponse {

    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo;

    @JsonProperty("PlanFacility")
    @Valid
    private List<PlanFacility> planFacility;

    @JsonProperty("TotalCount")
    @Valid
    private Integer totalCount;
}
