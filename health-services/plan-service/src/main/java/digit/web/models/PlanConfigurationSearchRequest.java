package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * PlanConfigurationSearchRequest
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanConfigurationSearchRequest {
    @JsonProperty("RequestInfo")
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("PlanConfigurationSearchCriteria")
    @Valid
    @NotNull(message = "Plan configuration search criteria cannot be null")
    private PlanConfigurationSearchCriteria planConfigurationSearchCriteria = null;


}
