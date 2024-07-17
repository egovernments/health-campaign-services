package org.egov.common.models.referralmanagement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralBulkRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("Referrals")
    @NotNull
    @Valid
    @Size(min = 1)
    private List<Referral> referrals;

    /**
     * Add a Referral item to the list of Referrals in the bulk request.
     *
     * @param referralItem The Referral item to add to the request.
     * @return The updated ReferralBulkRequest.
     */
    public ReferralBulkRequest addReferralItem(Referral referralItem) {
        if(Objects.isNull(referrals))
            referrals = new ArrayList<>();
        if(Objects.nonNull(referralItem))
            referrals.add(referralItem);
        return this;
    }
}
