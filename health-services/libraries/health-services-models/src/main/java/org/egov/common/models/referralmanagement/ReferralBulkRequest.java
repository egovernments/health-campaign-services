package org.egov.common.models.referralmanagement;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralBulkRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("Referrals")
    @NotNull
    @Valid
    @Size(min = 1)
    private List<Referral> referrals = new ArrayList<>();

    public ReferralBulkRequest addReferralItem(Referral referralItem) {
        if(Objects.nonNull(Objects.nonNull(referralItem)))
            this.referrals.add(referralItem);
        return this;
    }
}
