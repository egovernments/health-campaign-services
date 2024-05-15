package org.egov.egovsurveyservices.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.egov.common.contract.request.RequestInfo;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class SurveyResultsSearchRequest {
    @JsonProperty("SurveyResultsSearchCriteria")
    SurveyResultsSearchCriteria surveyResultsSearchCriteria;

    @JsonProperty("RequestInfo")
    RequestInfo requestInfo;
}
