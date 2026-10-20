package digit.web.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;
import org.egov.common.contract.request.RequestInfo;

/**
 * CampaignSearchReq
 */
@Data
@Builder
public class CampaignSearchReq {

    @JsonProperty("RequestInfo")
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("CampaignDetails")
    private CampaignSearchCriteria campaignSearchCriteria;
}