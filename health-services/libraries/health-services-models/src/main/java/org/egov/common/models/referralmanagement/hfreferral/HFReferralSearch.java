package org.egov.common.models.referralmanagement.hfreferral;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HFReferralSearch {
    @JsonProperty("id")
    private List<String> id;

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId;

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
