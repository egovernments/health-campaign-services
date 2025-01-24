package org.egov.common.models.referralmanagement.hfreferral;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

/**
 * Represents a bulk response for HF (Health Facility) referrals, containing response metadata and a list of referrals.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HFReferralBulkResponse {

    /**
     * Metadata about the API response, including details such as request status and information.
     */
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo;

    /**
     * List of health facility referrals returned in the response.
     */
    @JsonProperty("HFReferrals")
    @NotNull
    @Valid
    private List<HFReferral> hfReferrals;

    /**
     * Total number of HF referrals in the response, defaults to 0 if not specified.
     */
    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

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
