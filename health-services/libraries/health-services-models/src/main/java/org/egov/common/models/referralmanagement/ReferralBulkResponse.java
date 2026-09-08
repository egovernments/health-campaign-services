package org.egov.common.models.referralmanagement;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralBulkResponse {
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    @JsonProperty("Referrals")
    @NotNull
    @Valid
    private List<Referral> referrals;

    /**
     * Add a Referral item to the list of Referrals in the bulk response.
     *
     * @param referralItem The Referral item to add to the response.
     * @return The updated ReferralBulkResponse.
     */
    public ReferralBulkResponse addReferralItem(Referral referralItem) {
        if(Objects.isNull(referrals))
            referrals = new ArrayList<>();
        if(Objects.nonNull(referralItem))
            referrals.add(referralItem);
        return this;
    }
}
