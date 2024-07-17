package org.egov.common.models.referralmanagement.hfreferral;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class HFReferral extends EgovOfflineModel {

    @JsonProperty("projectId")
    @Size(min = 2, max = 64)
    private String projectId;

    @JsonProperty("projectFacilityId")
    @Size(min = 2, max = 64)
    private String projectFacilityId;

    @JsonProperty("symptom")
    @NotNull
    @Size(min = 2, max = 256)
    private String symptom;

    @JsonProperty("symptomSurveyId")
    @Size(min = 2, max = 100)
    private String symptomSurveyId;

    @JsonProperty("beneficiaryId")
    @Size(max=100)
    private String beneficiaryId;

    @JsonProperty("referralCode")
    @Size(max=100)
    private String referralCode;

    @JsonProperty("nationalLevelId")
    @Size(max=100)
    private String nationalLevelId;

    //TODO remove this
    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

}
