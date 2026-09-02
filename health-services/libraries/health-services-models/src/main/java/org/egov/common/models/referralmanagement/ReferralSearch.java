package org.egov.common.models.referralmanagement;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineSearchModel;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ReferralSearch extends EgovOfflineSearchModel {

    @JsonProperty("projectBeneficiaryId")
    private List<String> projectBeneficiaryId;

    @JsonProperty("projectBeneficiaryClientReferenceId")
    private List<String> projectBeneficiaryClientReferenceId;

    @JsonProperty("sideEffectId")
    private List<String> sideEffectId;

    @JsonProperty("sideEffectClientReferenceId")
    private List<String> sideEffectClientReferenceId;

    @JsonProperty("referrerId")
    private List<String> referrerId;

    @JsonProperty("recipientId")
    private List<String> recipientId;
}
