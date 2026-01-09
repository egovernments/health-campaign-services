package org.egov.common.models.referralmanagement.hfreferral;

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

    @JsonProperty("localityCode")
    private List<String> localityCode;
}
