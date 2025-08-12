package org.egov.common.models.referralmanagement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.egov.common.models.project.AdditionalFields;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Referral extends EgovOfflineModel {

    @JsonProperty("projectBeneficiaryId")
    @Size(min = 2, max = 64)
    private String projectBeneficiaryId;

    @JsonProperty("projectBeneficiaryClientReferenceId")
    @Size(min = 2, max = 64)
    private String projectBeneficiaryClientReferenceId;

    @JsonProperty("referrerId")
    @Size(min = 2, max = 64)
    private String referrerId;

    @JsonProperty("recipientType")
    private String recipientType;

    @JsonProperty("recipientId")
    @Size(min = 2, max = 64)
    private String recipientId;

    @JsonProperty("reasons")
    @NotNull
    @Size(min=1)
    private List<String> reasons;

    @JsonProperty("referralCode")
    @Size(max=100)
    private String referralCode;

    @JsonProperty("sideEffect")
    private SideEffect sideEffect;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

}
