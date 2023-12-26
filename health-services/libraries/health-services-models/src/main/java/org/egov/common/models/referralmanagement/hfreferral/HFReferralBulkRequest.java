package org.egov.common.models.referralmanagement.hfreferral;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HFReferralBulkRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("HFReferrals")
    @NotNull
    @Valid
    @Size(min = 1)
    private List<HFReferral> hfReferrals;

    /**
     * Add a HfReferral item to the list of HfReferrals in the bulk request.
     *
     * @param hfReferralItem The HfReferral item to add to the request.
     * @return The updated HFReferralBulkRequest.
     */
    public HFReferralBulkRequest addHFReferralItem(HFReferral hfReferralItem) {
        if(Objects.isNull(hfReferrals))
            hfReferrals = new ArrayList<>();
        if(Objects.nonNull(hfReferralItem))
            hfReferrals.add(hfReferralItem);
        return this;
    }
}
