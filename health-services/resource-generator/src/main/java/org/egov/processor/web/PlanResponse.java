package org.egov.processor.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.processor.web.models.Plan;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * PlanSearchResponse
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("Plan")
    @Valid
    private List<Plan> plan = null;

    @JsonProperty("TotalCount")
    @Valid
    private Integer totalCount = null;

    @JsonProperty("StatusCount")
    @Valid
    private Map<String, Integer> statusCount = null;

}
