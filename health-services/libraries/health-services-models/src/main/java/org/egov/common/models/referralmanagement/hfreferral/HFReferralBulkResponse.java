package org.egov.common.models.referralmanagement.hfreferral;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HFReferralBulkResponse {
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("HFReferrals")
    @NotNull
    @Valid
    private List<HFReferral> hfReferrals;

    /**
     * Add a HfReferral item to the list of HfReferrals in the bulk response.
     *
     * @param hfReferralItem The HfReferral item to add to the response.
     * @return The updated HFReferralBulkRequest.
     */
    public HFReferralBulkResponse addReferralItem(HFReferral hfReferralItem) {
        if(Objects.isNull(hfReferrals))
            hfReferrals = new ArrayList<>();
        if(Objects.nonNull(hfReferralItem))
            hfReferrals.add(hfReferralItem);
        return this;
    }
}
