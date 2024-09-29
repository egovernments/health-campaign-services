package digit.web.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import java.util.List;

/**
 * CampaignResponse
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CampaignResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("CampaignDetails")
    @Valid
    private List<CampaignDetail> campaignDetails = null;

    @JsonProperty("totalCount")
    @Valid
    private Integer totalCount = null;
}
