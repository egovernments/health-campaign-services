package org.egov.common.models.referralmanagement.hfreferral;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.core.EgovOfflineSearchModel;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HFReferralSearch extends EgovOfflineSearchModel {

    @JsonProperty("facilityId")
    private List<String> facilityId;

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("symptom")
    private List<String> symptom;

    @JsonProperty("symptomSurveyId")
    private List<String> symptomSurveyId;

    @JsonProperty("beneficiaryId")
    private List<String> beneficiaryId;

    @JsonProperty("referralCode")
    private List<String> referralCode;

    @JsonProperty("nationalLevelId")
    private List<String> nationalLevelId;
}
